package org.thoughtcrime.securesms.notifications

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nl.komponents.kovenant.task
import org.session.libsession.messaging.MessagingModuleConfiguration.Companion.shared
import org.session.libsession.messaging.messages.control.ReadReceipt
import org.session.libsession.messaging.sending_receiving.MessageSender.send
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeAPI.nowWithOffset
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.isReadReceiptsEnabled
import org.session.libsession.utilities.associateByNotNull
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.conversation.disappearingmessages.ExpiryType
import org.thoughtcrime.securesms.database.MessagingDatabase.ExpirationInfo
import org.thoughtcrime.securesms.database.MessagingDatabase.MarkedMessageInfo
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.util.SessionMetaProtocol.shouldSendReadReceipt

class MarkReadReceiver : BroadcastReceiver() {
    @SuppressLint("StaticFieldLeak")
    override fun onReceive(context: Context, intent: Intent) {
        if (CLEAR_ACTION != intent.action) return
        val threadIds = intent.getLongArrayExtra(THREAD_IDS_EXTRA)
        if (threadIds != null) {
            NotificationManagerCompat.from(context)
                .cancel(intent.getIntExtra(NOTIFICATION_ID_EXTRA, -1))
            object : AsyncTask<Void?, Void?, Void?>() {
                override fun doInBackground(vararg params: Void?): Void? {
                    val currentTime = nowWithOffset
                    for (threadId in threadIds) {
                        Log.i(TAG, "Marking as read: $threadId")
                        val storage = shared.storage
                        storage.markConversationAsRead(threadId, currentTime, true)
                    }
                    return null
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
    }

    companion object {
        private val TAG = MarkReadReceiver::class.java.simpleName
        const val CLEAR_ACTION = "network.loki.securesms.notifications.CLEAR"
        const val THREAD_IDS_EXTRA = "thread_ids"
        const val NOTIFICATION_ID_EXTRA = "notification_id"

        @JvmStatic
        fun process(
            context: Context,
            markedReadMessages: List<MarkedMessageInfo>
        ) {
            if (markedReadMessages.isEmpty()) return

            sendReadReceipts(context, markedReadMessages)

            markedReadMessages.forEach { scheduleDeletion(context, it.expirationInfo) }

            getHashToMessage(context, markedReadMessages)?.let {
                fetchUpdatedExpiriesAndScheduleDeletion(context, it)
                shortenExpiryOfDisappearingAfterRead(context, it)
            }
        }

        private fun getHashToMessage(
            context: Context,
            markedReadMessages: List<MarkedMessageInfo>
        ): Map<String, MarkedMessageInfo>? {
            val loki = DatabaseComponent.get(context).lokiMessageDatabase()

            return markedReadMessages
                .associateByNotNull { it.expirationInfo.run { loki.getMessageServerHash(id, isMms) } }
                .takeIf { it.isNotEmpty() }
        }

        private fun shortenExpiryOfDisappearingAfterRead(
            context: Context,
            hashToMessage: Map<String, MarkedMessageInfo>
        ) {
            hashToMessage.filterValues { it.guessExpiryType() == ExpiryType.AFTER_READ }
                .entries
                .groupBy(
                    keySelector =  { it.value.expirationInfo.expiresIn },
                    valueTransform = { it.key }
                ).forEach { (expiresIn, hashes) ->
                    SnodeAPI.alterTtl(
                        messageHashes = hashes,
                        newExpiry = nowWithOffset + expiresIn,
                        publicKey = TextSecurePreferences.getLocalNumber(context)!!,
                        shorten = true
                    )
                }
        }

        private fun sendReadReceipts(
            context: Context,
            markedReadMessages: List<MarkedMessageInfo>
        ) {
            if (isReadReceiptsEnabled(context)) {
                markedReadMessages.map { it.syncMessageId }
                    .filter { shouldSendReadReceipt(Recipient.from(context, it.address, false)) }
                    .groupBy { it.address }
                    .forEach { (address, messages) ->
                        messages.map { it.timetamp }
                            .let(::ReadReceipt)
                            .apply { sentTimestamp = nowWithOffset }
                            .let { send(it, address) }
                    }
            }
        }

        private fun fetchUpdatedExpiriesAndScheduleDeletion(
            context: Context,
            hashToMessage: Map<String, MarkedMessageInfo>
        ) {
            @Suppress("UNCHECKED_CAST")
            val expiries = SnodeAPI.getExpiries(hashToMessage.keys.toList(), TextSecurePreferences.getLocalNumber(context)!!).get()["expiries"] as Map<String, Long>
            hashToMessage.forEach { (hash, info) -> expiries[hash]?.let { scheduleDeletion(context, info.expirationInfo, it - info.expirationInfo.expireStarted) } }
        }

        private fun scheduleDeletion(
            context: Context?,
            expirationInfo: ExpirationInfo,
            expiresIn: Long = expirationInfo.expiresIn
        ) {
            android.util.Log.d(TAG, "scheduleDeletion() called with: expirationInfo = $expirationInfo, expiresIn = $expiresIn")

            if (expiresIn > 0 && expirationInfo.expireStarted <= 0) {
                if (expirationInfo.isMms) DatabaseComponent.get(context!!).mmsDatabase().markExpireStarted(expirationInfo.id)
                else DatabaseComponent.get(context!!).smsDatabase().markExpireStarted(expirationInfo.id)

                ApplicationContext.getInstance(context).expiringMessageManager.scheduleDeletion(
                    expirationInfo.id,
                    expirationInfo.isMms,
                    expiresIn
                )
            }
        }
    }
}
