package org.thoughtcrime.securesms.notifications

import android.os.Bundle
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import org.session.libsignal.utilities.Log
import java.lang.Exception
import javax.inject.Inject

@AndroidEntryPoint
class HuaweiPushNotificationService: HmsMessageService() {

    init {
        Log.d("pnh", "init Huawei Service")
    }

    @Inject lateinit var pushManager: PushManager
    @Inject lateinit var pushHandler: PushHandler

    override fun onCreate() {
        Log.d("pnh", "onCreate Huawei Service")
        super.onCreate()
    }

    override fun onMessageDelivered(p0: String?, p1: Exception?) {
        Log.d("pnh", "onMessageDelivered")
        super.onMessageDelivered(p0, p1)
    }

    override fun onMessageSent(p0: String?) {
        Log.d("pnh", "onMessageSent")
        super.onMessageSent(p0)
    }

    override fun onNewToken(p0: String?) {
        Log.d("pnh", "onNewToken")
        super.onNewToken(p0)
    }

    override fun onNewToken(token: String?, bundle: Bundle?) {
        Log.d("pnh", "New HCM token: $token.")
        pushManager.refresh(true)
    }
    override fun onMessageReceived(message: RemoteMessage?) {
        Log.d("pnh", "onMessageReceived: $message.")
        pushHandler.onPush(message?.dataOfMap)
    }
    override fun onDeletedMessages() {
        pushManager.refresh(true)
    }
}
