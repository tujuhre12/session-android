package org.thoughtcrime.securesms.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import org.session.libsession.utilities.Device
import org.session.libsignal.utilities.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FirebasePushManager"

@Singleton
class FirebasePushManager @Inject constructor(
    @ApplicationContext private val context: Context
): PushManager {

    @Inject lateinit var genericPushManager: GenericPushManager

    private var firebaseInstanceIdJob: Job? = null

    @Synchronized
    override fun refresh(force: Boolean) {
        Log.d(TAG, "refresh() called with: force = $force")

        firebaseInstanceIdJob?.apply {
            if (force) cancel() else if (isActive) return
        }

        firebaseInstanceIdJob = getFcmInstanceId { task ->
            when {
                task.isSuccessful -> try { task.result?.token?.let { genericPushManager.refresh(it, force).get() } } catch(e: Exception) { Log.e(TAG, "refresh() failed", e) }
                else -> Log.w(TAG, "getFcmInstanceId failed." + task.exception)
            }
        }
    }
}
