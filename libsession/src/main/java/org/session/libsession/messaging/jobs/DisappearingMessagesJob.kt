package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.control.SyncedExpiriesMessage
import org.session.libsession.messaging.messages.control.SyncedExpiry
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address

class DisappearingMessagesJob(val messageIds: LongArray, val startedAtMs: Long): Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 1

    override fun execute() {
        val userPublicKey = MessagingModuleConfiguration.shared.storage.getUserPublicKey() ?: return
        val module = MessagingModuleConfiguration.shared
        try {
            module.storage.getExpiringMessages(messageIds).groupBy { it.second }.forEach { (expiresInSeconds, messages) ->
                val serverHashes = messages.map { it.first }
                if (serverHashes.isEmpty()) return
                val expirationTimestamp = startedAtMs + expiresInSeconds * 1000
                val syncTarget = ""
                val syncedExpiriesMessage = SyncedExpiriesMessage()
                syncedExpiriesMessage.conversationExpiries = mapOf(
                    syncTarget to serverHashes.map { serverHash -> SyncedExpiry(serverHash, expirationTimestamp) }
                )
                MessageSender.send(syncedExpiriesMessage, Address.fromSerialized(userPublicKey))
                SnodeAPI.updateExpiry(expirationTimestamp, serverHashes)
            }
        } catch (e: Exception) {
            delegate?.handleJobFailed(this, e)
            return
        }
        delegate?.handleJobSucceeded(this)
    }

    override fun serialize(): Data = Data.Builder()
        .putLongArray(MESSAGE_IDS, messageIds)
        .putLong(STARTED_AT_MS, startedAtMs)
        .build()

    override fun getFactoryKey(): String = KEY

    class Factory : Job.Factory<DisappearingMessagesJob> {
        override fun create(data: Data): DisappearingMessagesJob {
            return DisappearingMessagesJob(
                data.getLongArray(MESSAGE_IDS),
                data.getLong(STARTED_AT_MS)
            )
        }
    }

    companion object {
        const val KEY = "DisappearingMessagesJob"

        private const val MESSAGE_IDS = "messageIds"
        private const val STARTED_AT_MS = "startedAtMs"
    }

}