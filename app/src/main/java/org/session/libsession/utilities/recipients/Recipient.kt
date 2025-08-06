package org.session.libsession.utilities.recipients

import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_PINNED
import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_VISIBLE
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.isCommunity
import org.session.libsession.utilities.isCommunityInbox
import org.session.libsession.utilities.isGroup
import org.session.libsession.utilities.isGroupOrCommunity
import org.session.libsession.utilities.isGroupV2
import org.session.libsession.utilities.isLegacyGroup
import org.session.libsession.utilities.isStandard
import org.session.libsession.utilities.truncateIdForDisplay
import org.session.libsession.utilities.truncatedForDisplay
import org.thoughtcrime.securesms.database.model.NotifyType
import java.time.ZonedDateTime

data class Recipient(
    val address: Address,
    val data: RecipientData,
    val mutedUntil: ZonedDateTime? = null,
    val autoDownloadAttachments: Boolean? = null,
    val notifyType: NotifyType = NotifyType.ALL,
) {
    /**
     * Whether this recipient is ourself. Note that this check only applies to the standard
     * address.
     */
    val isLocalNumber: Boolean get() = address is Address.Standard && isSelf

    /**
     * Check if this recipient is ourself, this property will handle blinding correctly.
     */
    val isSelf: Boolean get() = data is RecipientData.Self

    /**
     * Check if current user is an admin of this assumed-group recipient.
     * If the recipient is not a group or community, this will always return false.
     */
    val isAdmin: Boolean get() = when (data) {
        is RecipientData.Group -> data.isAdmin
        is RecipientData.Community -> data.openGroup.isAdmin || data.openGroup.isModerator
        else -> false
    }

    val isGroupOrCommunityRecipient: Boolean get() = address.isGroupOrCommunity
    val isCommunityRecipient: Boolean get() = address.isCommunity
    val isCommunityInboxRecipient: Boolean get() = address.isCommunityInbox
    val isGroupV2Recipient: Boolean get() = address.isGroupV2
    val isLegacyGroupRecipient: Boolean get() = address.isLegacyGroup
    val isStandardRecipient: Boolean get() = address.isStandard
    val is1on1: Boolean get() = !isLocalNumber && !address.isGroupOrCommunity
    val isGroupRecipient: Boolean get() = address.isGroup

    val avatar: RemoteFile? get() = data.avatar
    val expiryMode: ExpiryMode get() = when (data) {
        is RecipientData.Self -> data.expiryMode
        is RecipientData.Contact -> data.expiryMode
        is RecipientData.Group -> data.expiryMode
        else -> ExpiryMode.NONE
    }

    val approved: Boolean get() = when (data) {
        is RecipientData.Contact -> data.approved
        is RecipientData.Group -> data.approved
        is RecipientData.Generic -> false
        else -> true
    }

    val proStatus: ProStatus get() = data.proStatus

    val approvedMe: Boolean get() {
        return when (data) {
            is RecipientData.Self -> true
            is RecipientData.Group -> true
            is RecipientData.Generic -> {
                // A generic recipient is the one we only have minimal information about,
                // we don't know if they have approved us or not.
                false
            }
            is RecipientData.Contact -> data.approvedMe
            is RecipientData.Community -> true // Communities don't have approval status for users.
            is RecipientData.BlindedContact -> false
        }
    }

    val blocked: Boolean get() = when (data) {
        is RecipientData.Contact -> data.blocked
        else -> false
    }

    val priority: Long get() = data.priority

    val isPinned: Boolean get() = priority == PRIORITY_PINNED

    @JvmOverloads
    fun isMuted(now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        return mutedUntil?.isAfter(now) == true
    }

    val showCallMenu: Boolean
        get() = !isGroupOrCommunityRecipient && approvedMe && approved

    val mutedUntilMills: Long?
        get() = mutedUntil?.toInstant()?.toEpochMilli()

    val acceptsCommunityMessageRequests: Boolean
        get() = when (data) {
            is RecipientData.BlindedContact -> data.acceptsCommunityMessageRequests
            is RecipientData.Generic -> data.acceptsCommunityMessageRequests
            is RecipientData.Community,
            is RecipientData.Contact,
            is RecipientData.Self,
            is RecipientData.Group -> false
        }
}

sealed interface RecipientData {
    val avatar: RemoteFile?
    val priority: Long

    val proStatus: ProStatus

    /**
     * A recipient that is backed by the config system.
     */
    sealed interface ConfigBased : RecipientData

    data class Generic(
        val displayName: String = "",
        override val avatar: RemoteFile? = null,
        override val priority: Long = PRIORITY_VISIBLE,
        override val proStatus: ProStatus = ProStatus.Unknown,
        val acceptsCommunityMessageRequests: Boolean = false,
    ) : RecipientData

    data class BlindedContact(
        val displayName: String,
        override val avatar: RemoteFile.Encrypted?,
        override val priority: Long,
        override val proStatus: ProStatus,
        val acceptsCommunityMessageRequests: Boolean
    ) : ConfigBased

    data class Community(
        val openGroup: OpenGroup,
        override val priority: Long,
    ) : RecipientData {
        override val avatar: RemoteFile?
            get() = openGroup.imageId?.let { RemoteFile.Community(openGroup.server, openGroup.room, it) }

        override val proStatus: ProStatus
            get() = ProStatus.Unknown
    }

    /**
     * Yourself.
     */
    data class Self(
        val name: String,
        override val avatar: RemoteFile.Encrypted?,
        val expiryMode: ExpiryMode,
        override val priority: Long,
        override val proStatus: ProStatus
    ) : ConfigBased

    /**
     * A recipient that was saved in your contact config.
     */
    data class Contact(
        val name: String,
        val nickname: String?,
        override val avatar: RemoteFile.Encrypted?,
        val approved: Boolean,
        val approvedMe: Boolean,
        val blocked: Boolean,
        val expiryMode: ExpiryMode,
        override val priority: Long,
        override val proStatus: ProStatus
    ) : ConfigBased {
        val displayName: String
            get() = nickname?.takeIf { it.isNotBlank() } ?: name
    }

    /**
     * A recipient that is a groupv2.
     */
    data class Group(
        val name: String,
        override val avatar: RemoteFile.Encrypted?,
        val expiryMode: ExpiryMode,
        val approved: Boolean,
        override val priority: Long,
        val isAdmin: Boolean,
        val kicked: Boolean,
        val destroyed: Boolean,
        override val proStatus: ProStatus
    ) : ConfigBased
}


/**
 * Retrieve a formatted display name for a recipient.
 *
 * @param attachesBlindedId Whether to append the blinded ID to the display name if the address is a blinded address.
 */
@JvmOverloads
fun Recipient.displayName(
    attachesBlindedId: Boolean = false,
): String {
    val name = when (data) {
        is RecipientData.Self -> data.name
        is RecipientData.Contact -> data.displayName
        is RecipientData.Group -> data.name
        is RecipientData.Generic -> data.displayName
        is RecipientData.Community -> data.openGroup.name
        is RecipientData.BlindedContact -> data.displayName
    }

    if (name.isBlank()) {
        val addressToTruncate = when (address) {
            is Address.WithAccountId -> address.accountId.hexString
            is Address.Community -> return address.room // This is last resort - to show the room token
            else -> address.address
        }
        return truncateIdForDisplay(addressToTruncate)
    }

    if (attachesBlindedId && address is Address.Blinded) {
        return "$name (${address.blindedId.truncatedForDisplay()})"
    }

    return name
}