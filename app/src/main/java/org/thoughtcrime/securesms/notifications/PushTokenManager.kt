package org.thoughtcrime.securesms.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import org.session.libsession.utilities.TextSecurePreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushTokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenFetcher: TokenFetcher
) {
    private val expiryManager =  ExpiryManager(context)

    val isPushEnabled get() = TextSecurePreferences.isPushEnabled(context)

    var fcmToken
        get() = TextSecurePreferences.getPushToken(context)
        set(value) {
            TextSecurePreferences.setPushToken(context, value)
            if (value != null) markTime() else clearTime()
        }

    val requiresUnregister get() = fcmToken != null

    private fun clearTime() = expiryManager.clearTime()
    private fun markTime() = expiryManager.markTime()
    private fun isExpired() = expiryManager.isExpired()

    fun isInvalid(): Boolean = fcmToken == null || isExpired()
    fun fetchToken(): Job = tokenFetcher.fetch()
}
