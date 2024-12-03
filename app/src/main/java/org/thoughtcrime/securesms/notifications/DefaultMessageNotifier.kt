/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.AsyncTask
import android.os.Build
import android.text.TextUtils
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.annimon.stream.Stream
import com.squareup.phrase.Phrase
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.Volatile
import me.leolin.shortcutbadger.ShortcutBadger
import network.loki.messenger.R
import org.session.libsession.messaging.sending_receiving.notifications.MessageNotifier
import org.session.libsession.messaging.utilities.SodiumUtilities.blindedKeyPair
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.session.libsession.utilities.ServiceUtil
import org.session.libsession.utilities.StringSubstitutionConstants.EMOJI_KEY
import org.session.libsession.utilities.TextSecurePreferences.Companion.areNotificationsDisabled
import org.session.libsession.utilities.TextSecurePreferences.Companion.getLocalNumber
import org.session.libsession.utilities.TextSecurePreferences.Companion.getNotificationPrivacy
import org.session.libsession.utilities.TextSecurePreferences.Companion.getRepeatAlertsCount
import org.session.libsession.utilities.TextSecurePreferences.Companion.hasHiddenMessageRequests
import org.session.libsession.utilities.TextSecurePreferences.Companion.areNotificationsEnabled
import org.session.libsession.utilities.TextSecurePreferences.Companion.removeHasHiddenMessageRequestsPreference
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Util
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.contacts.ContactUtil
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionUtilities.highlightMentions
import org.thoughtcrime.securesms.crypto.KeyPairUtilities.getUserED25519KeyPair
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent.Companion.get
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.SessionMetaProtocol.canUserReplyToNotification
import org.thoughtcrime.securesms.util.SpanUtil

/**
 * Handles posting system notifications for new messages.
 *
 *
 * @author Moxie Marlinspike
 */
class DefaultMessageNotifier : MessageNotifier {

    //private var notificationState: NotificationState = NotificationState()

    override fun setVisibleThread(threadId: Long) {
        visibleThread = threadId
    }

    override fun setHomeScreenVisible(isVisible: Boolean) {
        homeScreenVisible = isVisible
    }

    override fun setLastDesktopActivityTimestamp(timestamp: Long) {
        lastDesktopActivityTimestamp = timestamp
    }

    override fun notifyMessageDeliveryFailed(context: Context?, recipient: Recipient?, threadId: Long) {
        // We do not provide notifications for message delivery failure.
    }

    override fun cancelDelayedNotifications() {
        executor.cancel()
    }

    private fun cancelActiveNotifications(context: Context): Boolean {
        val notifications = ServiceUtil.getNotificationManager(context)
        val hasNotifications = notifications.activeNotifications.size > 0
        notifications.cancel(SUMMARY_NOTIFICATION_ID)

        try {
            val activeNotifications = notifications.activeNotifications

            for (activeNotification in activeNotifications) {
                notifications.cancel(activeNotification.id)
            }
        } catch (e: Throwable) {
            // XXX Appears to be a ROM bug, see #6043
            Log.w(TAG, e)
            notifications.cancelAll()
        }
        return hasNotifications
    }

    private fun cancelOrphanedNotifications(context: Context, notificationState: NotificationState) {
        try {
            val notifications = ServiceUtil.getNotificationManager(context)
            val activeNotifications = notifications.activeNotifications

            for (notification in activeNotifications) {
                var validNotification = false

                if (notification.id != SUMMARY_NOTIFICATION_ID && notification.id != KeyCachingService.SERVICE_RUNNING_ID && notification.id != FOREGROUND_ID && notification.id != PENDING_MESSAGES_ID) {
                    for (item in notificationState.notifications) {
                        if (notification.id.toLong() == (SUMMARY_NOTIFICATION_ID + item.threadId)) {
                            validNotification = true
                            break
                        }
                    }

                    if (!validNotification) {
                        Log.i(TAG, "Cancelling notification with id: {notification.id} as orphaned.")
                        notifications.cancel(notification.id)
                    }
                }
            }
        } catch (e: Throwable) {
            // XXX Android ROM Bug, see #6043
            Log.w(TAG, e)
        }
    }

    override fun updateNotification(context: Context) {
        Log.w("ACL", "-1? Hit updateNotification(context)")
        if (areNotificationsDisabled(context)) return

        updateNotification(context, signal = false, reminderCount = 0)
    }

    override fun updateNotification(context: Context, threadId: Long) {
        Log.i("ACL", "1 - Hit updateNotification(context, threadId) - printing stack trace")
        Exception().printStackTrace()

        if (System.currentTimeMillis() - lastDesktopActivityTimestamp < DESKTOP_ACTIVITY_PERIOD) {
            Log.i(TAG, "Scheduling delayed notification...")
            executor.execute(DelayedNotification(context, threadId))
        } else {
            updateNotification(context, threadId, true)
        }
    }

    override fun updateNotification(context: Context, threadId: Long, signal: Boolean) {
        Log.i("ACL", "2 - Hit updateNotification(context, threadId, signal)")


        val threads = get(context).threadDatabase()
        val recipient = threads.getRecipientForThreadId(threadId)

        if (recipient == null) {
            Log.e(TAG, "Could not get recipient for threadId: $threadId - bailing.")
            return
        }

        if (areNotificationsDisabled(context)) {
            Log.i(TAG, "Notifications are disabled - aborting notification.")
            return
        }

        if (recipient.isMuted) {
            Log.i(TAG, "Recipient is muted - aborting notification.")
            return
        }








        val lastSeenAndHasSent = threads.getLastSeenAndHasSent(threadId)
        val lastSeenMessageTimestamp: Long             = lastSeenAndHasSent.first()
        val weHaveNotSentAMessageInThisThread: Boolean = !lastSeenAndHasSent.second() // CAREFUL: We invert the value of "hasSent" here!

        // Figure out whether we should display a notification from an unapproved individual recipient or not
        val now = System.currentTimeMillis()

        val notificationIsRegardingMessageRequest = recipient.isIndividualRecipient && recipient.isNotApproved && weHaveNotSentAMessageInThisThread

        if (notificationIsRegardingMessageRequest) {
            Log.i("ACL", "We think this notification is regarding a message request")

            // Whenever we receive a message request we adjust the app to display Message Requests UI element (which the user may have previously hidden)
            removeHasHiddenMessageRequestsPreference(context)

            // If the user is looking at the thread or the home active then there is no need to provide a notification as they can see that there is a new message
            val notificationThreadIsVisible = visibleThread == threadId
            val noNeedForNotificationAboutThisMessageRequest = notificationThreadIsVisible || homeScreenVisible

            if (noNeedForNotificationAboutThisMessageRequest) {
                Log.i(TAG, "Notification thread or home screen visible - skipping notification.")
                return
            } else {

                // We proceed through to call `updateNotification(context, signal, reminderCount, threadId) *PASSING* it a threadId which isn't -1L and then jump out before we hit the bottom call to the same
                updateNotification(context, signal, 0, threadId)
                return
            }
        }

        Log.i("ACL", "Got to end of updateNotification threadId / signal - moving on to updateNotification (and this isn't a message request)")
        updateNotification(context, signal, 0)
    }

    private fun hasExistingNotifications(context: Context): Boolean {
        val notifications = ServiceUtil.getNotificationManager(context)
        try {
            val activeNotifications = notifications.activeNotifications
            return activeNotifications.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Could not get active notifications", e)
            return false
        }
    }

    private fun haveExistingNotificationsRegardingThread(notificationState: NotificationState, threadId: Long): Boolean {

        Log.w("ACL", "Size of notificationState.notifications is: " + notificationState.notifications.size + ", threadId is: " + threadId)

        for (notificationItem in notificationState.notifications) {
            if (threadId == notificationItem.threadId) {
                Log.w("ACL", "Found a match with a notification regarding this threadId: $threadId")
                return true
            }
        }
        return false
    }



    override fun updateNotification(context: Context, signal: Boolean, reminderCount: Int, threadId: Long) {
        Log.i("ACL", "3 Hit updateNotification(context, signal, reminderCount, threadId)")

        var playNotificationAudio = signal // Local copy of the argument so we can modify it
        var unreadMessagesCursor: Cursor? = null

        try {
            unreadMessagesCursor = get(context).mmsSmsDatabase().unread // TODO: add a notification specific lighter query here

            // If we somehow couldn't get the unread messages cursor, or if we're in a bad state where the cursor is after the last
            // message, or if we couldn't get our own number then we clean-up the notification state and bail.
            if ((unreadMessagesCursor == null || unreadMessagesCursor.isAfterLast) || getLocalNumber(context) == null) {
                Log.w(TAG, "Unread messages cursor is corrupt or we couldn't find our own number - cleaning up and bailing.")
                updateBadge(context, 0)
                cancelActiveNotifications(context)
                clearReminder(context)
                return
            }

            try {
                val notificationState = constructNotificationState(context, unreadMessagesCursor)

                // If the threadId argument is not -1L then this notification is regarding a message request - so we'll (potentially) adjust whether we
                if (threadId != -1L) {
                    // Do we already have a notification about this thread?
                    val alreadyHaveANotificationAboutThisThread = haveExistingNotificationsRegardingThread(notificationState, threadId)
                    Log.w("ACL", "Already have notification about thread?: $alreadyHaveANotificationAboutThisThread")

                    // If not, then we'll want to notify and use sound..
                    if (alreadyHaveANotificationAboutThisThread == false) {
                        Log.i("ACL", "Notif. from NA indiv, we have not sent anything and there is no existing notification - notifying WITH sound")
                        playNotificationAudio = true
                    } else {
                        // ..otherwise, if we already have a notification about this thread (i.e., this user has previously sent us a message request
                        // and it's still in the notification state) then we DO NOT notify at all - otherwise we'll end up with several notifications
                        // from the same user saying "You have a message request".
                        Log.i("ACL", "Notif. from NA indiv, we have not sent anything but there IS a notification already - NOT NOTIFYING!!!! BAILING!")

                        // NOTE: The only downside of not showing a notification here is that the timestamp of the first notification is the only one
                        // shown - that is, if you message request, then wait 10 minutes and message them again, only the original (10 minutes ago)
                        // notification exists. Which I don't think is terrible, tbh. If we JUST set `playNotificationAudio = false` here rather than
                        // returning we get a second notification regarding the same thread, there's no audio played, but if we now look at the
                        // notification then we can see it's TWO notifications, shown as two-lines of "You have a message request". To match the
                        // acceptance criteria I'll opt to return here.
                        //playNotificationAudio = false

                        // ALSO: Clearing the Android notification will still land-us here. We have to actively go to "Message Requests -> Clear All"
                        return
                    }
                }




                val now = System.currentTimeMillis()
                // If we were asked to play audio with a notification..
                if (playNotificationAudio) {

                    // .. but it's been less than 5 seconds since the last time we did so then we DO NOT play audio with the notification.
                    if (now - lastAudibleNotificationTimestamp < MIN_AUDIBLE_PERIOD_MILLIS) {
                        Log.i(TAG, "Too many audible notifications - skipping audio on this notification.")
                        playNotificationAudio = false
                    } else {
                        // However if it's been more than 5 seconds since we last did so then we WILL play the audio, and we'll update our timestamp accordingly.
                        lastAudibleNotificationTimestamp = now
                    }
                }

                if (notificationState.hasMultipleThreads()) {
                    Log.w("ACL", "Notification state has multiple threads")

                    // Update all threads in the notification state via SINGLE-THREAD notifications where we DO NOT signal the user and we DO bundle the notifications..
                    for (threadId in notificationState.threads) {
                        sendSingleThreadNotification(context, NotificationState(notificationState.getNotificationsForThread(threadId)), signal = false, bundled = true)
                    }

                    // .. then send a MULTIPLE THREAD notification
                    sendMultipleThreadNotification(context, notificationState, playNotificationAudio)

                } else if (notificationState.notificationCount > 0) {
                    Log.w("ACL", "Notification state has a single thread")
                    sendSingleThreadNotification(context, notificationState, signal = playNotificationAudio, bundled = false)
                }
//            else {
//
//                    // ACL remove this else block
//                    Log.w("ACL", "Would previously cancel active notifications - NOT doing that")
//                    //cancelActiveNotifications(context)
//                }

                cancelOrphanedNotifications(context, notificationState)
                updateBadge(context, notificationState.notificationCount)

                // THIS MAKES ABSOLUTELY NO SENSE - WHY WOULD WE SCHEDULE A REMINDER FOR A NOTIFICATION?!
                if (playNotificationAudio) {
                    scheduleReminder(context, reminderCount)
                }
            }
            catch (e: Exception) {
                Log.e(TAG, "Error creating notification", e)
            }

        } finally {
            unreadMessagesCursor?.close()
        }
    }

    // Note: The `signal` parameter means "play an audio signal for the notification".
    private fun sendSingleThreadNotification(
        context: Context,
        notificationState: NotificationState,
        signal: Boolean,
        bundled: Boolean
    ) {
        Log.i(TAG, "Hit sendSingleThreadNotification()  signal: $signal  bundled: $bundled")

        // SO WE CANT DISPLAY A NOTIFICATION IF THERE ARE NO NOTIFICATIONS - we're in the process of adding one FFS!
//        if (notificationState.notifications.isEmpty()) {
//            if (!bundled) cancelActiveNotifications(context)
//            Log.i(TAG, "Empty notification state. Skipping.")
//            return
//        }

        val builder = SingleRecipientNotificationBuilder(context, getNotificationPrivacy(context))
        val notifications = notificationState.notifications
        val recipient = notifications[0].recipient
        val notificationId = (SUMMARY_NOTIFICATION_ID + (if (bundled) notifications[0].threadId else 0)).toInt()
        val messageIdTag = notifications[0].timestamp.toString()

        val notificationManager = ServiceUtil.getNotificationManager(context)
        for (notification in notificationManager.activeNotifications) {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && notification.isAppGroup == bundled)
                && (messageIdTag == notification.notification.extras.getString(LATEST_MESSAGE_ID_TAG))
            ) {
                return
            }
        }

        val timestamp = notifications[0].timestamp
        if (timestamp != 0L) builder.setWhen(timestamp)

        builder.putStringExtra(LATEST_MESSAGE_ID_TAG, messageIdTag)

        val text = notifications[0].text

        builder.setThread(notifications[0].recipient)
        builder.setMessageCount(notificationState.notificationCount)

        val builderCS = text ?: ""
        val ss = highlightMentions(
            builderCS,
            false,
            false,
            true,
            if (bundled) notifications[0].threadId else 0,
            context
        )

        builder.setPrimaryMessageBody(
            recipient,
            notifications[0].individualRecipient,
            ss,
            notifications[0].slideDeck
        )

        builder.setContentIntent(notifications[0].getPendingIntent(context))
        builder.setDeleteIntent(notificationState.getDeleteIntent(context))
        builder.setOnlyAlertOnce(!signal)
        builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        builder.setAutoCancel(true)

        val replyMethod = ReplyMethod.forRecipient(context, recipient)

        val canReply = canUserReplyToNotification(recipient)

        val quickReplyIntent = if (canReply) notificationState.getQuickReplyIntent(context, recipient) else null
        val remoteReplyIntent = if (canReply) notificationState.getRemoteReplyIntent(context, recipient, replyMethod) else null

        builder.addActions(
            notificationState.getMarkAsReadIntent(context, notificationId),
            quickReplyIntent,
            remoteReplyIntent,
            replyMethod
        )

        if (canReply) {
            builder.addAndroidAutoAction(
                notificationState.getAndroidAutoReplyIntent(context, recipient),
                notificationState.getAndroidAutoHeardIntent(context, notificationId),
                notifications[0].timestamp
            )
        }

        val iterator: ListIterator<NotificationItem> = notifications.listIterator(notifications.size)

        while (iterator.hasPrevious()) {
            val item = iterator.previous()
            builder.addMessageBody(item.recipient, item.individualRecipient, item.text)
        }

        if (signal) {
            builder.setAlarms(notificationState.getRingtone(context), notificationState.vibrate)
            builder.setTicker(
                notifications[0].individualRecipient,
                notifications[0].text
            )
        }

        if (bundled) {
            builder.setGroup(NOTIFICATION_GROUP)
            builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        }

        val notification = builder.build()

        // TODO - ACL to fix this properly & will do on 2024-08-26, but just skipping for now so review can start
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        Log.i(TAG, "Posted notification. $notification")
    }

    // Note: The `signal` parameter means "play an audio signal for the notification".
    private fun sendMultipleThreadNotification(
        context: Context,
        notificationState: NotificationState,
        signal: Boolean
    ) {
        Log.i(TAG, "sendMultiThreadNotification()  signal: $signal")

        val builder = MultipleRecipientNotificationBuilder(context, getNotificationPrivacy(context))
        val notifications = notificationState.notifications

        builder.setMessageCount(notificationState.notificationCount, notificationState.threadCount)
        builder.setMostRecentSender(notifications[0].individualRecipient, notifications[0].recipient)
        builder.setGroup(NOTIFICATION_GROUP)
        builder.setDeleteIntent(notificationState.getDeleteIntent(context))
        builder.setOnlyAlertOnce(!signal)
        builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        builder.setAutoCancel(true)

        val messageIdTag = notifications[0].timestamp.toString()

        val notificationManager = ServiceUtil.getNotificationManager(context)
        for (notification in notificationManager.activeNotifications) {
            if (notification.id == SUMMARY_NOTIFICATION_ID && messageIdTag == notification.notification.extras.getString(LATEST_MESSAGE_ID_TAG)) {
                return
            }
        }

        val timestamp = notifications[0].timestamp
        if (timestamp != 0L) builder.setWhen(timestamp)

        builder.addActions(notificationState.getMarkAsReadIntent(context, SUMMARY_NOTIFICATION_ID))

        val iterator: ListIterator<NotificationItem> = notifications.listIterator(notifications.size)
        while (iterator.hasPrevious()) {
            val item = iterator.previous()
            builder.addMessageBody(
                item.individualRecipient, item.recipient,
                highlightMentions(
                    (if (item.text != null) item.text else "")!!,
                    false,
                    false,
                    true,  // no styling here, only text formatting
                    item.threadId,
                    context
                )
            )
        }

        if (signal) {
            builder.setAlarms(notificationState.getRingtone(context), notificationState.vibrate)
            val text = notifications[0].text
            builder.setTicker(
                notifications[0].individualRecipient,
                highlightMentions(
                    text ?: "",
                    false,
                    false,
                    true,  // no styling here, only text formatting
                    notifications[0].threadId,
                    context
                )
            )
        }

        builder.putStringExtra(LATEST_MESSAGE_ID_TAG, messageIdTag)

        // TODO - ACL to fix this properly & will do on 2024-08-26, but just skipping for now so review can start
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }

        val notification = builder.build()
        NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, notification)
        Log.i(TAG, "Posted notification. $notification")
    }

    private fun constructNotificationState(context: Context, cursor: Cursor): NotificationState {

        Log.i("ACL", "Hit construct notification state")

        val notificationState = NotificationState()
        val reader = get(context).mmsSmsDatabase().readerFor(cursor)
        if (reader == null) {
            Log.e(TAG, "No reader for cursor - aborting constructNotificationState")
            return NotificationState()
        }

        val threadDatabase = get(context).threadDatabase()
        val cache: MutableMap<Long, String?> = HashMap()

        // CAREFUL: Do not put this loop back as `while ((reader.next.also { record = it }) != null) {` because it breaks with a Null Pointer Exception!
        var record: MessageRecord? = null
        do {
            record = reader.next
            if (record == null) break // Bail if there are no more MessageRecords

            val id = record.getId()
            val mms = record.isMms || record.isMmsNotification
            val recipient = record.individualRecipient
            val conversationRecipient = record.recipient
            val threadId = record.threadId
            var body: CharSequence = record.getDisplayBody(context)
            var threadRecipients: Recipient? = null
            var slideDeck: SlideDeck? = null
            val timestamp = record.timestamp
            var notificationIsMessageRequest = false

            if (threadId == -1L) {
                Log.w(TAG, "Received a threadId of -1L in constructNotificationState - skipping invalid thread.")
                continue
            }

            val lastSentAndLastSeen = threadDatabase.getLastSeenAndHasSent(threadId)
            val lastSeen = lastSentAndLastSeen.first()
            val haveNotSentMessageToThisRecipient = !lastSentAndLastSeen.second() // CAREFUL: We negate this! If we HAVE sent a message to them it means we have implicitly accepted their message request

            threadRecipients = threadDatabase.getRecipientForThreadId(threadId)
            notificationIsMessageRequest = threadRecipients != null               &&
                                           threadRecipients.isIndividualRecipient &&
                                           threadRecipients.isNotApproved         &&
                                           haveNotSentMessageToThisRecipient

            // If this is a message request from an unknown user..
            if (notificationIsMessageRequest) {
                Log.i(TAG, "Detected incoming message request.")
                body = SpanUtil.italic(context.getString(R.string.messageRequestsNew))

            // If we received some manner of notification but Session is locked..
            } else if (KeyCachingService.isLocked(context)) {
                // Note: We provide 1 because `messageNewYouveGot` is now a plurals string and we don't have a count yet, so just giving
                // it 1 will result in "You got a new message" - which is the type of vague message we want went the device is locked.
                body = SpanUtil.italic(context.resources.getQuantityString(R.plurals.messageNewYouveGot, 1, 1))

            // ----- Note: All further cases assume we know the contact and that Session isn't locked -----

            // If this is a notification about a multimedia message from a contact we know about..
            } else if (record.isMms && !(record as MmsMessageRecord).sharedContacts.isEmpty()) {
                val contact = (record as MmsMessageRecord).sharedContacts[0]
                body = ContactUtil.getStringSummary(context, contact)

            // If this is a notification about a multimedia message which contains no text but DOES contain a slide deck with at least one slide..
            } else if (record.isMms && TextUtils.isEmpty(body) && !(record as MmsMessageRecord).slideDeck.slides.isEmpty()) {
                slideDeck = (record as MediaMmsMessageRecord).slideDeck
                body = SpanUtil.italic(slideDeck.body)

            // If this is a notification about a multimedia message, but it's not ITSELF a multimedia notification AND it contains a slide deck with at least one slide..
            } else if (record.isMms && !record.isMmsNotification && !(record as MmsMessageRecord).slideDeck.slides.isEmpty()) {
                slideDeck = (record as MediaMmsMessageRecord).slideDeck
                val message = slideDeck.body + ": " + record.body
                val italicLength = message.length - body.length
                body = SpanUtil.italic(message, italicLength)

            // If this is a notification about an invitation to a community..
            } else if (record.isOpenGroupInvitation) {
                body = SpanUtil.italic(context.getString(R.string.communityInvitation))
            }

            val userPublicKey = getLocalNumber(context)
            var blindedPublicKey = cache[threadId]
            if (blindedPublicKey == null) {
                blindedPublicKey = generateBlindedId(threadId, context)
                cache[threadId] = blindedPublicKey
            }

            if (threadRecipients == null || !threadRecipients.isMuted) {
                if (threadRecipients != null && threadRecipients.notifyType == RecipientDatabase.NOTIFY_TYPE_MENTIONS) {
                    // check if mentioned here
                    var isQuoteMentioned = false
                    if (record is MmsMessageRecord) {
                        val quote = (record as MmsMessageRecord).quote
                        val quoteAddress = quote?.author
                        val serializedAddress = quoteAddress?.serialize()
                        isQuoteMentioned = (serializedAddress != null && userPublicKey == serializedAddress) ||
                                (blindedPublicKey != null && userPublicKey == blindedPublicKey)
                    }
                    if (body.toString().contains("@$userPublicKey") || body.toString().contains("@$blindedPublicKey") || isQuoteMentioned) {
                        notificationState.addNotification(NotificationItem(id, mms, recipient, conversationRecipient, threadRecipients, threadId, body, timestamp, slideDeck))
                    }
                } else if (threadRecipients != null && threadRecipients.notifyType == RecipientDatabase.NOTIFY_TYPE_NONE) {
                    // do nothing, no notifications
                } else {
                    Log.i("ACL", "Adding to notification state!")



                    notificationState.addNotification(NotificationItem(id, mms, recipient, conversationRecipient, threadRecipients, threadId, body, timestamp, slideDeck))
                }

                val userBlindedPublicKey = blindedPublicKey
                val lastReact = Stream.of(record.reactions)
                    .filter { r: ReactionRecord -> !(r.author == userPublicKey || r.author == userBlindedPublicKey) }
                    .findLast()

                if (lastReact.isPresent) {
                    if (threadRecipients != null && !threadRecipients.isGroupOrCommunityRecipient) {
                        val reaction = lastReact.get()
                        val reactor = Recipient.from(context, fromSerialized(reaction.author), false)
                        val emoji = Phrase.from(context, R.string.emojiReactsNotification).put(EMOJI_KEY, reaction.emoji).format().toString()
                        notificationState.addNotification(NotificationItem(id, mms, reactor, reactor, threadRecipients, threadId, emoji, reaction.dateSent, slideDeck))
                    }
                }
            }
        } while (record != null) // This will never hit because we break early if we get a null record at the start of the do..while loop

        reader.close()
        return notificationState
    }

    private fun generateBlindedId(threadId: Long, context: Context): String? {
        val lokiThreadDatabase = get(context).lokiThreadDatabase()
        val openGroup = lokiThreadDatabase.getOpenGroupChat(threadId)
        val edKeyPair = getUserED25519KeyPair(context)
        if (openGroup != null && edKeyPair != null) {
            val blindedKeyPair = blindedKeyPair(openGroup.publicKey, edKeyPair)
            if (blindedKeyPair != null) {
                return AccountId(IdPrefix.BLINDED, blindedKeyPair.publicKey.asBytes).hexString
            }
        }
        return null
    }

    private fun updateBadge(context: Context, count: Int) {
        try {
            if (count == 0) ShortcutBadger.removeCount(context)
            else ShortcutBadger.applyCount(context, count)
        } catch (t: Throwable) {
            Log.w("MessageNotifier", t)
        }
    }

    private fun scheduleReminder(context: Context, count: Int) {
        if (count >= getRepeatAlertsCount(context)) {
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(ReminderReceiver.REMINDER_ACTION)
        alarmIntent.putExtra("reminder_count", count)

        val pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val timeout = TimeUnit.MINUTES.toMillis(2)

        alarmManager[AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + timeout] = pendingIntent
    }

    override fun clearReminder(context: Context) {
        val alarmIntent = Intent(ReminderReceiver.REMINDER_ACTION)
        val pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    class ReminderReceiver : BroadcastReceiver() {
        @SuppressLint("StaticFieldLeak")
        override fun onReceive(context: Context, intent: Intent) {
            object : AsyncTask<Void?, Void?, Void?>() {

                override fun doInBackground(vararg params: Void?): Void? {
                    val reminderCount = intent.getIntExtra("reminder_count", 0)
                    ApplicationContext.getInstance(context).messageNotifier.updateNotification(context, true, reminderCount + 1)
                    return null
                }

            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }

        companion object {
            const val REMINDER_ACTION: String = "network.loki.securesms.MessageNotifier.REMINDER_ACTION"
        }
    }

    // ACL: What is the concept behind delayed notifications? Why would we ever want this? To batch them up so
    // that we get a bunch of notifications once per minute or something rather than a constant stream of them
    // if that's what was incoming?!?
    private class DelayedNotification(private val context: Context, private val threadId: Long) : Runnable {
        private val canceled = AtomicBoolean(false)

        private val delayUntil: Long

        init {
            this.delayUntil = System.currentTimeMillis() + DELAY
        }

        override fun run() {
            val delayMillis = delayUntil - System.currentTimeMillis()
            Log.i(TAG, "Waiting to notify: $delayMillis")

            if (delayMillis > 0) { Util.sleep(delayMillis) }

            if (!canceled.get()) {
                Log.i(TAG, "Not canceled, notifying...")
                ApplicationContext.getInstance(context).messageNotifier.updateNotification(context, threadId, true)
                ApplicationContext.getInstance(context).messageNotifier.cancelDelayedNotifications()
            } else {
                Log.w(TAG, "Canceled, not notifying...")
            }
        }

        fun cancel() {
            canceled.set(true)
        }

        companion object {
            private val DELAY = TimeUnit.SECONDS.toMillis(5)
        }
    }

    private class CancelableExecutor {
        private val executor: Executor = Executors.newSingleThreadExecutor()
        private val tasks: MutableSet<DelayedNotification> = HashSet()

        fun execute(runnable: DelayedNotification) {
            synchronized(tasks) { tasks.add(runnable) }

            val wrapper = Runnable {
                runnable.run()
                synchronized(tasks) {
                    tasks.remove(runnable)
                }
            }

            executor.execute(wrapper)
        }

        fun cancel() {
            synchronized(tasks) {
                for (task in tasks) { task.cancel() }
            }
        }
    }

    companion object {
        private val TAG: String = DefaultMessageNotifier::class.java.simpleName

        const val EXTRA_REMOTE_REPLY: String = "extra_remote_reply"
        const val LATEST_MESSAGE_ID_TAG: String = "extra_latest_message_id"

        private const val FOREGROUND_ID = 313399
        private const val SUMMARY_NOTIFICATION_ID = 1338
        private const val PENDING_MESSAGES_ID = 1111
        private const val NOTIFICATION_GROUP = "messages"
        private val MIN_AUDIBLE_PERIOD_MILLIS = TimeUnit.SECONDS.toMillis(5)
        private val DESKTOP_ACTIVITY_PERIOD = TimeUnit.MINUTES.toMillis(1)

        @Volatile
        private var visibleThread: Long = -1

        @Volatile
        private var homeScreenVisible = false

        @Volatile
        private var lastDesktopActivityTimestamp: Long = -1

        @Volatile
        private var lastAudibleNotificationTimestamp: Long = -1
        private val executor = CancelableExecutor()
    }
}
