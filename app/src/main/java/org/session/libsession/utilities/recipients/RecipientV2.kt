package org.session.libsession.utilities.recipients

import network.loki.messenger.libsession_util.util.Bytes
import org.session.libsession.avatars.ContactPhoto
import org.session.libsession.utilities.Address
import org.thoughtcrime.securesms.database.model.NotifyType
import java.time.ZonedDateTime

data class RecipientV2(
    val isLocalNumber: Boolean,
    val address: Address,
    val name: String,
    val avatar: ContactPhoto?,
    val approved: Boolean,
    val approvedMe: Boolean,
    val blocked: Boolean,
    val mutedUntil: ZonedDateTime?,
    val autoDownloadAttachments: Boolean,
    @get:NotifyType
    val notifyType: Int,
    val profileAvatar: String?,
    val profileKey: Bytes?
) {
    val isGroupOrCommunityRecipient: Boolean get() = address.isGroupOrCommunity
    val isCommunityRecipient: Boolean get() = address.isCommunity
    val isCommunityInboxRecipient: Boolean get() = address.isCommunityInbox
    val isCommunityOutboxRecipient: Boolean get() = address.isCommunityOutbox
    val isGroupV2Recipient: Boolean get() = address.isGroupV2
}
