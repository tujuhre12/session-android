package org.session.libsession.messaging.sending_receiving.notifications

import android.content.Context
import org.session.libsession.utilities.recipients.Recipient

interface MessageNotifier {
    fun setHomeScreenVisible(isVisible: Boolean)
    fun setVisibleThread(threadId: Long)
    fun setLastDesktopActivityTimestamp(timestamp: Long)
    fun notifyMessageDeliveryFailed(context: Context?, recipient: Recipient?, threadId: Long)
    fun cancelDelayedNotifications()
    fun updateNotification(context: Context)
    fun updateNotificationRegardingSpecificThread(context: Context, threadId: Long)
    fun updateNotificationRegardingSpecificThreadWithOptionalAudio(context: Context, threadId: Long, playNotificationAudio: Boolean)
    fun updateNotificationWithReminderCountAndOptionalAudio(context: Context, playNotificationAudio: Boolean, reminderCount: Int)
    fun clearReminder(context: Context)
}
