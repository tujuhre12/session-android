package org.session.libsession.utilities.recipients

import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_PINNED
import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_VISIBLE
import network.loki.messenger.libsession_util.util.Bytes
import network.loki.messenger.libsession_util.util.ExpiryMode
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.isCommunity
import org.session.libsession.utilities.isCommunityInbox
import org.session.libsession.utilities.isGroup
import org.session.libsession.utilities.isGroupOrCommunity
import org.session.libsession.utilities.isGroupV2
import org.session.libsession.utilities.isLegacyGroup
import org.session.libsession.utilities.isStandard
import org.session.libsession.utilities.toBlindedId
import org.session.libsession.utilities.truncateIdForDisplay
import org.session.libsession.utilities.truncatedForDisplay
import org.thoughtcrime.securesms.database.model.NotifyType
import java.time.ZonedDateTime

data class Recipient(
    val address: Address,
    val basic: BasicRecipient,
    val mutedUntil: ZonedDateTime? = null,
    val autoDownloadAttachments: Boolean? = null,
    val notifyType: NotifyType = NotifyType.ALL,
    val acceptsCommunityMessageRequests: Boolean = false,
) {
    /**
     * Whether this recipient is ourself. Note that this check only applies to the standard
     * address.
     */
    val isLocalNumber: Boolean get() = address is Address.Standard && isSelf

    /**
     * Check if this recipient is ourself, this property will handle blinding correctly.
     */
    val isSelf: Boolean get() = basic is BasicRecipient.Self

    /**
     * Check if current user is an admin of this assumed-group recipient.
     * If the recipient is not a group or community, this will always return false.
     */
    val isAdmin: Boolean get() = when (basic) {
        is BasicRecipient.Group -> basic.isAdmin
        is BasicRecipient.Community -> basic.openGroup.isAdmin || basic.openGroup.isModerator
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

    val avatar: RemoteFile? get() = basic.avatar
    val expiryMode: ExpiryMode get() = when (basic) {
        is BasicRecipient.Self -> basic.expiryMode
        is BasicRecipient.Contact -> basic.expiryMode
        is BasicRecipient.Group -> basic.expiryMode
        else -> ExpiryMode.NONE
    }

    val approved: Boolean get() = when (basic) {
        is BasicRecipient.Contact -> basic.approved
        is BasicRecipient.Group -> basic.approved
        else -> true
    }

    val approvedMe: Boolean get() {
        return when (basic) {
            is BasicRecipient.Self -> true
            is BasicRecipient.Group -> true
            is BasicRecipient.Generic -> {
                // A generic recipient is the one we only have minimal information about,
                // it's very unlikely they have approved us.
                false
            }
            is BasicRecipient.Contact -> basic.approvedMe
            is BasicRecipient.Community -> true // Communities don't have approval status for users.
        }
    }

    val blocked: Boolean get() = when (basic) {
        is BasicRecipient.Contact -> basic.blocked
        else -> false
    }

    val priority: Long get() = basic.priority

    val isPinned: Boolean get() = priority == PRIORITY_PINNED

    @JvmOverloads
    fun isMuted(now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        return mutedUntil?.isAfter(now) == true
    }

    val showCallMenu: Boolean
        get() = !isGroupOrCommunityRecipient && approvedMe && approved

    val mutedUntilMills: Long?
        get() = mutedUntil?.toInstant()?.toEpochMilli()
    
    companion object {
        fun empty(address: Address): Recipient {
            return Recipient(
                address = address,
                basic = BasicRecipient.Generic(),
                autoDownloadAttachments = true,
            )
        }
    }
}

sealed interface BasicRecipient {
    val avatar: RemoteFile?
    val priority: Long

    /**
     * A recipient that is backed by the config system.
     */
    sealed interface ConfigBasedRecipient : BasicRecipient

    data class Generic(
        val displayName: String = "",
        override val avatar: RemoteFile? = null,
        override val priority: Long = PRIORITY_VISIBLE,
    ) : BasicRecipient

    data class Community(
        val openGroup: OpenGroup,
        override val priority: Long
    ) : BasicRecipient {
        override val avatar: RemoteFile?
            get() = openGroup.imageId?.let { RemoteFile.Community(openGroup.server, openGroup.room, it) }
    }

    /**
     * Yourself.
     */
    data class Self(
        val name: String,
        override val avatar: RemoteFile.Encrypted?,
        val expiryMode: ExpiryMode,
        val acceptsCommunityMessageRequests: Boolean,
        override val priority: Long,
    ) : ConfigBasedRecipient

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
    ) : ConfigBasedRecipient {
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
    ) : ConfigBasedRecipient
}


/**
 * Represents a remote file that can be downloaded.
 */
sealed interface RemoteFile {
    data class Encrypted(val url: String, val key: Bytes) : RemoteFile
    data class Community(val communityServerBaseUrl: String, val roomId: String, val fileId: String) : RemoteFile
    companion object {
        fun UserPic.toRecipientAvatar(): Encrypted? {
            return when {
                url.isBlank() -> null
                else ->  Encrypted(
                    url = url,
                    key = key
                )
            }
        }

        fun from(url: String, bytes: ByteArray?): RemoteFile? {
            return if (url.isNotBlank() && bytes != null && bytes.isNotEmpty()) {
                Encrypted(url, Bytes(bytes))
            } else {
                null
            }
        }
    }
}

fun RemoteFile.toUserPic(): UserPic? {
    return when (this) {
        is RemoteFile.Encrypted -> UserPic(url, key)
        is RemoteFile.Community -> null
    }
}

/**
 * Retrieve the
 */
@JvmOverloads
fun Recipient.displayName(
    attachesBlindedId: Boolean = false,
): String {
    val name = when (basic) {
        is BasicRecipient.Self -> basic.name
        is BasicRecipient.Contact -> basic.displayName
        is BasicRecipient.Group -> basic.name
        is BasicRecipient.Generic -> basic.displayName
        is BasicRecipient.Community -> basic.openGroup.name
    }

    if (name.isBlank()) {
        return truncateIdForDisplay(address.address)
    }

    if (attachesBlindedId && address is Address.Blinded) {
        return "$name (${address.blindedId.truncatedForDisplay()})"
    }

    return name
}