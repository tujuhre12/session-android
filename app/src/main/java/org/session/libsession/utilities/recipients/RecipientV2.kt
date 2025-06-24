package org.session.libsession.utilities.recipients

import network.loki.messenger.libsession_util.util.Bytes
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsession.utilities.Address
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
    val autoDownloadAttachments: Boolean,
    @get:NotifyType
    val notifyType: Int,
    val avatar: RecipientAvatar?
) {
    val isGroupOrCommunityRecipient: Boolean get() = address.isGroupOrCommunity
    val isCommunityRecipient: Boolean get() = address.isCommunity
    val isCommunityInboxRecipient: Boolean get() = address.isCommunityInbox
    val isCommunityOutboxRecipient: Boolean get() = address.isCommunityOutbox
    val isGroupV2Recipient: Boolean get() = address.isGroupV2
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