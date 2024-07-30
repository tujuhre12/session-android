package org.thoughtcrime.securesms.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.session.libsession.utilities.TextSecurePreferences

class VersionUtil(
    private val context: Context,
    private val prefs: TextSecurePreferences
) {

    private val handler = Handler(Looper.getMainLooper())
    private val runnable: Runnable

    init {
        runnable = Runnable {
            // Task to be executed every 4 hours
            fetchVersionData()
        }

        // Re-schedule the task
        handler.postDelayed(runnable, FOUR_HOURS)
    }

    fun startTimedVersionCheck() {
        handler.post(runnable)
    }

    fun stopTimedVersionCheck() {
        handler.removeCallbacks(runnable)
    }

    private fun fetchVersionData() {
        // only perform this if at least 4h has elapsed since th last successful check
        if(prefs.getLastVersionCheck() < FOUR_HOURS) return


    }

    companion object {
        private const val FOUR_HOURS = 4 * 60 * 60 * 1000L // 4 hours in milliseconds
    }
}