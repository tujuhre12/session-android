package org.thoughtcrime.securesms.notifications

import android.content.Context
import org.session.libsession.utilities.TextSecurePreferences

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
        get() = TextSecurePreferences.getLastFCMUploadTime(context)
        set(value) = TextSecurePreferences.setLastFCMUploadTime(context, value)

    private fun currentTime() = System.currentTimeMillis()
}
