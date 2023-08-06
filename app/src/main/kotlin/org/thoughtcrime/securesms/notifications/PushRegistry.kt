package org.thoughtcrime.securesms.notifications

import android.content.Context
import com.goterl.lazysodium.utils.KeyPair
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.combine.and
import org.session.libsession.messaging.sending_receiving.notifications.PushRegistryV1
import org.session.libsession.utilities.Device
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.emptyPromise
import org.thoughtcrime.securesms.crypto.KeyPairUtilities
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GenericPushManager"

@Singleton
class PushRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val device: Device,
    private val tokenManager: PushTokenManager,
    private val pushRegistryV2: PushRegistryV2,
) {

    private var firebaseInstanceIdJob: Job? = null

    fun refresh(force: Boolean) {
        Log.d(TAG, "refresh() called with: force = $force")

        firebaseInstanceIdJob?.apply {
            if (force) cancel() else if (isActive) return
        }

        firebaseInstanceIdJob = tokenManager.fetchToken()
    }

    fun refresh(token: String?, force: Boolean): Promise<*, Exception> {
        Log.d(TAG, "refresh($token, $force) called")

        token ?: return emptyPromise()
        val userPublicKey = TextSecurePreferences.getLocalNumber(context) ?: return emptyPromise()
        val userEdKey = KeyPairUtilities.getUserED25519KeyPair(context) ?: return emptyPromise()

        return when {
            tokenManager.isPushEnabled -> register(force, token, userPublicKey, userEdKey)
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
    ): Promise<*, Exception> {
        android.util.Log.d(
            TAG,
            "register() called with: token = $token, publicKey = $publicKey, userEd25519Key = $userEd25519Key, namespaces = $namespaces"
        )

        val v1 = PushRegistryV1.register(
            device = device,
            token = token,
            publicKey = publicKey
        ) fail {
            Log.e(TAG, "register v1 failed", it)
        }

        val v2 = pushRegistryV2.register(
            device, token, publicKey, userEd25519Key, namespaces
        ) fail {
            Log.e(TAG, "register v2 failed", it)
        }

        return v1 and v2 success {
            Log.d(TAG, "registerBoth success... saving token!!")
            tokenManager.fcmToken = token
        }
    }

    private fun unregister(
        token: String,
        userPublicKey: String,
        userEdKey: KeyPair
    ): Promise<*, Exception> = PushRegistryV1.unregister() and pushRegistryV2.unregister(
        device, token, userPublicKey, userEdKey
    ) fail {
        Log.e(TAG, "unregisterBoth failed", it)
    } success {
        tokenManager.fcmToken = null
    }
}
