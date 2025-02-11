package org.session.libsession.messaging

interface LastSentMessageIdCache {
    fun getLastSentMessageId(threadId: Long): Long?
    fun submitMessageId(threadId: Long, messageId: Long)
    fun delete(threadId: Long, messageIds: List<Long>)
    fun delete(threadId: Long, messageId: Long) = delete(threadId, listOf(messageId))
    fun refreshFromDatabase(threadId: Long)
}
