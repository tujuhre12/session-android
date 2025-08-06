package org.thoughtcrime.securesms.database

import androidx.collection.LruCache
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_VISIBLE
import network.loki.messenger.libsession_util.ReadableGroupInfoConfig
import network.loki.messenger.libsession_util.util.ExpiryMode
import network.loki.messenger.libsession_util.util.GroupInfo
import network.loki.messenger.libsession_util.util.GroupMember
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.Address.Companion.toAddress
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.ConfigUpdateNotification
import org.session.libsession.utilities.GroupRecord
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.getGroup
import org.session.libsession.utilities.recipients.ProStatus
import org.session.libsession.utilities.recipients.RecipientData
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.recipients.RemoteFile
import org.session.libsession.utilities.recipients.RemoteFile.Companion.toRecipientAvatar
import org.session.libsession.utilities.toBlinded
import org.session.libsession.utilities.toGroupString
import org.session.libsession.utilities.userConfigsChanged
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.database.model.NotifyType
import org.thoughtcrime.securesms.database.model.RecipientSettings
import org.thoughtcrime.securesms.dependencies.ManagerScope
import org.thoughtcrime.securesms.pro.ProStatusManager
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This repository is responsible for observing and retrieving recipient data from different sources.
 *
 * Not to be confused with [RecipientSettingsDatabase], where it manages the actual database storage of
 * some recipient data. Note that not all recipient data is stored in the database, as we've moved
 * them to the config system. Details in the [RecipientSettingsDatabase].
 *
 * This class will source the correct recipient data from different sources based on their types.
 */
@Singleton
class RecipientRepository @Inject constructor(
    private val configFactory: ConfigFactoryProtocol,
    private val groupDatabase: GroupDatabase,
    private val recipientSettingsDatabase: RecipientSettingsDatabase,
    private val preferences: TextSecurePreferences,
    private val lokiThreadDatabase: LokiThreadDatabase,
    private val storage: Lazy<StorageProtocol>,
    private val blindedIdMappingRepository: BlindMappingRepository,
    private val proStatusManager: ProStatusManager,
    @param:ManagerScope private val managerScope: CoroutineScope,
) {
    private val recipientCache = LruCache<Address, WeakReference<SharedFlow<Recipient>>>(512)

    fun observeRecipient(address: Address): Flow<Recipient> {
        synchronized(recipientCache) {
            var cached = recipientCache[address]?.get()
            if (cached == null) {
                cached = createRecipientFlow(address)
                recipientCache.put(address, WeakReference(cached))
            }

            return cached
        }
    }

    fun observeSelf(): Flow<Recipient> {
        return preferences.watchLocalNumber()
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { observeRecipient(it.toAddress()) }
    }

    fun getSelf(): Recipient {
        return getRecipientSync(preferences.getLocalNumber()!!.toAddress())
    }

    // This function creates a flow that emits the recipient information for the given address,
    // the function itself must be fast, not directly access db and lock free, as it is called from a locked context.
    @OptIn(FlowPreview::class)
    private fun createRecipientFlow(address: Address): SharedFlow<Recipient> {
        return flow {
            while (true) {
                val (value, changeSource) = fetchRecipient(
                    address = address,
                    settingsFetcher = {
                        withContext(Dispatchers.Default) { recipientSettingsDatabase.getSettings(it) }
                    },
                    openGroupFetcher = {
                        withContext(Dispatchers.Default) { storage.get().getOpenGroup(it) }
                    }
                )

                emit(value)
                val evt = merge(changeSource,
                    proStatusManager.proStatus.drop(1),
                    proStatusManager.postProLaunchStatus.drop(1)
                )
                    .debounce(200) // Debounce to avoid too frequent updates
                    .first()
                Log.d(TAG, "Recipient changed for ${address.debugString}, triggering event: $evt")
            }

        }.shareIn(
            managerScope,
            // replay must be cleared one when no one is subscribed, so that if no one is subscribed,
            // we will always fetch the latest data. The cache is only valid while there is at least one subscriber.
            SharingStarted.WhileSubscribed(replayExpirationMillis = 0L), replay = 1
        )
    }

    private inline fun fetchRecipient(
        address: Address,
        settingsFetcher: (address: Address) -> RecipientSettings,
        openGroupFetcher: (address: Address.Community) -> OpenGroup?
    ): Pair<Recipient, Flow<*>> {
        val basicRecipient =
            address.toBlinded()?.let { blindedIdMappingRepository.findMappings(it).firstOrNull()?.second }
                ?.let(this::getConfigBasedData)
                ?: getConfigBasedData(address)

        val changeSource: Flow<*>
        val value: Recipient

        when (basicRecipient) {
            is RecipientData.Self -> {
                value = createLocalRecipient(address, basicRecipient)
                changeSource = configFactory.userConfigsChanged()
            }

            is RecipientData.BlindedContact -> {
                value = Recipient(
                    address = address,
                    data = basicRecipient,
                )

                changeSource = configFactory.userConfigsChanged()
            }

            is RecipientData.Contact -> {
                value = createContactRecipient(
                    address = address,
                    basic = basicRecipient,
                    fallbackSettings = settingsFetcher(address)
                )

                changeSource = merge(
                    configFactory.userConfigsChanged(),
                    recipientSettingsDatabase.changeNotification.filter { it == address }
                )
            }

            is RecipientData.Group -> {
                value = createGroupV2Recipient(
                    address = address,
                    basic = basicRecipient,
                    settings = settingsFetcher(address)
                )

                changeSource = merge(
                    configFactory.userConfigsChanged(),
                    configFactory.configUpdateNotifications
                        .filterIsInstance<ConfigUpdateNotification.GroupConfigsUpdated>()
                        .filter { it.groupId.hexString == address.address },
                    recipientSettingsDatabase.changeNotification.filter { it == address }
                )
            }

            null -> {
                // Given address is not backed by the config system so we'll get them from
                // local database.
                // If this is a community inbox, we'll load the underlying blinded recipient settings
                // from the db instead, this is because the "community inbox" recipient is never
                // updated from anywhere, it's purely an address to start a conversation. The data
                // like name and avatar were all updated to the blinded recipients.
                val settings = if (address is Address.CommunityBlindedId) {
                    settingsFetcher(address.blindedId)
                } else {
                    settingsFetcher(address)
                }

                when (address) {
                    is Address.LegacyGroup -> {
                        changeSource = merge(
                            groupDatabase.updateNotification,
                            recipientSettingsDatabase.changeNotification.filter { it == address },
                            configFactory.userConfigsChanged(),
                        )

                        val group: GroupRecord? =
                            groupDatabase.getGroup(address.toGroupString()).orNull()

                        val groupConfig = configFactory.withUserConfigs {
                            it.userGroups.getLegacyGroupInfo(GroupUtil.doubleDecodeGroupId(address.address))
                        }

                        value = group?.let { createLegacyGroupRecipient(address, groupConfig, it, settings) }
                            ?: createGenericRecipient(address, settings)
                    }

                    is Address.Community -> {
                        value = openGroupFetcher(address)
                            ?.let { openGroup ->
                                val groupConfig = configFactory.withUserConfigs {
                                    it.userGroups.getCommunityInfo(openGroup.server, openGroup.room)
                                }

                                createCommunityRecipient(
                                    address,
                                    groupConfig,
                                    openGroup,
                                    settings
                                )
                            }
                            ?: createGenericRecipient(address, settings)

                        changeSource = merge(
                            lokiThreadDatabase.changeNotification,
                            recipientSettingsDatabase.changeNotification.filter { it == address },
                            configFactory.userConfigsChanged(),
                        )
                    }

                    is Address.Standard -> {
                        // If we are a standard address, last attempt to find the
                        // recipient inside all closed groups' member list
                        // members:
                        val allGroups = configFactory.withUserConfigs { it.userGroups.allClosedGroupInfo() }
                        value = allGroups
                            .asSequence()
                            .mapNotNull { groupInfo ->
                                configFactory.withGroupConfigs(AccountId(groupInfo.groupAccountId)) {
                                    it.groupMembers.get(address.address)
                                }
                            }
                            .firstOrNull()
                            ?.let { groupMember -> createGroupMemberRecipient(address, groupMember) }
                            ?: createGenericRecipient(address, settings)

                        changeSource = merge(
                            configFactory.configUpdateNotifications.filterIsInstance<ConfigUpdateNotification.GroupConfigsUpdated>()
                                .filter { it.groupId == address.accountId },
                            configFactory.userConfigsChanged(),
                            recipientSettingsDatabase.changeNotification.filter { it == address }
                        )
                    }

                    else -> {
                        value = createGenericRecipient(address, settings)
                        changeSource = recipientSettingsDatabase.changeNotification.filter { it == address }
                    }
                }
            }
        }

        return value to changeSource
    }

    suspend fun getRecipient(address: Address): Recipient {
        return observeRecipient(address).first()
    }

    /**
     * Returns a [Recipient] for the given address, or null if not found.
     *
     * Note that this method might be querying database directly so use with caution.
     */
    @DelicateCoroutinesApi
    fun getRecipientSync(address: Address): Recipient {
        val flow = observeRecipient(address)

        // If the flow is a SharedFlow, we might be able to access its last cached value directly.
        if (flow is SharedFlow<Recipient?>) {
            val lastCacheValue = flow.replayCache.lastOrNull()
            if (lastCacheValue != null) {
                return lastCacheValue
            }
        }

        // Otherwise, we might have to go to the database to get the recipient..
        return fetchRecipient(
            address = address,
            settingsFetcher = recipientSettingsDatabase::getSettings,
            openGroupFetcher = storage.get()::getOpenGroup
        ).first
    }

    /**
     * Returns a [RecipientData.ConfigBased] for the given address, by only looking
     * at the config data. This method is useful when you know what you are looking for and it's
     * all in memory operation (almost) without any database access.
     */
    fun getConfigBasedData(address: Address): RecipientData.ConfigBased? {
        return when {
            // Is this our own address?
            address.address.equals(preferences.getLocalNumber(), ignoreCase = true) -> {
                configFactory.withUserConfigs { configs ->
                    RecipientData.Self(
                        name = configs.userProfile.getName().orEmpty(),
                        avatar = configs.userProfile.getPic().toRecipientAvatar(),
                        expiryMode = configs.userProfile.getNtsExpiry(),
                        priority = configs.userProfile.getNtsPriority(),
                        proStatus = if (proStatusManager.isCurrentUserPro()) ProStatus.ProVisible else ProStatus.Unknown,
                    )
                }
            }

            // Is this in our contact?
            address is Address.Standard -> {
                configFactory.withUserConfigs { configs ->
                    configs.contacts.get(address.accountId.hexString)
                }?.let { contact ->
                    RecipientData.Contact(
                        name = contact.name,
                        nickname = contact.nickname.takeIf { it.isNotBlank() },
                        avatar = contact.profilePicture.toRecipientAvatar(),
                        approved = contact.approved,
                        approvedMe = contact.approvedMe,
                        blocked = contact.blocked,
                        expiryMode = contact.expiryMode,
                        priority = contact.priority,
                        proStatus = if (proStatusManager.isUserPro(address)) ProStatus.ProVisible else ProStatus.Unknown
                    )
                }
            }

            // Is this a group?
            address is Address.Group -> {
                val groupInfo = configFactory.getGroup(address.accountId) ?: return null
                configFactory.withGroupConfigs(address.accountId) { configs ->
                    RecipientData.Group(
                        avatar = configs.groupInfo.getProfilePic().toRecipientAvatar(),
                        expiryMode = configs.groupInfo.expiryMode,
                        name = configs.groupInfo.getName() ?: groupInfo.name,
                        approved = !groupInfo.invited,
                        priority = groupInfo.priority,
                        isAdmin = groupInfo.adminKey != null,
                        kicked = groupInfo.kicked,
                        destroyed = groupInfo.destroyed,
                        proStatus = if (proStatusManager.isUserPro(address)) ProStatus.ProVisible else ProStatus.Unknown
                    )
                }
            }

            // Is this a blinded contact?
            address is Address.Blinded || address is Address.CommunityBlindedId -> {
                val blinded = address.toBlinded() ?: return null
                val contact = configFactory.withUserConfigs { it.contacts.getBlinded(blinded.blindedId.hexString) } ?: return null

                RecipientData.BlindedContact(
                    displayName = contact.name,
                    avatar = contact.profilePic.toRecipientAvatar(),
                    priority = PRIORITY_VISIBLE,
                    proStatus = if (proStatusManager.isUserPro(address)) ProStatus.ProVisible else ProStatus.Unknown,

                    // This information is not available in the config but we infer that
                    // if you already have this person as blinded contact, you would have been
                    // able to send them a message before.
                    acceptsCommunityMessageRequests = true,
                )
            }

            // Otherwise, there's no fast way to get a basic recipient
            else -> null
        }
    }

    /**
     * Creates a RecipientV2 instance from the provided Address and RecipientSettings.
     * Note that this method assumes the recipient is not ourselves.
     */
    private fun createGenericRecipient(
        address: Address,
        settings: RecipientSettings,
    ): Recipient {
        return Recipient(
            address = address,
            data = RecipientData.Generic(
                displayName = settings.name.orEmpty(),
                avatar = settings.profilePic?.toRecipientAvatar(),
                proStatus = settings.proStatus,
                acceptsCommunityMessageRequests = !settings.blocksCommunityMessagesRequests,
            ),
            mutedUntil = settings.muteUntil,
            autoDownloadAttachments = settings.autoDownloadAttachments,
            notifyType = settings.notifyType,
        )
    }

    companion object {
        private const val TAG = "RecipientRepository"

        private fun createLocalRecipient(address: Address, basic: RecipientData.Self): Recipient {
            return Recipient(
                address = address,
                data = basic,
                autoDownloadAttachments = true,
            )
        }

        private val ReadableGroupInfoConfig.expiryMode: ExpiryMode
            get() {
                val timer = getExpiryTimer()
                return when {
                    timer > 0 -> ExpiryMode.AfterSend(timer)
                    else -> ExpiryMode.NONE
                }
            }

        private fun createGroupV2Recipient(
            address: Address,
            basic: RecipientData.Group,
            settings: RecipientSettings?
        ): Recipient {
            return Recipient(
                address = address,
                data = basic,
                mutedUntil = settings?.muteUntil,
                autoDownloadAttachments = settings?.autoDownloadAttachments,
                notifyType = settings?.notifyType ?: NotifyType.ALL,
            )

        }

        /**
         * Creates a RecipientV2 instance from the provided Contact config and optional fallback settings.
         */
        private fun createContactRecipient(
            address: Address,
            basic: RecipientData.Contact,
            fallbackSettings: RecipientSettings?, // Local db data
        ): Recipient {
            return Recipient(
                address = address,
                data = basic,
                mutedUntil = fallbackSettings?.muteUntil,
                autoDownloadAttachments = fallbackSettings?.autoDownloadAttachments,
                notifyType = fallbackSettings?.notifyType ?: NotifyType.ALL,
            )
        }

        private fun createCommunityRecipient(
            address: Address,
            config: GroupInfo.CommunityGroupInfo?,
            community: OpenGroup,
            settings: RecipientSettings?,
        ): Recipient {
            return Recipient(
                address = address,
                data = RecipientData.Community(
                    openGroup = community,
                    priority = config?.priority ?: PRIORITY_VISIBLE,
                ),
                mutedUntil = settings?.muteUntil,
                autoDownloadAttachments = settings?.autoDownloadAttachments,
                notifyType = settings?.notifyType ?: NotifyType.ALL,
            )
        }

        private fun createLegacyGroupRecipient(
            address: Address,
            config: GroupInfo.LegacyGroupInfo?,
            group: GroupRecord, // Local db data
            settings: RecipientSettings?, // Local db data
        ): Recipient {
            return Recipient(
                address = address,
                data = RecipientData.Generic(
                    displayName = group.title,
                    avatar = if (group.url != null && group.avatarId != null) {
                        RemoteFile.Community(
                            communityServerBaseUrl = group.url,
                            roomId = "",
                            fileId = group.avatarId.toString()
                        )
                    } else {
                        null
                    },
                    priority = config?.priority ?: PRIORITY_VISIBLE,
                    proStatus = ProStatus.Unknown,
                    acceptsCommunityMessageRequests = false,
                ),
                mutedUntil = settings?.muteUntil,
                autoDownloadAttachments = settings?.autoDownloadAttachments,
                notifyType = settings?.notifyType ?: NotifyType.ALL,
            )
        }

        private fun createGroupMemberRecipient(
            address: Address,
            groupMember: GroupMember,
        ): Recipient {
            return Recipient(
                address = address,
                data = RecipientData.Generic(
                    displayName = groupMember.name,
                    avatar = groupMember.profilePic()?.toRecipientAvatar(),
                    acceptsCommunityMessageRequests = false,
                ),
            )
        }

        fun empty(address: Address): Recipient {
            return Recipient(
                address = address,
                data = RecipientData.Generic(),
            )
        }
    }
}