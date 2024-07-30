package org.thoughtcrime.securesms.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.session.libsession.messaging.file_server.FileServerApi
import org.session.libsession.utilities.TextSecurePreferences

class VersionUtil(
    private val context: Context,
    private val prefs: TextSecurePreferences
) {

    private val handler = Handler(Looper.getMainLooper())
    private val runnable: Runnable

    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

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

    fun clear() {
        job?.cancel()
        stopTimedVersionCheck()
    }

    fun fetchVersionData() {
        Log.d("", "***** Trying to fetch version. Last check: ${prefs.getLastVersionCheck()}")
        // only perform this if at least 4h has elapsed since th last successful check
        if(prefs.getLastVersionCheck() < FOUR_HOURS) return

        job = scope.launch {
            try {
                // perform the version check
                Log.d("", "***** Fetching last version")
                val clientVersion = FileServerApi.getClientVersion()
                Log.d("", "***** Got version: $clientVersion")
                prefs.setLastVersionCheck()
            } catch (e: Exception) {
                // we can silently ignore the error
                Log.e("", "***** Error fetching version", e)
            }
        }
    }

    companion object {
        private const val FOUR_HOURS = 4 * 60 * 60 * 1000L // 4 hours in milliseconds
    }
}