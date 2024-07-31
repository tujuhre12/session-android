package org.thoughtcrime.securesms.util

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.session.libsession.messaging.file_server.FileServerApi
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours

private val TAG: String = VersionDataFetcher::class.java.simpleName
private val REFRESH_TIME_MS = 4.hours.inWholeMilliseconds

@Singleton
class VersionDataFetcher @Inject constructor(
    private val prefs: TextSecurePreferences
) {
    private val handler = Handler(Looper.getMainLooper())
    private val fetchVersionData = Runnable {
        scope.launch {
            try {
                // Perform the version check
                val clientVersion = FileServerApi.getClientVersion()
                Log.i(TAG, "Fetched version data: $clientVersion")
                prefs.setLastVersionCheck()
                startTimedVersionCheck()
            } catch (e: Exception) {
                // We can silently ignore the error
                Log.e(TAG, "Error fetching version data", e)
                // Schedule the next check for 4 hours from now, but do not setLastVersionCheck
                // so the app will retry when the app is next foregrounded.
                startTimedVersionCheck(REFRESH_TIME_MS)
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    fun startTimedVersionCheck(
        delayMillis: Long = REFRESH_TIME_MS + prefs.getLastVersionCheck() - System.currentTimeMillis()
    ) {
        stopTimedVersionCheck()

        // Call immediately if 4h or more has elapsed since the last successful check else schedule.
        handler.postDelayed(fetchVersionData, delayMillis)
    }

    fun stopTimedVersionCheck() {
        handler.removeCallbacks(fetchVersionData)
    }
}