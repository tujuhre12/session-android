package org.session.libsession.messaging.open_groups

import org.session.libsignal.utilities.AccountId

data class GroupMemberAndRole(
    val memberId: AccountId,
    val role: GroupMemberRole
)

enum class GroupMemberRole(val canModerate: Boolean = false) {
    STANDARD,
    ZOOMBIE,
    MODERATOR(true),
    ADMIN(true),
    HIDDEN_MODERATOR(true),
    HIDDEN_ADMIN(true),
}
