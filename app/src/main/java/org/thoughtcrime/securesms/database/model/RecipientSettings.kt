package org.thoughtcrime.securesms.database.model

import network.loki.messenger.libsession_util.util.UserPic
import java.time.ZonedDateTime

/**
 * Represents local database data for a recipient.
 */
data class RecipientSettings(
    val name: String? = null,
    val muteUntil: ZonedDateTime? = null,
    val notifyType: NotifyType = NotifyType.ALL,
    val autoDownloadAttachments: Boolean = false,
    val profilePic: UserPic? = null,
    val blocksCommunityMessagesRequests: Boolean = true,
    val isPro: Boolean = false,
    val profileUpdated: ZonedDateTime? = null,
)
