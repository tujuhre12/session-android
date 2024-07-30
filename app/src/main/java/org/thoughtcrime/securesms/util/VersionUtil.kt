package org.thoughtcrime.securesms.util

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.session.libsession.messaging.file_server.FileServerApi
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import java.util.concurrent.TimeUnit

class VersionUtil(
    private val prefs: TextSecurePreferences
) {
    private val TAG: String = VersionUtil::class.java.simpleName
    private val FOUR_HOURS: Long = TimeUnit.HOURS.toMillis(4)

    private val handler = Handler(Looper.getMainLooper())
    private val runnable: Runnable

    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    init {
        runnable = Runnable {
            fetchAndScheduleNextVersionCheck()
        }
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

    private fun fetchAndScheduleNextVersionCheck() {
        fetchVersionData()
        handler.postDelayed(runnable, FOUR_HOURS)
    }

    private fun fetchVersionData() {
        // only perform this if at least 4h has elapsed since th last successful check
        val lastCheck = System.currentTimeMillis() - prefs.getLastVersionCheck()
        if (lastCheck < FOUR_HOURS) return

        job?.cancel()
        job = scope.launch {
            try {
                // perform the version check
                val clientVersion = FileServerApi.getClientVersion()
                Log.i(TAG, "Fetched version data: $clientVersion")
                prefs.setLastVersionCheck()
            } catch (e: Exception) {
                // we can silently ignore the error
                Log.e(TAG, "Error fetching version data: $e")
            }
        }
    }
}