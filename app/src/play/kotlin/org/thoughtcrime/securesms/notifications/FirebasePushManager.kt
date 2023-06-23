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
import org.session.libsession.messaging.sending_receiving.notifications.PushManagerV1
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

class FirebasePushManager(
    private val context: Context
    ): PushManager {

    private val pushManagerV2 = PushManagerV2(context)

    companion object {
        const val maxRetryCount = 4
    }

    private val tokenManager = FcmTokenManager(context, ExpiryManager(context))
    private var firebaseInstanceIdJob: Job? = null

    @Synchronized
    override fun refresh(force: Boolean) {
        Log.d(TAG, "refresh() called with: force = $force")

        firebaseInstanceIdJob?.apply {
            if (force) cancel() else if (isActive) return
        }

        firebaseInstanceIdJob = getFcmInstanceId { task ->
            when {
                task.isSuccessful -> try { task.result?.token?.let { refresh(it, force).get() } } catch(e: Exception) { Log.e(TAG, "refresh() failed", e) }
                else -> Log.w(TAG, "getFcmInstanceId failed." + task.exception)
            }
        }
    }

    private fun refresh(token: String, force: Boolean): Promise<*, Exception> {
        Log.d(TAG, "refresh() called")

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
    ): Promise<*, Exception> = PushManagerV1.register(
        token = token,
        publicKey = publicKey
    ) and pushManagerV2.register(
        token, publicKey, userEd25519Key, namespaces
    ) fail {
        Log.e(TAG, "registerBoth failed", it)
    } success {
        Log.d(TAG, "registerBoth success... saving token!!")
        tokenManager.fcmToken = token
    }

    private fun unregister(
        token: String,
        userPublicKey: String,
        userEdKey: KeyPair
    ): Promise<*, Exception> = PushManagerV1.unregister() and pushManagerV2.unregister(
        token, userPublicKey, userEdKey
    ) fail {
        Log.e(TAG, "unregisterBoth failed", it)
    } success {
        tokenManager.fcmToken = null
    }

    fun decrypt(decode: ByteArray) = pushManagerV2.decrypt(decode)
}
