package org.thoughtcrime.securesms.notifications

import android.content.Context
import com.huawei.hms.aaid.HmsInstanceId
import kotlinx.coroutines.Job

class HuaweiPushManager(val context: Context): PushManager {
    private var huaweiPushInstanceIdJob: Job? = null

    @Synchronized
    override fun refresh(force: Boolean) {
        val huaweiPushInstanceIdJob = huaweiPushInstanceIdJob

        huaweiPushInstanceIdJob?.apply {
            if (force) cancel() else if (isActive) return
        }

        val hmsInstanceId = HmsInstanceId.getInstance(context)

        val task = hmsInstanceId.aaid

//        HuaweiPushNotificationService().start()
//
//        huaweiPushInstanceIdJob = HmsInstanceId.getInstance(this) { hmsInstanceId ->
//            RegisterHuaweiPushService(hmsInstanceId, this, force).start()
//            Unit.INSTANCE
//        }
    }
}
