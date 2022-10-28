package org.thoughtcrime.securesms.notifications

import android.content.Context
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import kotlinx.coroutines.Job
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationAPI
import org.session.libsession.messaging.sending_receiving.notifications.SubscriptionRequest
import org.session.libsession.messaging.sending_receiving.notifications.SubscriptionResponse
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.Version
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.getLocalNumber
import org.session.libsession.utilities.bencode.Bencode
import org.session.libsession.utilities.bencode.BencodeDict
import org.session.libsession.utilities.bencode.BencodeList
import org.session.libsession.utilities.bencode.BencodeString
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.retryIfNeeded
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.KeyPairUtilities

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

    fun decrypt(encPayload: ByteArray) {
        val encKey = getOrCreateNotificationKey()
        val nonce = encPayload.take(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES).toByteArray()
        val payload = encPayload.drop(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES).toByteArray()
        val decrypted = SodiumUtilities.decrypt(payload, encKey.asBytes, nonce)
            ?: return Log.e("Loki", "Failed to decrypt push notification")
        val bencoded = Bencode.Decoder(decrypted)
        val expectedList = (bencoded.decode() as? BencodeList)
            ?: return Log.e("Loki", "Failed to decode bencoded list from payload")

        val (metadata, content) = expectedList.values
        val metadataDict = (metadata as? BencodeDict)?.values
            ?: return Log.e("Loki", "Failed to decode metadata dict")

        val push = """
            Push metadata received was:
                @: ${metadataDict["@"]}
                #: ${metadataDict["#"]}
                n: ${metadataDict["n"]}
                l: ${metadataDict["l"]}
                B: ${metadataDict["B"]}
        """.trimIndent()

        Log.d("Loki", "push")

        val contentBytes = (content as? BencodeString)?.value
            ?: return Log.e("Loki", "Failed to decode content string")

        // TODO: something with contentBytes
    }

    override fun register(force: Boolean) {
        val currentInstanceIdJob = firebaseInstanceIdJob
        if (currentInstanceIdJob != null && currentInstanceIdJob.isActive && !force) return

        if (force && currentInstanceIdJob != null) {
            currentInstanceIdJob.cancel(null)
        }

        firebaseInstanceIdJob = getFcmInstanceId { task ->
            // context in here is Dispatchers.IO
            if (!task.isSuccessful) {
                Log.w(
                    "Loki",
                    "FirebaseInstanceId.getInstance().getInstanceId() failed." + task.exception
                )
                return@getFcmInstanceId
            }
            val token: String = task.result?.token ?: return@getFcmInstanceId
            val userPublicKey = getLocalNumber(context) ?: return@getFcmInstanceId
            val userEdKey = KeyPairUtilities.getUserED25519KeyPair(context) ?: return@getFcmInstanceId
            if (prefs.isUsingFCM()) {
                register(token, userPublicKey, userEdKey, force)
            } else {
                unregister(token)
            }
        }
    }

    override fun unregister(token: String) {
        TODO("Not yet implemented")
    }

    fun register(token: String, publicKey: String, userEd25519Key: KeyPair, force: Boolean, namespaces: List<Int> = listOf(Namespace.DEFAULT)) {
        val oldToken = TextSecurePreferences.getFCMToken(context)
        val lastUploadDate = TextSecurePreferences.getLastFCMUploadTime(context)
        if (!force && token == oldToken && System.currentTimeMillis() - lastUploadDate < tokenExpirationInterval) { return }

        val pnKey = getOrCreateNotificationKey()

        val timestamp = SnodeAPI.nowWithOffset / 1000 // get timestamp in ms -> s
        // if we want to support passing namespace list, here is the place to do it
        val sigData = "MONITOR${publicKey}${timestamp}1${namespaces.joinToString(separator = ",")}".encodeToByteArray()
        val signature = ByteArray(Sign.BYTES)
        sodium.cryptoSignDetached(signature, sigData, sigData.size.toLong(), userEd25519Key.secretKey.asBytes)
        val requestParameters = SubscriptionRequest (
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
            getResponseBody(request.build()).map { response ->
                if (response.isSuccess()) {
                    TextSecurePreferences.setIsUsingFCM(context, true)
                    TextSecurePreferences.setFCMToken(context, token)
                    TextSecurePreferences.setLastFCMUploadTime(context, System.currentTimeMillis())
                } else {
                    val (_, message) = response.errorInfo()
                    Log.d("Loki", "Couldn't register for FCM due to error: $message.")
                }
            }.fail { exception ->
                Log.d("Loki", "Couldn't register for FCM due to error: ${exception}.")
            }
        }
    }

    private fun getResponseBody(request: Request): Promise<SubscriptionResponse, Exception> {
        return OnionRequestAPI.sendOnionRequest(request,
            PushNotificationAPI.server,
            PushNotificationAPI.serverPublicKey, Version.V4).map { response ->
            Json.decodeFromStream(response.body!!.inputStream())
        }
    }


}