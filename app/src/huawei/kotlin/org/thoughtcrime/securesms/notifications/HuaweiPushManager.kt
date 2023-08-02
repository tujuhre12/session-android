package org.thoughtcrime.securesms.notifications

import android.content.Context
import android.util.Log
import com.huawei.hmf.tasks.Tasks
import com.huawei.hms.aaid.HmsInstanceId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.session.libsession.utilities.TextSecurePreferences
import javax.inject.Inject
import javax.inject.Singleton

private val TAG = HuaweiPushManager::class.java.name

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

        val appId = "107146885"
        val tokenScope = "HCM"
        val hmsInstanceId = HmsInstanceId.getInstance(context)

        MainScope().launch(Dispatchers.IO) {
            val token = hmsInstanceId.getToken(appId, tokenScope)

            Log.d(TAG, "refresh() with huawei token: $token")

            genericPushManager.refresh(token, force)
        }
    }
}
