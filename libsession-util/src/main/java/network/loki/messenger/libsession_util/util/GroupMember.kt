package network.loki.messenger.libsession_util.util

data class GroupMember(
    val sessionId: String,
    val name: String?,
    val profilePicture: UserPic?,
    val inviteFailed: Boolean,
    val invitePending: Boolean,
    val admin: Boolean,
    val promotionFailed: Boolean,
    val promotionPending: Boolean,
)