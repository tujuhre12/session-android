package org.thoughtcrime.securesms.notifications

import android.content.Context
import org.session.libsession.utilities.TextSecurePreferences

class FcmTokenManager(
    private val context: Context,
    private val expiryManager: ExpiryManager
) {
    val isUsingFCM get() = TextSecurePreferences.isUsingFCM(context)

    var fcmToken
        get() = TextSecurePreferences.getFCMToken(context)
        set(value) {
            TextSecurePreferences.setFCMToken(context, value)
            if (value != null) markTime() else clearTime()
        }

    val requiresUnregister get() = fcmToken != null

    private fun clearTime() = expiryManager.clearTime()
    private fun markTime() = expiryManager.markTime()
    private fun isExpired() = expiryManager.isExpired()

    fun isInvalid(): Boolean = fcmToken == null || isExpired()
}
