package org.thoughtcrime.securesms.notifications

import android.content.Context
import com.huawei.hms.aaid.HmsInstanceId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HuaweiTokenFetcher @Inject constructor(
    @ApplicationContext private val context: Context
): TokenFetcher {
    override fun fetch(): Job {
        val hmsInstanceId = HmsInstanceId.getInstance(context)

        return MainScope().launch(Dispatchers.IO) {
            val appId = "107205081"
            val tokenScope = "HCM"
            // getToken returns an empty string, but triggers the service to initialize.
            hmsInstanceId.getToken(appId, tokenScope)
        }
    }
}
