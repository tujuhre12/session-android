package org.session.libsession.utilities.recipients

import androidx.annotation.IntDef
import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_PINNED
import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_VISIBLE
import network.loki.messenger.libsession_util.util.Bytes
import network.loki.messenger.libsession_util.util.ExpiryMode
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.truncateIdForDisplay
import org.session.libsignal.utilities.IdPrefix
import org.thoughtcrime.securesms.database.RecipientDatabase
import java.time.ZonedDateTime

data class Recipient(
    val address: Address,
    val basic: BasicRecipient,
    val mutedUntil: ZonedDateTime?,
    val autoDownloadAttachments: Boolean?,
    @get:NotifyType
    val notifyType: Int,
    val acceptsCommunityMessageRequests: Boolean,
    val notificationChannel: String? = null,
) {
    val isLocalNumber: Boolean get() = basic.isLocalNumber
    val isGroupOrCommunityRecipient: Boolean get() = address.isGroupOrCommunity
    val isCommunityRecipient: Boolean get() = address.isCommunity
    val isCommunityInboxRecipient: Boolean get() = address.isCommunityInbox
    val isGroupV2Recipient: Boolean get() = address.isGroupV2
    val isLegacyGroupRecipient: Boolean get() = address.isLegacyGroup
    val isContactRecipient: Boolean get() = address.isContact
    val is1on1: Boolean get() = !isLocalNumber && address.isContact
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
                // If the recipient is a blinded address, we will never know if they approved us. So
                // they will be treated as if they did not approve us.
                // Of course if we can find out their real address, we will be able to know the status
                // of approval on that real address, just not on this blinded address.
                address.toBlindedId() == null
            }
            is BasicRecipient.Contact -> basic.approvedMe
        }
    }

    val blocked: Boolean get() = when (basic) {
        is BasicRecipient.Generic -> basic.blocked
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
                basic = BasicRecipient.Generic(),
                address = address,
                mutedUntil = null,
                autoDownloadAttachments = true,
                notifyType = RecipientDatabase.NOTIFY_TYPE_ALL,
                acceptsCommunityMessageRequests = false,
            )
        }
    }
}

sealed interface BasicRecipient {
    val isLocalNumber: Boolean
    val avatar: RemoteFile?
    val priority: Long

    /**
     * A recipient that is backed by the config system.
     */
    sealed interface ConfigBasedRecipient : BasicRecipient

    data class Generic(
        val displayName: String = "",
        override val avatar: RemoteFile? = null,
        override val isLocalNumber: Boolean = false,
        val blocked: Boolean = false,
        override val priority: Long = PRIORITY_VISIBLE,
    ) : BasicRecipient

    /**
     * Yourself.
     */
    data class Self(
        val name: String,
        override val avatar: RemoteFile.Encrypted?,
        val expiryMode: ExpiryMode,
        val acceptsCommunityMessageRequests: Boolean,
        override val priority: Long,
    ) : ConfigBasedRecipient {
        override val isLocalNumber: Boolean
            get() = true
    }

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

        override val isLocalNumber: Boolean
            get() = false
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
    ) : ConfigBasedRecipient {
        override val isLocalNumber: Boolean
            get() = false
    }
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

/**
 * Represents local database data for a recipient.
 */
class RecipientSettings(
    val blocked: Boolean,
    val approved: Boolean,
    val approvedMe: Boolean,
    val muteUntil: Long,
    val notifyType: Int,
    val autoDownloadAttachments: Boolean?,
    val expireMessages: Int,
    val profileKey: ByteArray?,
    val systemDisplayName: String?,
    val profileName: String?,
    val profileAvatar: String?,
    val blocksCommunityMessagesRequests: Boolean
) {
    val profilePic: UserPic? get() =
        if (!profileAvatar.isNullOrBlank() && profileKey != null) {
            UserPic(profileAvatar, profileKey)
        } else {
            null
        }
}

@Retention(AnnotationRetention.SOURCE)
@IntDef(RecipientDatabase.NOTIFY_TYPE_MENTIONS, RecipientDatabase.NOTIFY_TYPE_ALL, RecipientDatabase.NOTIFY_TYPE_NONE)
annotation class NotifyType


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
    }

    if (name.isBlank()) {
        return truncateIdForDisplay(address.address)
    }

    if (attachesBlindedId &&
        IdPrefix.fromValue(address.address)?.isBlinded() == true) {
        return "$name (${truncateIdForDisplay(address.address)})"
    }

    return name
}