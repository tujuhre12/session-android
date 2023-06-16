package org.thoughtcrime.securesms.notifications

import android.content.Context
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import kotlinx.coroutines.Job
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.combine.and
import nl.komponents.kovenant.functional.map
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationAPI
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationMetadata
import org.session.libsession.messaging.sending_receiving.notifications.Response
import org.session.libsession.messaging.sending_receiving.notifications.SubscriptionRequest
import org.session.libsession.messaging.sending_receiving.notifications.SubscriptionResponse
import org.session.libsession.messaging.sending_receiving.notifications.UnsubscribeResponse
import org.session.libsession.messaging.sending_receiving.notifications.UnsubscriptionRequest
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.Version
import org.session.libsession.utilities.TextSecurePreferences.Companion.getLocalNumber
import org.session.libsession.utilities.bencode.Bencode
import org.session.libsession.utilities.bencode.BencodeList
import org.session.libsession.utilities.bencode.BencodeString
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.emptyPromise
import org.session.libsignal.utilities.retryIfNeeded
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.KeyPairUtilities

private const val TAG = "FirebasePushManager"

class FirebasePushManager (private val context: Context): PushManager {

    companion object {
        private const val maxRetryCount = 4
    }

    private val tokenManager = FcmTokenManager(context, ExpiryManager(context))
    private var firebaseInstanceIdJob: Job? = null
    private val sodium = LazySodiumAndroid(SodiumAndroid())

    private fun getOrCreateNotificationKey(): Key {
        if (IdentityKeyUtil.retrieve(context, IdentityKeyUtil.NOTIFICATION_KEY) == null) {
            // generate the key and store it
            val key = sodium.keygen(AEAD.Method.XCHACHA20_POLY1305_IETF)
            IdentityKeyUtil.save(context, IdentityKeyUtil.NOTIFICATION_KEY, key.asHexString)
        }
        return Key.fromHexString(IdentityKeyUtil.retrieve(context, IdentityKeyUtil.NOTIFICATION_KEY))
    }

    fun decrypt(encPayload: ByteArray): ByteArray? {
        val encKey = getOrCreateNotificationKey()
        val nonce = encPayload.take(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES).toByteArray()
        val payload = encPayload.drop(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES).toByteArray()
        val padded = SodiumUtilities.decrypt(payload, encKey.asBytes, nonce)
            ?: error("Failed to decrypt push notification")
        val decrypted = padded.dropLastWhile { it.toInt() == 0 }.toByteArray()
        val bencoded = Bencode.Decoder(decrypted)
        val expectedList = (bencoded.decode() as? BencodeList)?.values
            ?: error("Failed to decode bencoded list from payload")

        val metadataJson = (expectedList[0] as? BencodeString)?.value ?: error("no metadata")
        val metadata: PushNotificationMetadata = Json.decodeFromString(String(metadataJson))

        val content: ByteArray? = if (expectedList.size >= 2) (expectedList[1] as? BencodeString)?.value else null
        // null content is valid only if we got a "data_too_long" flag
        if (content == null)
            check(metadata.data_too_long) { "missing message data, but no too-long flag" }
        else
            check(metadata.data_len == content.size) { "wrong message data size" }

        Log.d(TAG, "Received push for ${metadata.account}/${metadata.namespace}, msg ${metadata.msg_hash}, ${metadata.data_len}B")

        return content
    }

    @Synchronized
    override fun refresh(force: Boolean) {
        firebaseInstanceIdJob?.apply {
            if (force) cancel() else if (isActive) return
        }

        firebaseInstanceIdJob = getFcmInstanceId { task ->
            when {
                task.isSuccessful -> task.result?.token?.let { refresh(it, force).get() }
                else -> Log.w(TAG, "getFcmInstanceId failed." + task.exception)
            }
        }
    }

    private fun refresh(token: String, force: Boolean): Promise<*, Exception> {
        val userPublicKey = getLocalNumber(context) ?: return emptyPromise()
        val userEdKey = KeyPairUtilities.getUserED25519KeyPair(context) ?: return emptyPromise()

        return when {
            tokenManager.isUsingFCM -> register(force, token, userPublicKey, userEdKey)
            tokenManager.requiresUnregister -> unregister(token, userPublicKey, userEdKey)
            else -> emptyPromise()
        }
    }

    /**
     * Register for push notifications if:
     *   force is true
     *   there is no FCM Token
     *   FCM Token has expired
     */
    private fun register(
        force: Boolean,
        token: String,
        publicKey: String,
        userEd25519Key: KeyPair,
        namespaces: List<Int> = listOf(Namespace.DEFAULT)
    ): Promise<*, Exception> = if (force || tokenManager.isInvalid()) {
        register(token, publicKey, userEd25519Key, namespaces)
    } else emptyPromise()

    /**
     * Register for push notifications.
     */
    private fun register(
        token: String,
        publicKey: String,
        userEd25519Key: KeyPair,
        namespaces: List<Int> = listOf(Namespace.DEFAULT)
    ): Promise<*, Exception> = PushNotificationAPI.register(token) and getSubscription(
        token, publicKey, userEd25519Key, namespaces
    ) fail {
        Log.e(TAG, "Couldn't register for FCM due to error: $it.", it)
    } success {
        tokenManager.fcmToken = token
    }

    private fun unregister(
        token: String,
        userPublicKey: String,
        userEdKey: KeyPair
    ): Promise<*, Exception> = PushNotificationAPI.unregister() and getUnsubscription(
        token, userPublicKey, userEdKey
    ) fail {
        Log.e(TAG, "Couldn't unregister for FCM due to error: ${it}.", it)
    } success {
        tokenManager.fcmToken = null
    }

    private fun getSubscription(
        token: String,
        publicKey: String,
        userEd25519Key: KeyPair,
        namespaces: List<Int>
    ): Promise<SubscriptionResponse, Exception> {
        val pnKey = getOrCreateNotificationKey()

        val timestamp = SnodeAPI.nowWithOffset / 1000 // get timestamp in ms -> s
        // if we want to support passing namespace list, here is the place to do it
        val sigData = "MONITOR${publicKey}${timestamp}1${namespaces.joinToString(separator = ",")}".encodeToByteArray()
        val signature = ByteArray(Sign.BYTES)
        sodium.cryptoSignDetached(signature, sigData, sigData.size.toLong(), userEd25519Key.secretKey.asBytes)
        val requestParameters = SubscriptionRequest(
            pubkey = publicKey,
            session_ed25519 = userEd25519Key.publicKey.asHexString,
            namespaces = listOf(Namespace.DEFAULT),
            data = true, // only permit data subscription for now (?)
            service = "firebase",
            sig_ts = timestamp,
            signature = Base64.encodeBytes(signature),
            service_info = mapOf("token" to token),
            enc_key = pnKey.asHexString,
        ).let(Json::encodeToString)

        return retryResponseBody("subscribe", requestParameters)
    }

    private fun getUnsubscription(
        token: String,
        userPublicKey: String,
        userEdKey: KeyPair
    ): Promise<UnsubscribeResponse, Exception> {
        val timestamp = SnodeAPI.nowWithOffset / 1000 // get timestamp in ms -> s
        // if we want to support passing namespace list, here is the place to do it
        val sigData = "UNSUBSCRIBE${userPublicKey}${timestamp}".encodeToByteArray()
        val signature = ByteArray(Sign.BYTES)
        sodium.cryptoSignDetached(signature, sigData, sigData.size.toLong(), userEdKey.secretKey.asBytes)

        val requestParameters = UnsubscriptionRequest(
            pubkey = userPublicKey,
            session_ed25519 = userEdKey.publicKey.asHexString,
            service = "firebase",
            sig_ts = timestamp,
            signature = Base64.encodeBytes(signature),
            service_info = mapOf("token" to token),
        ).let(Json::encodeToString)

        return retryResponseBody("unsubscribe", requestParameters)
    }

    private inline fun <reified T: Response> retryResponseBody(path: String, requestParameters: String): Promise<T, Exception> =
        retryIfNeeded(maxRetryCount) { getResponseBody(path, requestParameters) }

    private inline fun <reified T: Response> getResponseBody(path: String, requestParameters: String): Promise<T, Exception> {
        val url = "${PushNotificationAPI.server}/$path"
        val body = RequestBody.create(MediaType.get("application/json"), requestParameters)
        val request = Request.Builder().url(url).post(body).build()

        return OnionRequestAPI.sendOnionRequest(
            request,
            PushNotificationAPI.server,
            PushNotificationAPI.serverPublicKey,
            Version.V4
        ).map { response ->
            response.body!!.inputStream()
                .let { Json.decodeFromStream<T>(it) }
                .also { if (it.isFailure()) throw Exception("error: ${it.message}.") }
        }
    }
}
