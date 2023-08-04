package org.thoughtcrime.securesms.notifications

import kotlinx.coroutines.Job
import org.session.libsignal.utilities.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FirebasePushManager"

@Singleton
class FirebasePushManager @Inject constructor(
    private val genericPushManager: GenericPushManager
): PushManager {

    private var firebaseInstanceIdJob: Job? = null

    @Synchronized
    override fun refresh(force: Boolean) {
        Log.d(TAG, "refresh() called with: force = $force")

        firebaseInstanceIdJob?.apply {
            when {
                force -> cancel()
                isActive -> return
            }
        }

        firebaseInstanceIdJob = getFcmInstanceId { task ->
            when {
                task.isSuccessful -> try { task.result?.token?.let {
                    genericPushManager.refresh(it, force).get()
                } } catch(e: Exception) { Log.e(TAG, "refresh() failed", e) }
                else -> Log.w(TAG, "getFcmInstanceId failed." + task.exception)
            }
        }
    }
}
