package org.thoughtcrime.securesms.notifications

import android.content.Context
import com.huawei.hmf.tasks.Tasks
import com.huawei.hms.aaid.HmsInstanceId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HuaweiPushManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val genericPushManager: GenericPushManager
): PushManager {
    private var huaweiPushInstanceIdJob: Job? = null

    @Synchronized
    override fun refresh(force: Boolean) {
        val huaweiPushInstanceIdJob = huaweiPushInstanceIdJob

        huaweiPushInstanceIdJob?.apply {
            if (force) cancel() else if (isActive) return
        }

        val hmsInstanceId = HmsInstanceId.getInstance(context)

        MainScope().launch(Dispatchers.IO) {
            val task = hmsInstanceId.aaid
            Tasks.await(task)
            if (!isActive) return@launch // don't 'complete' task if we were canceled
            task.result?.id?.let { genericPushManager.refresh(it, force) }
        }
    }
}
