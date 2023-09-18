package org.session.libsession.utilities

import network.loki.messenger.libsession_util.Config
import network.loki.messenger.libsession_util.ConfigBase
import network.loki.messenger.libsession_util.Contacts
import network.loki.messenger.libsession_util.ConversationVolatileConfig
import network.loki.messenger.libsession_util.GroupInfoConfig
import network.loki.messenger.libsession_util.GroupKeysConfig
import network.loki.messenger.libsession_util.GroupMembersConfig
import network.loki.messenger.libsession_util.UserGroupsConfig
import network.loki.messenger.libsession_util.UserProfile
import org.session.libsignal.utilities.SessionId

interface ConfigFactoryProtocol {
    val user: UserProfile?
    val contacts: Contacts?
    val convoVolatile: ConversationVolatileConfig?
    val userGroups: UserGroupsConfig?

    fun getGroupInfoConfig(groupSessionId: SessionId): GroupInfoConfig?
    fun getGroupMemberConfig(groupSessionId: SessionId): GroupMembersConfig?
    fun getGroupKeysConfig(groupSessionId: SessionId): GroupKeysConfig?

    fun getUserConfigs(): List<ConfigBase>
    fun persist(forConfigObject: Config, timestamp: Long)

    fun conversationInConfig(publicKey: String?, groupPublicKey: String?, openGroupId: String?, visibleOnly: Boolean): Boolean
    fun canPerformChange(variant: String, publicKey: String, changeTimestampMs: Long): Boolean
    fun saveGroupConfigs(
        groupKeys: GroupKeysConfig,
        groupInfo: GroupInfoConfig,
        groupMembers: GroupMembersConfig
    )
}

interface ConfigFactoryUpdateListener {
    fun notifyUpdates(forConfigObject: Config)
}