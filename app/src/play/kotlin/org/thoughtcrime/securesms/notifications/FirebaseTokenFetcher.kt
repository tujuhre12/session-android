package org.thoughtcrime.securesms.notifications

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.InstanceIdResult
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.session.libsignal.utilities.Log
import javax.inject.Inject
import javax.inject.Singleton

private val TAG = FirebaseTokenFetcher::class.java.name

@Singleton
class FirebaseTokenFetcher @Inject constructor(
    private val pushRegistry: Lazy<PushRegistry>,
): TokenFetcher {
    override fun fetch(): Job = MainScope().launch(Dispatchers.IO) {
        FirebaseInstanceId.getInstance().instanceId
            .also(Tasks::await)
            .also { if (!isActive) return@launch } // don't 'complete' task if we were canceled
            .process()
    }

    private fun Task<InstanceIdResult>.process() = when {
        isSuccessful -> try {
            result?.token?.let {
                pushRegistry.get().refresh(it, force = true).get()
            }
        } catch (e: Exception) {
            onFail(e)
        }
        else -> exception?.let(::onFail)
    }

    private fun onFail(e: Exception) = Log.e(TAG, "fetch failed", e)
}

