package org.thoughtcrime.securesms.notifications

import android.os.Bundle
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import java.lang.Exception
import javax.inject.Inject

private val TAG = HuaweiPushService::class.java.simpleName

@AndroidEntryPoint
class HuaweiPushService: HmsMessageService() {

    init {
        Log.d(TAG, "init Huawei Service")
    }

    @Inject lateinit var pushRegistry: PushRegistry
    @Inject lateinit var pushReceiver: PushReceiver

    override fun onCreate() {
        Log.d(TAG, "onCreate Huawei Service")
        super.onCreate()
    }

    override fun onMessageReceived(message: RemoteMessage?) {
        Log.d(TAG, "onMessageReceived: $message.")
        pushReceiver.onPush(message?.data?.let(Base64::decode))
    }

    override fun onMessageSent(p0: String?) {
        Log.d(TAG, "onMessageSent() called with: p0 = $p0")
        super.onMessageSent(p0)
    }

    override fun onSendError(p0: String?, p1: Exception?) {
        Log.d(TAG, "onSendError() called with: p0 = $p0, p1 = $p1")
        super.onSendError(p0, p1)
    }

    override fun onMessageDelivered(p0: String?, p1: Exception?) {
        Log.d(TAG, "onMessageDelivered")
        super.onMessageDelivered(p0, p1)
    }


    override fun onNewToken(p0: String?) {
        Log.d(TAG, "onNewToken")
        super.onNewToken(p0)
    }

    override fun onNewToken(token: String?, bundle: Bundle?) {
        Log.d(TAG, "New HCM token: $token.")

        TextSecurePreferences.setPushToken(this, token)

        pushRegistry.refresh(token, true)
    }

    override fun onDeletedMessages() {
        pushRegistry.refresh(true)
    }
}
