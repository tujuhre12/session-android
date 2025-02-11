package org.thoughtcrime.securesms.database

import org.session.libsession.messaging.LastSentMessageIdCache
import javax.inject.Inject
import javax.inject.Singleton

// Note: This class was previously a `LastSendMessageTimestampCache` used to determine the last sent
// message without hitting the database too often. However, as messages to blinded recipients result
// in a rounded-down timestamp we could miss messages - so this has been adjusted to work on the
// last sent message ID instead. This way we don't require exact timestamp messages, and simply
// rebuild the cache should messages be deleted (as happened with timestamps) to ensure we can
// always accurately identify the last sent message.

@Singleton
class LastSentMessageIdCache @Inject constructor(
    val mmsSmsDatabase: MmsSmsDatabase
): LastSentMessageIdCache {

    // Map of thread Id to the Id of the last sent message
    private val map = mutableMapOf<Long, Long>()

    @Synchronized
    override fun getLastSentMessageId(threadId: Long): Long? = map[threadId]

    @Synchronized
    override fun submitMessageId(threadId: Long, messageId: Long) {
        map[threadId] = messageId
    }

    @Synchronized
    override fun delete(threadId: Long, messageIds: List<Long>) {
        map.remove(threadId)
        refreshFromDatabase(threadId)
    }

    @Synchronized
    override fun refreshFromDatabase(threadId: Long) {
        map[threadId] = mmsSmsDatabase.getLastOutgoingMessageId(threadId)
    }
}
