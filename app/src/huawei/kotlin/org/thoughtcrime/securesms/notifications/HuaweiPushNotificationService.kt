package org.thoughtcrime.securesms.notifications

import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import org.session.libsession.messaging.jobs.BatchMessageReceiveJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveParameters
import org.session.libsession.messaging.utilities.MessageWrapper
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import javax.inject.Inject

@AndroidEntryPoint
class HuaweiPushNotificationService: HmsMessageService() {

    @Inject lateinit var pushManager: PushManager
    @Inject lateinit var pushHandler: PushHandler

    override fun onNewToken(token: String?, bundle: Bundle?) {
        Log.d("Loki", "New HCM token: $token.")
        pushManager.refresh(true)
    }

    override fun onMessageReceived(message: RemoteMessage?) {
        pushHandler.onPush(message?.dataOfMap)
    }

    override fun onDeletedMessages() {
        pushManager.refresh(true)
    }
}
