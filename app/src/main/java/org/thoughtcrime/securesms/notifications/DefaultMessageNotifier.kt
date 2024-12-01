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
import org.session.libsession.messaging.utilities.AccountId
import org.session.libsession.messaging.utilities.SodiumUtilities.blindedKeyPair
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.session.libsession.utilities.ServiceUtil
import org.session.libsession.utilities.StringSubstitutionConstants.EMOJI_KEY
import org.session.libsession.utilities.TextSecurePreferences.Companion.getLocalNumber
import org.session.libsession.utilities.TextSecurePreferences.Companion.getNotificationPrivacy
import org.session.libsession.utilities.TextSecurePreferences.Companion.getRepeatAlertsCount
import org.session.libsession.utilities.TextSecurePreferences.Companion.areMessageRequestsDisabled
import org.session.libsession.utilities.TextSecurePreferences.Companion.areMessageRequestsEnabled
import org.session.libsession.utilities.TextSecurePreferences.Companion.areNotificationsDisabled
import org.session.libsession.utilities.TextSecurePreferences.Companion.areNotificationsEnabled
import org.session.libsession.utilities.TextSecurePreferences.Companion.removeHasDisabledMessageRequests
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Util
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.contacts.ContactUtil
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionUtilities.highlightMentions
import org.thoughtcrime.securesms.crypto.KeyPairUtilities.getUserED25519KeyPair
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
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
        // We do not provide notifications for message delivery failure - but we still need this method in the
        // interface because messages that fail to send to trigger a "Failed" state which is displayed in a conversation.
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
                        notifications.cancel(notification.id)
                    }
                }
            }
        } catch (e: Throwable) {
            // XXX Android ROM Bug, see #6043
            Log.w(TAG, e)
        }
    }

    override fun resetAllNotificationsSilently(context: Context) {
        Log.i(TAG, "Hit resetAllNotificationsSilently")

        // Just bail if notifications are disabled..
        if (areNotificationsDisabled(context)) return else updateNotificationWithReminderCountAndOptionalAudio(context, playNotificationAudio = false, reminderCount = 0)
    }

    override fun updateNotificationForSpecificThread(context: Context, threadId: Long) {
        Log.i("ACL", "Hit updateNotificationForSpecificThread. Thread ID: $threadId")

        if (System.currentTimeMillis() - lastDesktopActivityTimestamp < DESKTOP_ACTIVITY_PERIOD) {
            Log.i(TAG, "Scheduling delayed notification...")
            executor.execute(DelayedNotification(context, threadId))
        } else {
            updateNotificationForSpecificThreadWithOptionalAudio(context, threadId, true)
        }
    }

    override fun updateNotificationForSpecificThreadWithOptionalAudio(
        context: Context,
        threadId: Long,
        playNotificationAudio: Boolean
    ) {
        Log.i("ACL", "Hit updateNotificationRegardingSpecificThreadWithOptionalAudio. Thread ID: $threadId, audio: $playNotificationAudio")

        val isVisible = visibleThread == threadId
        val threads = get(context).threadDatabase()
        val recipient = threads.getRecipientForThreadId(threadId)

        if (recipient != null && !recipient.isGroupRecipient && threads.getMessageCount(threadId) == 1 &&
            !(recipient.isApproved || threads.getLastSeenAndHasSent(threadId).second())
        ) {
            removeHasDisabledMessageRequests(context)
        }

        if (!areNotificationsEnabled(context) ||
            (recipient != null && recipient.isMuted)
        ) {
            return
        }

        if ((!isVisible && !homeScreenVisible) || hasExistingNotifications(context)) {
            updateNotificationWithReminderCountAndOptionalAudio(context, 0, playNotificationAudio)
        }
    }

    private fun hasExistingNotifications(context: Context): Boolean {
        Log.i("ACL", "Hit hasExistingNotifications")

        val notifications = ServiceUtil.getNotificationManager(context)
        try {
            val activeNotifications = notifications.activeNotifications
            return activeNotifications.isNotEmpty()
        } catch (e: Exception) {
            return false
        }
    }

    override fun updateNotificationWithReminderCountAndOptionalAudio(
        context: Context,
        reminderCount: Int,
        playNotificationAudio: Boolean
    ) {
        Log.i(TAG, "Hit updateNotificationWithReminderCountAndOptionalAudio. Audio: $playNotificationAudio, reminder count: $reminderCount")

        var telcoCursor: Cursor? = null

        try {
            telcoCursor = get(context).mmsSmsDatabase().unread // TODO: add a notification specific lighter query here

            val cannotAttemptNotification = telcoCursor == null || telcoCursor.isAfterLast || getLocalNumber(context) == null
            if (cannotAttemptNotification) {
                Log.w(TAG, "We lack the required details to attempt a notification - bailing.")

                // ACL - do not mess with any existing notifications!!!!
//                updateBadge(context, 0)
//                cancelActiveNotifications(context)
//                clearReminder(context)
                return
            }

            try {
                val notificationState = constructNotificationState(context, telcoCursor)

                // If we were asked to play audio with the notification but we have recently done so then we'll not play any audio
                // and then we'll flip our matable flag to false..
                val timeSinceLastNotificationAudioMS = System.currentTimeMillis() - lastAudibleNotification
                var shouldPlayNotificationAudio = if (playNotificationAudio &&
                    (timeSinceLastNotificationAudioMS < MIN_TIME_BETWEEN_NOTIFICATION_AUDIO_MS))
                {
                    false
                } else if (playNotificationAudio) {
                    // ..otherwise, if it's not too soon and we were asked to play audio we will, and we'll also update the timestamp that we last did so
                    lastAudibleNotification = System.currentTimeMillis()
                    true
                } else {
                    false // Not asked to play audio - no problem, we won't
                }

                if (notificationState.hasMultipleThreads()) {
                    for (threadId in notificationState.threads) {
                        val notificationStateForThread = NotificationState(notificationState.getNotificationsForThread(threadId))
                        sendSingleThreadNotification(
                            context,
                            notificationState = notificationStateForThread,
                            signalTheUser = false,
                            bundled = true)
                    }
                    sendMultipleThreadNotification(context, notificationState, shouldPlayNotificationAudio)
                } else if (notificationState.messageCount > 0) {
                    sendSingleThreadNotification(context,
                        notificationState = notificationState,
                        signalTheUser = shouldPlayNotificationAudio,
                        bundled = false)
                }

                cancelOrphanedNotifications(context, notificationState)
                updateBadge(context, notificationState.messageCount)

                if (playNotificationAudio) {
                    scheduleReminder(context, reminderCount)
                }
            }
            catch (e: Exception) {
                Log.e(TAG, "Error creating notification", e)
            }

        } finally {
            telcoCursor?.close()
        }
    }

    private fun sendSingleThreadNotification(
        context: Context,
        notificationState: NotificationState,
        signalTheUser: Boolean,
        bundled: Boolean
    ) {
        Log.i(TAG, "sendSingleThreadNotification()  signal: $signalTheUser  bundled: $bundled")

        if (notificationState.notifications.isEmpty()) {
            if (!bundled) cancelActiveNotifications(context)
            Log.i(TAG, "Empty notification state. Skipping.")
            return
        }

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
        builder.setMessageCount(notificationState.messageCount)

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
        builder.setOnlyAlertOnce(!signalTheUser)
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

        if (signalTheUser) {
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

    private fun sendMultipleThreadNotification(
        context: Context,
        notificationState: NotificationState,
        signalTheUser: Boolean // i.e., play audio
    ) {
        Log.i(TAG, "sendMultiThreadNotification()  signal: $signalTheUser")

        val builder = MultipleRecipientNotificationBuilder(context, getNotificationPrivacy(context))
        val notifications = notificationState.notifications

        builder.setMessageCount(notificationState.messageCount, notificationState.threadCount)
        builder.setMostRecentSender(notifications[0].individualRecipient, notifications[0].recipient)
        builder.setGroup(NOTIFICATION_GROUP)
        builder.setDeleteIntent(notificationState.getDeleteIntent(context))
        builder.setOnlyAlertOnce(!signalTheUser)
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

        if (signalTheUser) {
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

//    private fun constructNotificationState(context: Context, cursor: Cursor): NotificationState {
//        Log.i(TAG, "Hit constructNotificationState")
//
//        val notificationState = NotificationState()
//        val reader = get(context).mmsSmsDatabase().readerFor(cursor)
//        if (reader == null) {
//            Log.e(TAG, "No reader for cursor - aborting constructNotificationState")
//            return NotificationState()
//        }
//
//        val threadDatabase = get(context).threadDatabase()
//        val cache: MutableMap<Long, String?> = HashMap()
//
//        // CAREFUL: Do not put this loop back as `while ((reader.next.also { record = it }) != null) {`
//        // because it breaks with a Null Pointer Exception!
//        var record: MessageRecord? = null
//        do {
//            record = reader.next
//            if (record == null) break // Bail if there are no more MessageRecords
//
//            val id = record.getId()
//            val mms = record.isMms || record.isMmsNotification
//            val recipient = record.individualRecipient
//            val conversationRecipient = record.recipient
//            val threadId = record.threadId
//            var body: CharSequence = record.getDisplayBody(context)
//            var sender: Recipient? = null
//            var slideDeck: SlideDeck? = null
//            val timestamp = record.timestamp
//            var notificationIsAMessageRequest = false
//
//            if (threadId != -1L) {
//                sender = threadDatabase.getRecipientForThreadId(threadId)
//
//                if (sender == null) {
//                    Log.w(TAG, "Got a null sender for threadId: $threadId in constructNotificationState - skipping this message.")
//                    continue
//                }
//
//                Log.w("ACL", "Thread recipient is: ${sender.address}")
//
//                val lastSeenAndHasSent = threadDatabase.getLastSeenAndHasSent(threadId)
//                val lastSeen = lastSeenAndHasSent.first()
//                val hasSent = lastSeenAndHasSent.second()
//                Log.i("ACL", "Last seen is: $lastSeen, has sent is: $hasSent")
//
//                // We'll say that this notification is a message request if:
//                notificationIsAMessageRequest = sender.isIndividualRecipient && // It's from an individual (not a group)        AND
//                                                sender.isNotApproved         && // We haven't approved already approved them    AND
//                                                !threadDatabase.getLastSeenAndHasSent(threadId).second() // Has sent us a msg previously?
//
//                Log.i("ACL", "We think this is a message request?: $notificationIsAMessageRequest")
//
//                // If we already have more than one message or
//                val moreThanOneMsgOrMessageRequestsEnabled = threadDatabase.getMessageCount(threadId) > 0 || areMessageRequestsDisabled(context)
//                if (notificationIsAMessageRequest && moreThanOneMsgOrMessageRequestsEnabled)
//                {
//                    Log.i("ACL", "We There's more than one message in the thread or message requests are disabled so skipping...")
//                    continue
//                }
//            }
//
//            // If this is a message request from an unknown user..
//            if (notificationIsAMessageRequest) {
//                Log.i("ACL", "This is a message request from an unknown user")
//                body = SpanUtil.italic(context.getString(R.string.messageRequestsNew))
//
//            // If we received some manner of notification but Session is locked..
//            } else if (KeyCachingService.isLocked(context)) {
//                Log.i("ACL", "We'll do a notification because Session is locked")
//
//                // Note: We provide 1 because `messageNewYouveGot` is now a plurals string and we don't have a count yet, so just
//                // giving it 1 will result in "You got a new message".
//                body = SpanUtil.italic(context.resources.getQuantityString(R.plurals.messageNewYouveGot, 1, 1))
//
//            // ----- Note: All further cases assume we know the contact and that Session isn't locked -----
//
//            // If this is a notification about a multimedia message from a contact we know about..
//            } else if (record.isMms && !(record as MmsMessageRecord).sharedContacts.isEmpty()) {
//                val contact = (record as MmsMessageRecord).sharedContacts[0]
//                body = ContactUtil.getStringSummary(context, contact)
//
//            // If this is a notification about a multimedia message which contains no text but DOES contain a slide deck with at least one slide..
//            } else if (record.isMms && TextUtils.isEmpty(body) && !(record as MmsMessageRecord).slideDeck.slides.isEmpty()) {
//                slideDeck = (record as MediaMmsMessageRecord).slideDeck
//                body = SpanUtil.italic(slideDeck.body)
//
//            // If this is a notification about a multimedia message, but it's not ITSELF a multimedia notification AND it contains a slide deck with at least one slide..
//            } else if (record.isMms && !record.isMmsNotification && !(record as MmsMessageRecord).slideDeck.slides.isEmpty()) {
//                slideDeck = (record as MediaMmsMessageRecord).slideDeck
//                val message = slideDeck.body + ": " + record.body
//                val italicLength = message.length - body.length
//                body = SpanUtil.italic(message, italicLength)
//
//            // If this is a notification about an invitation to a community..
//            } else if (record.isOpenGroupInvitation) {
//                body = SpanUtil.italic(context.getString(R.string.communityInvitation))
//            }
//            else {
//                Log.i("ACL", "We have absolutely no idea what this notification is about")
//            }
//
//            val userPublicKey = getLocalNumber(context)
//            var blindedPublicKey = cache[threadId]
//            if (blindedPublicKey == null) {
//                blindedPublicKey = generateBlindedId(threadId, context)
//                cache[threadId] = blindedPublicKey
//            }
//
//            if (sender == null) continue
//
//            if (sender.isNotMuted) {
//
//                if (sender.notifyType == RecipientDatabase.NOTIFY_TYPE_MENTIONS) {
//                    // check if mentioned here
//                    var isQuoteMentioned = false
//                    if (record is MmsMessageRecord) {
//                        val quote = (record as MmsMessageRecord).quote
//                        val quoteAddress = quote?.author
//                        val serializedAddress = quoteAddress?.serialize()
//                        isQuoteMentioned = (serializedAddress != null && userPublicKey == serializedAddress) ||
//                                (blindedPublicKey != null && userPublicKey == blindedPublicKey)
//                    }
//                    if (body.toString().contains("@$userPublicKey") || body.toString().contains("@$blindedPublicKey") || isQuoteMentioned) {
//                        notificationState.addNotification(NotificationItem(id, mms, recipient, conversationRecipient, sender, threadId, body, timestamp, slideDeck))
//                    }
//                } else if (sender.notifyType == RecipientDatabase.NOTIFY_TYPE_NONE) {
//                    // do nothing, no notifications
//                } else {
//                    notificationState.addNotification(NotificationItem(id, mms, recipient, conversationRecipient, sender, threadId, body, timestamp, slideDeck))
//                }
//
//                val userBlindedPublicKey = blindedPublicKey
//                val lastReact = Stream.of(record.reactions)
//                    .filter { r: ReactionRecord -> !(r.author == userPublicKey || r.author == userBlindedPublicKey) }
//                    .findLast()
//
//                if (lastReact.isPresent) {
//                    if (sender.isIndividualRecipient) {
//                        val reaction = lastReact.get()
//                        val reactor = Recipient.from(context, fromSerialized(reaction.author), false)
//                        val emoji = Phrase.from(context, R.string.emojiReactsNotification).put(EMOJI_KEY, reaction.emoji).format().toString()
//                        notificationState.addNotification(NotificationItem(id, mms, reactor, reactor, sender, threadId, emoji, reaction.dateSent, slideDeck))
//                    }
//                }
//            }
//        } while (record != null) // This will never hit because we break early if we get a null record at the start of the do..while loop
//
//        reader.close()
//        return notificationState
//    }

    private fun constructNotificationState(context: Context, cursor: Cursor): NotificationState {
        Log.i(TAG, "Hit constructNotificationState")

        val notificationState = NotificationState()
        val reader = get(context).mmsSmsDatabase().readerFor(cursor)
        if (reader == null) {
            Log.e(TAG, "No reader for cursor - aborting constructNotificationState")
            return notificationState
        }

        val threadDatabase = get(context).threadDatabase()

        // We put into this or get from it ad we process records
        val blindedPublicKeyCache = mutableMapOf<Long, String?>()

        while (true) {
            val record = reader.next ?: break
            val notificationItem = processRecord(context, record, threadDatabase, blindedPublicKeyCache)
            if (notificationItem != null) {
                Log.i("ACL", "Adding notification at: ${notificationItem.timestamp}")
                Log.i("ACL", "Individual recipient is: ${notificationItem.individualRecipient.address}")
                Log.i("ACL", "Recipient is: ${notificationItem.recipient.address}")
                notificationState.addNotification(notificationItem)
            }
        }

        reader.close()
        return notificationState
    }

    private fun processRecord(
        context: Context,
        record: MessageRecord,
        threadDatabase: ThreadDatabase,
        blindedPublicKeyCache: MutableMap<Long, String?>
    ): NotificationItem? {
        val messageId = record.getId()
        val mms = record.isMms || record.isMmsNotification
        val recipient = record.individualRecipient
        val conversationRecipient = record.recipient
        val threadId = record.threadId
        var body = record.getDisplayBody(context)
        val timestamp = record.timestamp
        var slideDeck: SlideDeck? = null

        // Couldn't get sender? Bail
        val sender = getSender(threadDatabase, threadId)
        if (sender == null) {
            Log.w(TAG, "Could not get sender for threadId: $threadId - cannot process for notification.")
            return null
        }

        // Sender is muted? Bail
        if (sender.isMuted) {
            Log.i(TAG, "Skipping notification because sender is muted")
            return null
        }

        // If this is a message request, and message requests have been disabled by the user then bail
        val notificationIsAMessageRequest = isMessageRequest(sender, threadDatabase, threadId)
        val messageRequestsAreDisabled    = areMessageRequestsDisabled(context)
        if (notificationIsAMessageRequest && messageRequestsAreDisabled) {
            Log.i(TAG, "Skipping notification because message requests are disabled")
            return null
        }

        val messagesCountFromSender = threadDatabase.getMessageCount(threadId)
        Log.i("ACL", "The number of messages we have from this sender is: $messagesCountFromSender")
        if (messagesCountFromSender > 0) {
            Log.i("ACL", "We already have a message from this sender - skipping notification") // The debouncing is broken - so we'll hit this when we return here even for a single notification!
            return null
        }

        body = prepareNotificationBody(context, record, body, notificationIsAMessageRequest)
        slideDeck = getSlideDeck(record)

        val userPublicKey = getLocalNumber(context)!!
        val blindedPublicKey = blindedPublicKeyCache.getOrPut(threadId) { generateBlindedId(threadId, context) }

        // If the notification type is NOTIFY_TYPE_ALL or NOTIFY_TYPE_MENTIONS and specifically mentions the user then send the notification
        if (shouldNotify(sender, record, userPublicKey, blindedPublicKey, body)) {
            return NotificationItem(
                messageId, mms, recipient, conversationRecipient, sender,
                threadId, body, timestamp, slideDeck
            )
        } else {
            // If the notification type is NOTIFY_TYPE_NONE then check if it's a notification regarding a reaction to a message, such as
            // someone giving a "Thumbs up" to something you've posted to them.
            val reactionNotification = getReactionNotification(
                context, record, sender, messageId, mms, recipient,
                conversationRecipient, threadId, slideDeck
            )
            if (reactionNotification != null) {
                return reactionNotification
            }
        }
        // If we've exhausted our should we / shouldn't we criteria we don't provide a notification
        Log.w("ACL", "Exhausted our to-notify-or-not-notify criteria (shouldn't happen?) - not notifying.")
        return null
    }

    private fun getSender(
        threadDatabase: ThreadDatabase,
        threadId: Long
    ): Recipient? {
        if (threadId == -1L) return null
        val sender = threadDatabase.getRecipientForThreadId(threadId)
        if (sender == null) {
            Log.w(TAG, "Got a null sender for threadId: $threadId - skipping this message.")
        }
        return sender
    }

    private fun isMessageRequest(
        sender: Recipient,
        threadDatabase: ThreadDatabase,
        threadId: Long
    ): Boolean {
        val lastSeenAndHasSent = threadDatabase.getLastSeenAndHasSent(threadId)
        val lastSeen = lastSeenAndHasSent.first()
        val hasSent = lastSeenAndHasSent.second()
        val isMessageRequest = sender.isIndividualRecipient &&
                sender.isNotApproved &&
                !hasSent
        Log.i(TAG, "In isMessageRequest - lastSeen is: $lastSeen, hasSent is: $hasSent, message request status: $isMessageRequest")
        return isMessageRequest
    }

    private fun prepareNotificationBody(
        context: Context,
        record: MessageRecord,
        body: CharSequence,
        notificationIsAMessageRequest: Boolean
    ): CharSequence {
        return when {
            notificationIsAMessageRequest -> {
                Log.i(TAG, "Preparing message request body.")
                SpanUtil.italic(context.getString(R.string.messageRequestsNew))
            }
            KeyCachingService.isLocked(context) -> {
                Log.i(TAG, "Session is locked; preparing locked session body.")
                SpanUtil.italic(context.resources.getQuantityString(R.plurals.messageNewYouveGot, 1, 1))
            }
            record is MmsMessageRecord && record.sharedContacts.isNotEmpty() -> {
                val contact = record.sharedContacts[0]
                ContactUtil.getStringSummary(context, contact)
            }
            record is MmsMessageRecord && body.isEmpty() && record.slideDeck.slides.isNotEmpty() -> {
                val slideDeck = record.slideDeck
                SpanUtil.italic(slideDeck.body)
            }
            record is MmsMessageRecord && !record.isMmsNotification && record.slideDeck.slides.isNotEmpty() -> {
                val slideDeck = record.slideDeck
                val message = "${slideDeck.body}: ${record.body}"
                val italicLength = message.length - body.length
                SpanUtil.italic(message, italicLength)
            }
            record.isOpenGroupInvitation -> {
                SpanUtil.italic(context.getString(R.string.communityInvitation))
            }
            else -> {
                Log.i(TAG, "Default notification body used.")
                body
            }
        }
    }

    private fun getSlideDeck(record: MessageRecord): SlideDeck? {
        return if (record is MediaMmsMessageRecord && record.slideDeck.slides.isNotEmpty()) {
            record.slideDeck
        } else {
            null
        }
    }

    private fun shouldNotify(
        sender: Recipient,
        record: MessageRecord,
        userPublicKey: String,
        blindedPublicKey: String?,
        bodyText: CharSequence
    ): Boolean {
        return when (sender.notifyType) {
            RecipientDatabase.NOTIFY_TYPE_MENTIONS -> {
                isUserMentioned(record, userPublicKey, blindedPublicKey, bodyText)
            }
            RecipientDatabase.NOTIFY_TYPE_NONE -> {
                false
            }
            else -> {
                true // For NOTIFY_TYPE_ALL, which is the only remaining notification type
            }
        }
    }

    private fun isUserMentioned(
        record: MessageRecord,
        userPublicKey: String,
        blindedPublicKey: String?,
        bodyText: CharSequence
    ): Boolean {
        val isMentionedInBody = bodyText.contains("@$userPublicKey") ||
                (blindedPublicKey != null && bodyText.contains("@$blindedPublicKey"))

        val isMentionedInQuote = if (record is MmsMessageRecord) {
            val quoteAuthor = record.quote?.author?.serialize()
            quoteAuthor == userPublicKey || quoteAuthor == blindedPublicKey
        } else {
            false
        }

        return isMentionedInBody || isMentionedInQuote
    }

    private fun getReactionNotification(
        context: Context,
        record: MessageRecord,
        sender: Recipient,
        id: Long,
        mms: Boolean,
        recipient: Recipient,
        conversationRecipient: Recipient,
        threadId: Long,
        slideDeck: SlideDeck?
    ): NotificationItem? {
        val userPublicKey = getLocalNumber(context)
        val reactions = record.reactions.filterNot { it.author == userPublicKey }
        val lastReaction = reactions.lastOrNull() ?: return null

        if (sender.isIndividualRecipient) {
            val reactor = Recipient.from(context, fromSerialized(lastReaction.author), false)
            val emoji = Phrase.from(context, R.string.emojiReactsNotification)
                .put(EMOJI_KEY, lastReaction.emoji)
                .format()
                .toString()
            return NotificationItem(
                id, mms, reactor, reactor, sender,
                threadId, emoji, lastReaction.dateSent, slideDeck
            )
        }
        return null
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
        Log.i(TAG, "Hit updateBadge with count: " + count)

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
                    val appContext = ApplicationContext.getInstance(context)
                    appContext.messageNotifier.updateNotificationWithReminderCountAndOptionalAudio(context, reminderCount + 1, true)
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
                val appContext = ApplicationContext.getInstance(context)
                appContext.messageNotifier.updateNotificationForSpecificThreadWithOptionalAudio(context, threadId, playNotificationAudio = true)
                appContext.messageNotifier.cancelDelayedNotifications()
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
        private val MIN_TIME_BETWEEN_NOTIFICATION_AUDIO_MS = TimeUnit.SECONDS.toMillis(5)
        private val DESKTOP_ACTIVITY_PERIOD = TimeUnit.MINUTES.toMillis(1)

        @Volatile
        private var visibleThread: Long = -1

        @Volatile
        private var homeScreenVisible = false

        @Volatile
        private var lastDesktopActivityTimestamp: Long = -1

        @Volatile
        private var lastAudibleNotification: Long = -1
        private val executor = CancelableExecutor()
    }
}
