package org.thoughtcrime.securesms.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import network.loki.messenger.libsession_util.ReadableGroupInfoConfig
import network.loki.messenger.libsession_util.util.Contact
import network.loki.messenger.libsession_util.util.ExpiryMode
import network.loki.messenger.libsession_util.util.GroupInfo
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.ConfigUpdateNotification
import org.session.libsession.utilities.GroupRecord
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.getGroup
import org.session.libsession.utilities.recipients.Recipient.RecipientSettings
import org.session.libsession.utilities.recipients.RecipientAvatar
import org.session.libsession.utilities.recipients.RecipientAvatar.Companion.toRecipientAvatar
import org.session.libsession.utilities.recipients.RecipientV2
import org.session.libsession.utilities.userConfigsChanged
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Log
import java.lang.ref.WeakReference
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This repository is responsible for observing and retrieving recipient data from different sources.
 *
 * Not to be confused with [RecipientDatabase], where it manages the actual database storage of
 * some recipient data. Note that not all recipient data is stored in the database, as we've moved
 * them to the config system. Details in the [RecipientDatabase].
 *
 * This class will source the correct recipient data from different sources based on their types.
 */
@Singleton
class RecipientRepository @Inject constructor(
    private val configFactory: ConfigFactoryProtocol,
    private val groupDatabase: GroupDatabase,
    private val recipientDatabase: RecipientDatabase,
    private val preferences: TextSecurePreferences,
) {
    private val recipientCache = HashMap<Address, WeakReference<SharedFlow<RecipientV2?>>>()

    fun observeRecipient(address: Address): Flow<RecipientV2?> {
        synchronized(recipientCache) {
            var cached = recipientCache[address]?.get()
            if (cached == null) {
                cached = createRecipientFlow(address)
                recipientCache[address] = WeakReference(cached)
            }

            return cached
        }
    }

    // This function creates a flow that emits the recipient information for the given address,
    // the function itself must be fast, not directly access db and lock free, as it is called from a locked context.
    private fun createRecipientFlow(address: Address): SharedFlow<RecipientV2?> {
        return flow {
            while (true) {
                val (value, changeSource) = fetchRecipient(address)
                    ?: run {
                        // In this case, we won't be able to fetch the recipient forever, so we
                        // will emit null and stop the flow.
                        emit(null)
                        return@flow
                    }

                emit(value)
                changeSource.first()
                Log.d(TAG, "Recipient changed for ${address.address.substring(0..10)}")
            }

        }.shareIn(GlobalScope, SharingStarted.WhileSubscribed(), replay = 1)
    }

    private suspend fun fetchRecipient(address: Address): Pair<RecipientV2?, Flow<*>>? {
        val myAddress by lazy { preferences.getLocalNumber()?.let(Address::fromSerialized) }

        // Short-circuit for our own address.
        if (address.isContact && address == myAddress) {
            return configFactory.withUserConfigs {
                createLocalRecipient(
                    address,
                    it.userProfile.getName().orEmpty(),
                    it.userProfile.getPic()
                )
            } to configFactory.userConfigsChanged()
        }

        val changeSource: Flow<*>
        val value: RecipientV2?

        // We'll always try to fetch the recipient settings from the database first
        val settings: RecipientSettings? = withContext(Dispatchers.Default) {
            recipientDatabase.getRecipientSettings(address).orNull()
        }

        when {
            // Address is a legit 05 person (not necessarily a "real" contact)
            address.isContact && AccountId.fromStringOrNull(address.address)?.prefix == IdPrefix.STANDARD -> {
                // We know this address can be looked up in the contacts config or the recipient database.
                changeSource = merge(
                    configFactory.userConfigsChanged(),
                    recipientDatabase.updateNotifications.filter { it == address }
                )

                val contactInConfig = configFactory.withUserConfigs { configs ->
                    configs.contacts.get(address.address)
                }

                value = if (contactInConfig != null) {
                    createContactRecipient(address, contactInConfig, settings)
                } else if (settings != null) {
                    createGenericRecipient(address, settings)
                } else {
                    null
                }
            }

            address.isGroupV2 -> {
                val groupId = AccountId(address.address)
                val group = configFactory.getGroup(groupId)

                if (group == null) {
                    value = null
                    changeSource = configFactory.userConfigsChanged()
                } else {
                    value = configFactory.withGroupConfigs(groupId) { configs ->
                        createGroupV2Recipient(address, group, configs.groupInfo, settings)
                    }

                    changeSource = merge(
                        configFactory.userConfigsChanged(),
                        configFactory.configUpdateNotifications
                            .filterIsInstance<ConfigUpdateNotification.GroupConfigsUpdated>()
                            .filter { it.groupId.hexString == address.address },
                        recipientDatabase.updateNotifications.filter { it == address }
                    )
                }
            }

            address.isCommunity || address.isLegacyGroup -> {
                changeSource = merge(
                    groupDatabase.updateNotification,
                    recipientDatabase.updateNotifications.filter { it == address }
                )

                val group: GroupRecord? = groupDatabase.getGroup(address.toGroupString()).orNull()
                value = group?.let { createCommunityOrLegacyGroupRecipient(address, it, settings) }
            }

            else -> {
                changeSource = recipientDatabase.updateNotifications.filter { it == address }
                value = settings?.let { createGenericRecipient(address, it) }
            }
        }

        return value to changeSource
    }

    suspend fun getRecipient(address: Address): RecipientV2? {
        return observeRecipient(address).first()
    }

    @Deprecated(
        "Use the suspend version of getRecipient instead",
        ReplaceWith("getRecipient(address)")
    )
    fun getRecipientSync(address: Address): RecipientV2? {
        val flow = observeRecipient(address)

        // If the flow is a SharedFlow, we might be able to access its last cached value directly.
        if (flow is SharedFlow<RecipientV2?>) {
            val lastCacheValue = flow.replayCache.lastOrNull()
            if (lastCacheValue != null) {
                return lastCacheValue
            }
        }

        return runBlocking { flow.first() }
    }

    companion object {
        private const val TAG = "RecipientRepository"

        private fun createLocalRecipient(address: Address, name: String, avatar: UserPic?): RecipientV2 {
            return RecipientV2(
                isLocalNumber = true,
                address = address,
                name = name,
                approved = true,
                approvedMe = true,
                blocked = false,
                mutedUntil = null,
                autoDownloadAttachments = true,
                notifyType = RecipientDatabase.NOTIFY_TYPE_ALL,
                avatar = avatar?.toRecipientAvatar(),
                nickname = null,
                expiryMode = ExpiryMode.NONE,
            )
        }

        private val RecipientSettings.muteUntilDate: ZonedDateTime?
            get() = if (muteUntil > 0) {
                ZonedDateTime.from(Instant.ofEpochMilli(muteUntil))
            } else {
                null
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
            group: GroupInfo.ClosedGroupInfo,
            groupInfo: ReadableGroupInfoConfig,
            settings: RecipientSettings?
        ): RecipientV2 {
            val timer = groupInfo.getExpiryTimer()

            return RecipientV2(
                name = groupInfo.getName() ?: group.name,
                address = address,
                isLocalNumber = false,
                nickname = null,
                approvedMe = true,
                approved = !group.invited,
                avatar = groupInfo.getProfilePic().toRecipientAvatar(),
                blocked = false,
                mutedUntil = settings?.muteUntilDate,
                notifyType = settings?.notifyType ?: RecipientDatabase.NOTIFY_TYPE_ALL,
                autoDownloadAttachments = settings?.autoDownloadAttachments,
                expiryMode = groupInfo.expiryMode,
            )

        }

        /**
         * Creates a RecipientV2 instance from the provided Contact config and optional fallback settings.
         */
        private fun createContactRecipient(
            address: Address,
            contactInConfig: Contact,
            fallbackSettings: RecipientSettings?
        ): RecipientV2 {
            return RecipientV2(
                isLocalNumber = false,
                address = address,
                name = contactInConfig.name,
                nickname = contactInConfig.nickname.takeIf { it.isNotBlank() },
                avatar = contactInConfig.profilePicture.toRecipientAvatar(),
                approved = contactInConfig.approved,
                approvedMe = contactInConfig.approvedMe,
                blocked = contactInConfig.blocked,
                mutedUntil = fallbackSettings?.muteUntilDate,
                autoDownloadAttachments = fallbackSettings?.autoDownloadAttachments,
                notifyType = fallbackSettings?.notifyType
                    ?: RecipientDatabase.NOTIFY_TYPE_ALL,
                expiryMode = contactInConfig.expiryMode,
            )
        }

        private fun createCommunityOrLegacyGroupRecipient(
            address: Address,
            group: GroupRecord,
            settings: RecipientSettings?
        ): RecipientV2 {
            return RecipientV2(
                isLocalNumber = false,
                address = address,
                name = group.title,
                nickname = null,
                avatar = RecipientAvatar.fromBytes(group.avatar),
                approved = true,
                approvedMe = true,
                blocked = false,
                mutedUntil = settings?.muteUntilDate,
                autoDownloadAttachments = settings?.autoDownloadAttachments,
                notifyType = settings?.notifyType ?: RecipientDatabase.NOTIFY_TYPE_ALL,
                expiryMode = ExpiryMode.NONE,
            )
        }

        /**
         * Creates a RecipientV2 instance from the provided Address and RecipientSettings.
         * Note that this method assumes the recipient is not ourselves.
         */
        private fun createGenericRecipient(address: Address, settings: RecipientSettings): RecipientV2 {
            return RecipientV2(
                isLocalNumber = false,
                address = address,
                name = settings.profileName.orEmpty(),
                nickname = settings.systemDisplayName,
                avatar = settings.profileAvatar?.let { RecipientAvatar.from(it, settings.profileKey) },
                approved = settings.isApproved,
                approvedMe = settings.hasApprovedMe(),
                blocked = settings.isBlocked,
                mutedUntil = settings.muteUntil.takeIf { it > 0 }
                    ?.let { ZonedDateTime.from(Instant.ofEpochMilli(it)) },
                autoDownloadAttachments = settings.autoDownloadAttachments,
                notifyType = settings.notifyType,
                expiryMode = ExpiryMode.NONE, // A generic recipient does not have an expiry mode
            )
        }

        fun empty(address: Address): RecipientV2 {
            return RecipientV2(
                isLocalNumber = false,
                address = address,
                name = "",
                nickname = null,
                approved = false,
                approvedMe = false,
                blocked = false,
                mutedUntil = null,
                autoDownloadAttachments = null,
                notifyType = RecipientDatabase.NOTIFY_TYPE_ALL,
                avatar = null,
                expiryMode = ExpiryMode.NONE,
            )
        }
    }
}