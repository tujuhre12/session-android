package org.thoughtcrime.securesms.notifications

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.iid.InstanceIdResult
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
import nl.komponents.kovenant.functional.map
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationAPI
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationMetadata
import org.session.libsession.messaging.sending_receiving.notifications.SubscriptionRequest
import org.session.libsession.messaging.sending_receiving.notifications.SubscriptionResponse
import org.session.libsession.messaging.sending_receiving.notifications.UnsubscribeResponse
import org.session.libsession.messaging.sending_receiving.notifications.UnsubscriptionRequest
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.Version
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.getLocalNumber
import org.session.libsession.utilities.bencode.Bencode
import org.session.libsession.utilities.bencode.BencodeList
import org.session.libsession.utilities.bencode.BencodeString
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.retryIfNeeded
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.KeyPairUtilities

private const val TAG = "FirebasePushManager"

class FirebasePushManager(private val context: Context, private val prefs: TextSecurePreferences): PushManager {

    companion object {
        private const val maxRetryCount = 4
        private const val tokenExpirationInterval = 12 * 60 * 60 * 1000
    }

    private var firebaseInstanceIdJob: Job? = null
    private val sodium = LazySodiumAndroid(SodiumAndroid())

    private fun getOrCreateNotificationKey(): Key {
        if (IdentityKeyUtil.retrieve(context, IdentityKeyUtil.NOTIFICATION_KEY) == null) {
            // generate the key and store it
            val key = sodium.keygen(AEAD.Method.XCHACHA20_POLY1305_IETF)
            IdentityKeyUtil.save(context, IdentityKeyUtil.NOTIFICATION_KEY, key.asHexString)
        }
        return Key.fromHexString(
            IdentityKeyUtil.retrieve(
                context,
                IdentityKeyUtil.NOTIFICATION_KEY
            )
        )
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

        val metadataJson = (expectedList[0] as? BencodeString)?.value
                ?: error("no metadata")
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

    override fun refresh(force: Boolean) {
        firebaseInstanceIdJob?.apply {
            if (force) cancel() else if (isActive) return
        }

        firebaseInstanceIdJob = getFcmInstanceId { refresh(it, force) }
    }

    private fun refresh(task: Task<InstanceIdResult>, force: Boolean) {
        Log.d(TAG, "refresh")

        // context in here is Dispatchers.IO
        if (!task.isSuccessful) {
            Log.w(TAG, "FirebaseInstanceId.getInstance().getInstanceId() failed." + task.exception
            )
            return
        }

        val token: String = task.result?.token ?: return
        val userPublicKey = getLocalNumber(context) ?: return
        val userEdKey = KeyPairUtilities.getUserED25519KeyPair(context) ?: return

        when {
            prefs.isUsingFCM() -> register(token, userPublicKey, userEdKey, force)
            prefs.getFCMToken() != null -> unregister(token, userPublicKey, userEdKey)
        }
    }

    private fun unregister(token: String, userPublicKey: String, userEdKey: KeyPair) {
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
        )

        val url = "${PushNotificationAPI.server}/unsubscribe"
        val body = RequestBody.create(MediaType.get("application/json"), Json.encodeToString(requestParameters))
        val request = Request.Builder().url(url).post(body)
        retryIfNeeded(maxRetryCount) {
            getResponseBody<UnsubscribeResponse>(request.build()).map { response ->
                if (response.success == true) {
                    Log.d(TAG, "Unsubscribe FCM success")
                    TextSecurePreferences.setFCMToken(context, null)
                    PushNotificationAPI.unregister(token)
                } else {
                    Log.e(TAG, "Couldn't unregister for FCM due to error: ${response.message}")
                }
            }.fail { exception ->
                Log.e(TAG, "Couldn't unregister for FCM due to error: ${exception}.", exception)
            }
        }
    }

    private fun register(token: String, publicKey: String, userEd25519Key: KeyPair, force: Boolean, namespaces: List<Int> = listOf(Namespace.DEFAULT)) {
        Log.d(TAG, "register token: $token")

        val oldToken = TextSecurePreferences.getFCMToken(context)
        val lastUploadDate = TextSecurePreferences.getLastFCMUploadTime(context)
        if (!force && token == oldToken && System.currentTimeMillis() - lastUploadDate < tokenExpirationInterval) return

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
        )

        val url = "${PushNotificationAPI.server}/subscribe"
        val body = RequestBody.create(MediaType.get("application/json"), Json.encodeToString(requestParameters))
        val request = Request.Builder().url(url).post(body)
        retryIfNeeded(maxRetryCount) {
            getResponseBody<SubscriptionResponse>(request.build()).map { response ->
                if (response.isSuccess()) {
                    Log.d(TAG, "Success $token")
                    TextSecurePreferences.setFCMToken(context, token)
                    TextSecurePreferences.setLastFCMUploadTime(context, System.currentTimeMillis())
                    PushNotificationAPI.register(token)
                } else {
                    val (_, message) = response.errorInfo()
                    Log.e(TAG, "Couldn't register for FCM due to error: $message.")
                }
            }.fail { exception ->
                Log.e(TAG, "Couldn't register for FCM due to error: ${exception}.", exception)
            }
        }
    }

    private inline fun <reified T> getResponseBody(request: Request): Promise<T, Exception> =
        OnionRequestAPI.sendOnionRequest(
            request,
            PushNotificationAPI.server,
            PushNotificationAPI.serverPublicKey,
            Version.V4
        ).map { response -> Json.decodeFromStream(response.body!!.inputStream()) }
}
