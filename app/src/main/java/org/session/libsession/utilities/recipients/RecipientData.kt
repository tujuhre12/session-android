package org.session.libsession.utilities.recipients

import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_VISIBLE
import network.loki.messenger.libsession_util.util.ExpiryMode
import network.loki.messenger.libsession_util.util.GroupMember
import org.session.libsession.messaging.open_groups.OpenGroup

/**
 * Represents different kind of data associated with different types of recipients.
 */
sealed interface RecipientData {
    val avatar: RemoteFile?
    val priority: Long

    val proStatus: ProStatus

    // Marker interface to distinguish between config-based and other recipient data.
    sealed interface ConfigBased

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
    ) : ConfigBased, RecipientData

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
    ) : ConfigBased, RecipientData

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
    ) : ConfigBased, RecipientData {
        val displayName: String
            get() = nickname?.takeIf { it.isNotBlank() } ?: name
    }

    /**
     * Group data fetched from the config. It's named as "partial" because it does not include
     * all the information we need to resemble a full group recipient, hence not implementing the
     * [RecipientData] interface.
     */
    data class PartialGroup(
        val name: String,
        val avatar: RemoteFile.Encrypted?,
        val expiryMode: ExpiryMode,
        val approved: Boolean,
        val priority: Long,
        val isAdmin: Boolean,
        val kicked: Boolean,
        val destroyed: Boolean,
        val proStatus: ProStatus,
        val members: List<GroupMember>,
    ) : ConfigBased

    /**
     * Full group data that includes additional information that may not be present in the config.
     */
    data class Group(
        val partial: PartialGroup,
        val firstMember: Recipient?, // Used primarily to assemble the profile picture for the group.
        val secondMember: Recipient?, // Used primarily to assemble the profile picture for the group.
    ) : RecipientData {
        override val avatar: RemoteFile?
            get() = partial.avatar

        override val priority: Long
            get() = partial.priority

        override val proStatus: ProStatus
            get() = partial.proStatus
    }
}