package org.thoughtcrime.securesms.notifications

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import org.session.libsession.messaging.jobs.BatchMessageReceiveJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveParameters
import org.session.libsession.messaging.utilities.MessageWrapper
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import javax.inject.Inject

private const val TAG = "PushNotificationService"

@AndroidEntryPoint
class PushNotificationService : FirebaseMessagingService() {

    @Inject lateinit var pushManager: PushManager
    @Inject lateinit var pushHandler: PushHandler

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        TextSecurePreferences.getLocalNumber(this) ?: return
        if (TextSecurePreferences.getFCMToken(this) != token) {
            pushManager.refresh(true)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "Received a push notification.")
        pushHandler.onPush(message.data)
    }

    override fun onDeletedMessages() {
        Log.d(TAG, "Called onDeletedMessages.")
        super.onDeletedMessages()
        pushManager.refresh(true)
    }
}
