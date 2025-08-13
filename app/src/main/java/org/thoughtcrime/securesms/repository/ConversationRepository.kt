package org.thoughtcrime.securesms.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import network.loki.messenger.libsession_util.ReadableUserProfile
import network.loki.messenger.libsession_util.util.BlindedContact
import network.loki.messenger.libsession_util.util.Contact
import network.loki.messenger.libsession_util.util.ExpiryMode
import network.loki.messenger.libsession_util.util.GroupInfo
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.database.userAuth
import org.session.libsession.messaging.groups.GroupManagerV2
import org.session.libsession.messaging.messages.MarkAsDeletedMessage
import org.session.libsession.messaging.messages.control.MessageRequestResponse
import org.session.libsession.messaging.messages.control.UnsendRequest
import org.session.libsession.messaging.messages.signal.OutgoingTextMessage
import org.session.libsession.messaging.messages.visible.OpenGroupInvitation
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeClock
import org.session.libsession.snode.utilities.await
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.isGroupV2
import org.session.libsession.utilities.isLegacyGroup
import org.session.libsession.utilities.isStandard
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.upsertContact
import org.session.libsession.utilities.userConfigsChanged
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.database.DraftDatabase
import org.thoughtcrime.securesms.database.LokiMessageDatabase
import org.thoughtcrime.securesms.database.LokiThreadDatabase
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.RecipientRepository
import org.thoughtcrime.securesms.database.RecipientSettingsDatabase
import org.thoughtcrime.securesms.database.SmsDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import javax.inject.Inject
import javax.inject.Singleton

interface ConversationRepository {
    fun observeConversationList(approved: Boolean? = null): Flow<List<ThreadRecord>>

    fun getConversationList(approved: Boolean? = null): List<ThreadRecord>

    fun getConversationListAddresses(approved: Boolean? = null): List<Address.Conversable>

    fun getConversationListAddresses(
        nts: (ReadableUserProfile) -> Boolean = { false },
        contactFilter: (Contact) -> Boolean = { false },
        blindedContactFilter: (BlindedContact) -> Boolean = { false },
        groupFilter: (GroupInfo.ClosedGroupInfo) -> Boolean = { false },
        legacyFilter: (GroupInfo.LegacyGroupInfo) -> Boolean = { false },
        communityFilter: (GroupInfo.CommunityGroupInfo) -> Boolean = { false }
    ): List<Address.Conversable>

    fun maybeGetRecipientForThreadId(threadId: Long): Address?
    fun saveDraft(threadId: Long, text: String)
    fun getDraft(threadId: Long): String?
    fun clearDrafts(threadId: Long)
    fun inviteContactsToCommunity(threadId: Long, contacts: Collection<Address>)
    fun setBlocked(recipient: Address, blocked: Boolean)
    fun markAsDeletedLocally(messages: Set<MessageRecord>, displayedMessage: String)
    fun deleteMessages(messages: Set<MessageRecord>, threadId: Long)
    fun deleteAllLocalMessagesInThreadFromSenderOfMessage(messageRecord: MessageRecord)
    fun isGroupReadOnly(recipient: Recipient): Boolean
    fun getLastSentMessageID(threadId: Long): Flow<MessageId?>

    suspend fun deleteCommunityMessagesRemotely(threadId: Long, messages: Set<MessageRecord>)
    suspend fun delete1on1MessagesRemotely(
        threadId: Long,
        recipient: Address,
        messages: Set<MessageRecord>
    )
    suspend fun deleteNoteToSelfMessagesRemotely(
        threadId: Long,
        recipient: Address,
        messages: Set<MessageRecord>
    )
    suspend fun deleteLegacyGroupMessagesRemotely(
        recipient: Address,
        messages: Set<MessageRecord>
    )

    suspend fun deleteGroupV2MessagesRemotely(recipient: Address, messages: Set<MessageRecord>)

    suspend fun banUser(threadId: Long, recipient: Address): Result<Unit>
    suspend fun banAndDeleteAll(threadId: Long, recipient: Address): Result<Unit>
    suspend fun deleteMessageRequest(thread: ThreadRecord): Result<Unit>
    suspend fun clearAllMessageRequests(block: Boolean): Result<Unit>
    suspend fun acceptMessageRequest(threadId: Long, recipient: Address.Conversable): Result<Unit>
    suspend fun declineMessageRequest(recipient: Address.Conversable): Result<Unit>
    fun hasReceived(threadId: Long): Boolean
    fun getInvitingAdmin(threadId: Long): Address?

    /**
     * This will delete all messages from the database.
     * If a groupId is passed along, and if the user is an admin of that group,
     * this will also remove the messages from the swarm and update
     * the delete_before flag for that group to now
     *
     * Returns the amount of deleted messages
     */
    suspend fun clearAllMessages(threadId: Long, groupId: AccountId?): Int
}

@Singleton
class DefaultConversationRepository @Inject constructor(
    private val textSecurePreferences: TextSecurePreferences,
    private val messageDataProvider: MessageDataProvider,
    private val threadDb: ThreadDatabase,
    private val draftDb: DraftDatabase,
    private val lokiThreadDb: LokiThreadDatabase,
    private val smsDb: SmsDatabase,
    private val mmsSmsDb: MmsSmsDatabase,
    private val storage: Storage,
    private val lokiMessageDb: LokiMessageDatabase,
    private val configFactory: ConfigFactory,
    private val groupManager: GroupManagerV2,
    private val clock: SnodeClock,
    private val preferences: TextSecurePreferences,
    private val recipientDatabase: RecipientSettingsDatabase,
    private val recipientRepository: RecipientRepository,
) : ConversationRepository {

    override fun getConversationListAddresses(
        nts: (ReadableUserProfile) -> Boolean,
        contactFilter: (Contact) -> Boolean,
        blindedContactFilter: (BlindedContact) -> Boolean,
        groupFilter: (GroupInfo.ClosedGroupInfo) -> Boolean,
        legacyFilter: (GroupInfo.LegacyGroupInfo) -> Boolean,
        communityFilter: (GroupInfo.CommunityGroupInfo) -> Boolean,
    ): List<Address.Conversable> {

        val (shouldHaveNts, dataSeq) = configFactory.withUserConfigs { configs ->
            (configs.userProfile.getNtsPriority() >= 0 && nts(configs.userProfile)) to
                    (configs.contacts.all().asSequence() +
                            configs.contacts.allBlinded().asSequence() +
                            configs.userGroups.all().asSequence())
        }

        val localNumber = preferences.getLocalNumber()

        val ntsSequence: Sequence<Address.Conversable> = sequenceOf(
            localNumber
                ?.takeIf { shouldHaveNts }
                ?.let(::AccountId)
                ?.let(Address::Standard))
            .filterNotNull()

        return (ntsSequence + dataSeq
            .mapNotNull { data ->
                when (data) {
                    is Contact -> if (data.priority >= 0 && contactFilter(data)) {
                        Address.Standard(AccountId(data.id))
                    } else {
                        null
                    }

                    is BlindedContact -> if (blindedContactFilter(data)) {
                        Address.CommunityBlindedId(
                            serverUrl = data.communityServer,
                            serverPubKey = data.communityServerPubKeyHex,
                            blindedId = Address.Blinded(AccountId(data.id))
                        )
                    } else {
                        null
                    }

                    is GroupInfo.ClosedGroupInfo -> if (data.priority >= 0 && groupFilter(data)) {
                        Address.Group(AccountId(data.groupAccountId))
                    } else {
                        null
                    }

                    is GroupInfo.LegacyGroupInfo -> if (data.priority >= 0 && legacyFilter(data)) {
                        Address.LegacyGroup(data.accountId)
                    } else {
                        null
                    }

                    is GroupInfo.CommunityGroupInfo -> if (communityFilter(data)) {
                        Address.Community(
                            serverUrl = data.community.baseUrl,
                            room = data.community.room
                        )
                    } else {
                        null
                    }

                    else -> error("Unknown data type: $data")
                }
            })
            .toList()
    }

    private val GroupInfo.ClosedGroupInfo.approved: Boolean
        get() = !invited


    override fun getConversationListAddresses(approved: Boolean?) = getConversationListAddresses(
        blindedContactFilter = { true },
        nts = { approved == null || approved },
        contactFilter = {
            !it.blocked && (approved == null || it.approved == approved)
        },
        groupFilter = {
            approved == null || it.approved == approved
        },
        legacyFilter = { approved != false }, // Legacy groups are always approved
        communityFilter = { approved != false } // Communities are always approved
    )

    private fun List<ThreadRecord>.filterThreadsForApprovalStatus(): List<ThreadRecord> {
        return this.filter { record ->
            // We don't actually want to show unapproved threads without any messages.
            record.recipient.approved || record.lastMessage != null
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun observeConversationList(approved: Boolean?): Flow<List<ThreadRecord>> {
        return configFactory.userConfigsChanged(200)
            .onStart { emit(Unit) }
            .map { getConversationListAddresses(approved) }
            .distinctUntilChanged()
            .flatMapLatest { allAddresses ->
                merge(
                    configFactory.configUpdateNotifications,
                    recipientDatabase.changeNotification,
                    threadDb.updateNotifications
                ).debounce(500)
                    .onStart { emit(Unit) }
                    .mapLatest {
                        withContext(Dispatchers.Default) {
                            threadDb.getThreads(allAddresses).filterThreadsForApprovalStatus()
                        }
                    }
            }
    }

    override fun getConversationList(approved: Boolean?): List<ThreadRecord> {
        return threadDb.getThreads(getConversationListAddresses(approved))
            .filterThreadsForApprovalStatus()
    }

    override fun maybeGetRecipientForThreadId(threadId: Long): Address? {
        return threadDb.getRecipientForThreadId(threadId)
    }

    override fun saveDraft(threadId: Long, text: String) {
        if (text.isEmpty()) return
        val drafts = DraftDatabase.Drafts()
        drafts.add(DraftDatabase.Draft(DraftDatabase.Draft.TEXT, text))
        draftDb.insertDrafts(threadId, drafts)
    }

    override fun getDraft(threadId: Long): String? {
        val drafts = draftDb.getDrafts(threadId)
        return drafts.find { it.type == DraftDatabase.Draft.TEXT }?.value
    }

    override fun clearDrafts(threadId: Long) {
        draftDb.clearDrafts(threadId)
    }

    override fun inviteContactsToCommunity(threadId: Long, contacts: Collection<Address>) {
        val openGroup = lokiThreadDb.getOpenGroupChat(threadId) ?: return
        for (contact in contacts) {
            val message = VisibleMessage()
            message.sentTimestamp = clock.currentTimeMills()
            val openGroupInvitation = OpenGroupInvitation().apply {
                name = openGroup.name
                url = openGroup.joinURL
            }
            message.openGroupInvitation = openGroupInvitation
            val contactThreadId = threadDb.getOrCreateThreadIdFor(contact)
            val expirationConfig = recipientRepository.getRecipientSync(contact).expiryMode
            val expireStartedAt = if (expirationConfig is ExpiryMode.AfterSend) message.sentTimestamp!! else 0
            val outgoingTextMessage = OutgoingTextMessage.fromOpenGroupInvitation(
                openGroupInvitation,
                contact,
                message.sentTimestamp,
                expirationConfig.expiryMillis,
                expireStartedAt
            )

            message.id = MessageId(
                smsDb.insertMessageOutbox(contactThreadId, outgoingTextMessage, false, message.sentTimestamp!!, true),
                false
            )

            MessageSender.send(message, contact)
        }
    }

    override fun isGroupReadOnly(recipient: Recipient): Boolean {
        // We only care about group v2 recipient
        if (!recipient.isGroupV2Recipient) {
            return false
        }

        val groupId = recipient.address.toString()
        return configFactory.withUserConfigs { configs ->
            configs.userGroups.getClosedGroup(groupId)?.let { it.kicked || it.destroyed } == true
        }
    }

    override fun getLastSentMessageID(threadId: Long): Flow<MessageId?> {
        return (threadDb.updateNotifications.filter { it == threadId } as Flow<*>)
            .onStart { emit(Unit) }
            .map {
                withContext(Dispatchers.Default) {
                    mmsSmsDb.getLastSentMessageID(threadId)
                }
            }
    }

    // This assumes that recipient.isContactRecipient is true
    override fun setBlocked(recipient: Address, blocked: Boolean) {
        if (recipient.isStandard) {
            storage.setBlocked(listOf(recipient), blocked)
        }
    }

    /**
     * This will delete these messages from the db
     * Not to be confused with 'marking messages as deleted'
     */
    override fun deleteMessages(messages: Set<MessageRecord>, threadId: Long) {
        // split the messages into mms and sms
        val (mms, sms) = messages.partition { it.isMms }

        if(mms.isNotEmpty()){
            messageDataProvider.deleteMessages(mms.map { it.id }, threadId, isSms = false)
        }

        if(sms.isNotEmpty()){
            messageDataProvider.deleteMessages(sms.map { it.id }, threadId, isSms = true)
        }
    }

    /**
     * This will mark the messages as deleted.
     * They won't be removed from the db but instead will appear as a special type
     * of message that says something like "This message was deleted"
     */
    override fun markAsDeletedLocally(messages: Set<MessageRecord>, displayedMessage: String) {
        // split the messages into mms and sms
        val (mms, sms) = messages.partition { it.isMms }

        if(mms.isNotEmpty()){
            messageDataProvider.markMessagesAsDeleted(
                mms.map { MarkAsDeletedMessage(
                    messageId = it.messageId,
                    isOutgoing = it.isOutgoing
                ) },
                displayedMessage = displayedMessage
            )

            // delete reactions
            storage.deleteReactions(messageIds = mms.map { it.id }, mms = true)
        }

        if(sms.isNotEmpty()){
            messageDataProvider.markMessagesAsDeleted(
                sms.map { MarkAsDeletedMessage(
                    messageId = it.messageId,
                    isOutgoing = it.isOutgoing
                ) },
                displayedMessage = displayedMessage
            )

            // delete reactions
            storage.deleteReactions(messageIds = sms.map { it.id }, mms = false)
        }
    }

    override fun deleteAllLocalMessagesInThreadFromSenderOfMessage(messageRecord: MessageRecord) {
        val threadId = messageRecord.threadId
        val senderId = messageRecord.recipient.address.address
        val messageRecordsToRemoveFromLocalStorage = mmsSmsDb.getAllMessageRecordsFromSenderInThread(threadId, senderId)
        for (message in messageRecordsToRemoveFromLocalStorage) {
            messageDataProvider.deleteMessage(messageId = message.messageId)
        }
    }

    override suspend fun deleteCommunityMessagesRemotely(
        threadId: Long,
        messages: Set<MessageRecord>
    ) {
        val community = checkNotNull(lokiThreadDb.getOpenGroupChat(threadId)) { "Not a community" }

        messages.forEach { message ->
            lokiMessageDb.getServerID(message.messageId)?.let { messageServerID ->
                OpenGroupApi.deleteMessage(messageServerID, community.room, community.server).await()
            }
        }
    }

    override suspend fun delete1on1MessagesRemotely(
        threadId: Long,
        recipient: Address,
        messages: Set<MessageRecord>
    ) {
        // delete the messages remotely
        val userAddress: Address? =  textSecurePreferences.getLocalNumber()?.let { Address.fromSerialized(it) }
        val userAuth = requireNotNull(storage.userAuth) {
            "User auth is required to delete messages remotely"
        }

        messages.forEach { message ->
            // delete from swarm
            messageDataProvider.getServerHashForMessage(message.messageId)
                ?.let { serverHash ->
                    SnodeAPI.deleteMessage(recipient.address, userAuth, listOf(serverHash))
                }

            // send an UnsendRequest to user's swarm
            buildUnsendRequest(message).let { unsendRequest ->
                userAddress?.let { MessageSender.send(unsendRequest, it) }
            }

            // send an UnsendRequest to recipient's swarm
            buildUnsendRequest(message).let { unsendRequest ->
                MessageSender.send(unsendRequest, recipient)
            }
        }
    }

    override suspend fun deleteLegacyGroupMessagesRemotely(
        recipient: Address,
        messages: Set<MessageRecord>
    ) {
        if (recipient.isLegacyGroup) {
            messages.forEach { message ->
                // send an UnsendRequest to group's swarm
                buildUnsendRequest(message).let { unsendRequest ->
                    MessageSender.send(unsendRequest, recipient)
                }
            }
        }
    }

    override suspend fun deleteGroupV2MessagesRemotely(
        recipient: Address,
        messages: Set<MessageRecord>
    ) {
        require(recipient.isGroupV2) { "Recipient is not a group v2 recipient" }

        val groupId = AccountId(recipient.address)
        val hashes = messages.mapNotNullTo(mutableSetOf()) { msg ->
            messageDataProvider.getServerHashForMessage(msg.messageId)
        }

        groupManager.requestMessageDeletion(groupId, hashes)
    }

    override suspend fun deleteNoteToSelfMessagesRemotely(
        threadId: Long,
        recipient: Address,
        messages: Set<MessageRecord>
    ) {
        // delete the messages remotely
        val userAddress: Address? =  textSecurePreferences.getLocalNumber()?.let { Address.fromSerialized(it) }
        val userAuth = requireNotNull(storage.userAuth) {
            "User auth is required to delete messages remotely"
        }

        messages.forEach { message ->
            // delete from swarm
            messageDataProvider.getServerHashForMessage(message.messageId)
                ?.let { serverHash ->
                    SnodeAPI.deleteMessage(recipient.address, userAuth, listOf(serverHash))
                }

            // send an UnsendRequest to user's swarm
            buildUnsendRequest(message).let { unsendRequest ->
                userAddress?.let { MessageSender.send(unsendRequest, it) }
            }
        }
    }

    private fun buildUnsendRequest(message: MessageRecord): UnsendRequest {
        return UnsendRequest(
            author = message.takeUnless { it.isOutgoing }?.run { individualRecipient.address.address } ?: textSecurePreferences.getLocalNumber(),
            timestamp = message.timestamp
        )
    }

    override suspend fun banUser(threadId: Long, recipient: Address): Result<Unit> = runCatching {
        val openGroup = lokiThreadDb.getOpenGroupChat(threadId)!!
        OpenGroupApi.ban(recipient.toString(), openGroup.room, openGroup.server).await()
    }

    override suspend fun banAndDeleteAll(threadId: Long, recipient: Address) = runCatching {
        // Note: This accountId could be the blinded Id
        val openGroup = lokiThreadDb.getOpenGroupChat(threadId)!!

        OpenGroupApi.banAndDeleteAll(recipient.toString(), openGroup.room, openGroup.server).await()
    }

    override suspend fun deleteMessageRequest(thread: ThreadRecord): Result<Unit> {
        val address = thread.recipient.address as? Address.Conversable ?: return Result.success(Unit)

        return declineMessageRequest(
            address
        )
    }

    override suspend fun clearAllMessageRequests(block: Boolean) = runCatching {
        observeConversationList(approved = false)
            .first()
            .forEach { record ->
                deleteMessageRequest(record)
                val recipient = record.recipient
                if (block && !recipient.isGroupV2Recipient) {
                    setBlocked(recipient.address, true)
                }
            }
    }

    override suspend fun clearAllMessages(threadId: Long, groupId: AccountId?): Int {
        return withContext(Dispatchers.Default) {
            // delete data locally
            val deletedHashes = storage.clearAllMessages(threadId)
            Log.i("", "Cleared messages with hashes: $deletedHashes")

            // if required, also sync groupV2 data
            if (groupId != null) {
                groupManager.clearAllMessagesForEveryone(groupId, deletedHashes)
            }

            deletedHashes.size
        }
    }

    override suspend fun acceptMessageRequest(threadId: Long, recipient: Address.Conversable) = runCatching {
        when (recipient) {
            is Address.Standard -> {
                configFactory.withMutableUserConfigs { configs ->
                    configs.contacts.upsertContact(recipient) {
                        approved = true
                    }
                }

                withContext(Dispatchers.Default) {
                    MessageSender.send(message = MessageRequestResponse(true), address = recipient)

                    // add a control message for our user
                    storage.insertMessageRequestResponseFromYou(threadId)
                }
            }

            is Address.Group -> {
                groupManager.respondToInvitation(
                    recipient.accountId,
                    approved = true
                )
            }

            is Address.Community,
            is Address.CommunityBlindedId,
            is Address.LegacyGroup -> {
                // These addresses are not supported for message requests
            }
        }

        Unit
    }

    override suspend fun declineMessageRequest(recipient: Address.Conversable): Result<Unit> = runCatching {
        when (recipient) {
            is Address.Standard -> {
                configFactory.removeContact(recipient.accountId.hexString)
            }

            is Address.Group -> {
                groupManager.respondToInvitation(
                    recipient.accountId,
                    approved = false
                )
            }

            is Address.Community,
            is Address.CommunityBlindedId,
            is Address.LegacyGroup -> {
                // These addresses are not supported for message requests
            }
        }
    }

    override fun hasReceived(threadId: Long): Boolean {
        val cursor = mmsSmsDb.getConversation(threadId, true)
        mmsSmsDb.readerFor(cursor).use { reader ->
            while (reader.next != null) {
                if (!reader.current.isOutgoing) { return true }
            }
        }
        return false
    }

    // Only call this with a closed group thread ID
    override fun getInvitingAdmin(threadId: Long): Address? {
        return lokiMessageDb.groupInviteReferrer(threadId)?.let(Address::fromSerialized)
    }
}