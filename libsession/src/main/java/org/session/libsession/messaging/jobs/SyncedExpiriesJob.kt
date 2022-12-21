package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.messages.control.SyncedExpiriesMessage
import org.session.libsession.messaging.messages.control.SyncedExpiry
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address

class SyncedExpiriesJob(
    val messageIds: List<Long> = emptyList(),
    val startedAtMs: Long = 0,
    val threadId: Long = 0
) : Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 1

    override fun execute() {
        if (!ExpirationConfiguration.isNewConfigEnabled) return
        val module = MessagingModuleConfiguration.shared
        val userPublicKey = module.storage.getUserPublicKey() ?: return
        try {
            val messageIdsWithNoServerHashByExpiresIn = mutableMapOf<Long, List<Long>>()
            module.storage.getExpiringMessages(messageIds).groupBy { it.second }.forEach { (expiresInSeconds, messageIds) ->
                val serverHashesByMessageIds = module.messageDataProvider.getServerHashForMessages(messageIds.map { it.first })
                val messageIdsWithNoHash = serverHashesByMessageIds.filter { it.second == null }.map { it.first }
                if (messageIdsWithNoHash.isNotEmpty()) {
                    messageIdsWithNoServerHashByExpiresIn[expiresInSeconds] = messageIdsWithNoHash
                }
                val serverHashes = serverHashesByMessageIds.mapNotNull { it.second }
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
            if (messageIdsWithNoServerHashByExpiresIn.isNotEmpty()) {
                JobQueue.shared.add(
                    SyncedExpiriesJob(messageIdsWithNoServerHashByExpiresIn.flatMap { it.value }, startedAtMs, threadId)
                )
            }
        } catch (e: Exception) {
            delegate?.handleJobFailed(this, e)
            return
        }
        delegate?.handleJobSucceeded(this)
    }

    override fun serialize(): Data = Data.Builder()
        .putLongArray(MESSAGE_IDS, messageIds.toLongArray())
        .putLong(STARTED_AT_MS, startedAtMs)
        .putLong(THREAD_ID, threadId)
        .build()

    override fun getFactoryKey(): String = KEY

    class Factory : Job.Factory<SyncedExpiriesJob> {
        override fun create(data: Data): SyncedExpiriesJob {
            return SyncedExpiriesJob(
                data.getLongArray(MESSAGE_IDS).toList(),
                data.getLong(STARTED_AT_MS),
                data.getLong(THREAD_ID)
            )
        }
    }

    companion object {
        const val KEY = "DisappearingMessagesJob"

        private const val MESSAGE_IDS = "messageIds"
        private const val STARTED_AT_MS = "startedAtMs"
        private const val THREAD_ID = "threadId"
    }

}