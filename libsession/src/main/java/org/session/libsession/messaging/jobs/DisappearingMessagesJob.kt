package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.utilities.Data

class DisappearingMessagesJob(
    val messageIds: List<Long> = listOf(),
    val startedAtMs: Long = 0,
    val threadId: Long = 0
) : Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 1

    override fun execute() {
        if (!ExpirationConfiguration.isNewConfigEnabled) return
        try {
            val ids = MessagingModuleConfiguration.shared.storage.getExpiringMessages(messageIds).map { it.first }
            if (ids.isNotEmpty()) {
                JobQueue.shared.add(SyncedExpiriesJob(ids, startedAtMs, threadId))
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

    class Factory : Job.Factory<DisappearingMessagesJob> {
        override fun create(data: Data): DisappearingMessagesJob {
            return DisappearingMessagesJob(
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