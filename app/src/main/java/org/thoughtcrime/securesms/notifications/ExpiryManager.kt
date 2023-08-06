package org.thoughtcrime.securesms.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.session.libsession.utilities.TextSecurePreferences
import javax.inject.Inject
import javax.inject.Singleton

class ExpiryManager(
    private val context: Context,
    private val interval: Int = 12 * 60 * 60 * 1000
) {
    fun isExpired() = currentTime() > time + interval

    fun markTime() {
        time = currentTime()
    }

    fun clearTime() {
        time = 0
    }

    private var time
        get() = TextSecurePreferences.getPushRegisterTime(context)
        set(value) = TextSecurePreferences.setPushRegisterTime(context, value)

    private fun currentTime() = System.currentTimeMillis()
}
