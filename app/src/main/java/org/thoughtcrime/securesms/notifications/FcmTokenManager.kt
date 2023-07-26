package org.thoughtcrime.securesms.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.session.libsession.utilities.TextSecurePreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FcmTokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val expiryManager =  ExpiryManager(context)

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
