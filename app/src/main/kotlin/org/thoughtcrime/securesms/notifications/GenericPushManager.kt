package org.thoughtcrime.securesms.notifications

import android.content.Context
import com.goterl.lazysodium.utils.KeyPair
import dagger.hilt.android.qualifiers.ApplicationContext
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.combine.and
import org.session.libsession.messaging.sending_receiving.notifications.PushManagerV1
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
class GenericPushManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val device: Device,
    private val tokenManager: FcmTokenManager,
    private val pushManagerV2: PushManagerV2,
) {
    fun refresh(token: String, force: Boolean): Promise<*, Exception> {
        Log.d(TAG, "refresh() called")

        val userPublicKey = TextSecurePreferences.getLocalNumber(context) ?: return emptyPromise()
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
    ): Promise<*, Exception> {
        val v1 = PushManagerV1.register(
            device = device,
            token = token,
            publicKey = publicKey
        ) fail {
            Log.e(TAG, "register v1 failed", it)
        }

        val v2 = pushManagerV2.register(
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
    ): Promise<*, Exception> = PushManagerV1.unregister() and pushManagerV2.unregister(
        device, token, userPublicKey, userEdKey
    ) fail {
        Log.e(TAG, "unregisterBoth failed", it)
    } success {
        tokenManager.fcmToken = null
    }
}
