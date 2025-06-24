package org.session.libsession.utilities.recipients

import network.loki.messenger.libsession_util.util.Bytes
import network.loki.messenger.libsession_util.util.ExpiryMode
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.utilities.Address
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.model.NotifyType
import java.time.ZonedDateTime

data class RecipientV2(
    val isLocalNumber: Boolean,
    val address: Address,
    val nickname: String?,
    val name: String,
    val approved: Boolean,
    val approvedMe: Boolean,
    val blocked: Boolean,
    val mutedUntil: ZonedDateTime?,
    val autoDownloadAttachments: Boolean?,
    @get:NotifyType
    val notifyType: Int,
    val avatar: RecipientAvatar?,
    val expiryMode: ExpiryMode,
) {
    val isGroupOrCommunityRecipient: Boolean get() = address.isGroupOrCommunity
    val isCommunityRecipient: Boolean get() = address.isCommunity
    val isCommunityInboxRecipient: Boolean get() = address.isCommunityInbox
    val isCommunityOutboxRecipient: Boolean get() = address.isCommunityOutbox
    val isGroupV2Recipient: Boolean get() = address.isGroupV2
    val isLegacyGroupRecipient: Boolean get() = address.isLegacyGroup

    val displayName: String
        get() = nickname?.takeIf { it.isNotBlank() } ?: name

    fun isMuted(now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        return mutedUntil?.isAfter(now) == true
    }

    val mutedUntilMills: Long?
        get() = mutedUntil?.toInstant()?.toEpochMilli()

    companion object {
        fun empty(address: Address): RecipientV2 {
            return RecipientV2(
                isLocalNumber = false,
                address = address,
                nickname = null,
                name = "",
                approved = false,
                approvedMe = false,
                blocked = false,
                mutedUntil = null,
                autoDownloadAttachments = true,
                notifyType = RecipientDatabase.NOTIFY_TYPE_ALL,
                avatar = null,
                expiryMode = ExpiryMode.NONE,
            )
        }
    }
}


sealed interface RecipientAvatar {
    data class EncryptedRemotePic(val url: String, val key: Bytes) : RecipientAvatar
    data class Inline(val bytes: Bytes) : RecipientAvatar

    companion object {
        fun UserPic.toRecipientAvatar(): RecipientAvatar? {
            return when {
                url.isBlank() -> null
                else ->  EncryptedRemotePic(
                    url = url,
                    key = key
                )
            }
        }

        fun from(url: String, bytes: ByteArray?): RecipientAvatar? {
            return if (url.isNotBlank() && bytes != null && bytes.isNotEmpty()) {
                EncryptedRemotePic(url, Bytes(bytes))
            } else {
                null
            }
        }

        fun fromBytes(bytes: ByteArray?): RecipientAvatar? {
            return if (bytes == null || bytes.isEmpty()) {
                null
            } else {
                Inline(Bytes(bytes))
            }
        }
    }
}