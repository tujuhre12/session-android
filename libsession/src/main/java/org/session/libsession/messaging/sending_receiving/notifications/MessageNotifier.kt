package org.session.libsession.messaging.sending_receiving.notifications

import android.content.Context
import org.session.libsession.utilities.recipients.Recipient

interface MessageNotifier {
    fun setHomeScreenVisible(isVisible: Boolean)
    fun setVisibleThread(threadId: Long)
    fun setLastDesktopActivityTimestamp(timestamp: Long)
    fun notifyMessageDeliveryFailed(context: Context?, recipient: Recipient?, threadId: Long)
    fun cancelDelayedNotifications()
    fun resetAllNotificationsSilently(context: Context)
    fun updateNotificationForSpecificThread(context: Context, threadId: Long)
    fun updateNotificationForSpecificThreadWithOptionalAudio(context: Context, threadId: Long, playNotificationAudio: Boolean)
    fun updateNotificationWithReminderCountAndOptionalAudio(context: Context,  reminderCount: Int, playNotificationAudio: Boolean)
    fun clearReminder(context: Context)
}
