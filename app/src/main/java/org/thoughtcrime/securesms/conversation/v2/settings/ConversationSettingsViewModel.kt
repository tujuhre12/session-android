package org.thoughtcrime.securesms.conversation.v2.settings

import android.content.Context
import android.graphics.Path.Op
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.StringSubstitutionConstants.TIME_KEY
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.getGroup
import org.session.libsession.utilities.recipients.MessageType
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.IdPrefix
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.repository.ConversationRepository
import org.thoughtcrime.securesms.ui.getSubbedString
import org.thoughtcrime.securesms.util.AvatarUIData
import org.thoughtcrime.securesms.util.AvatarUtils


@HiltViewModel(assistedFactory = ConversationSettingsViewModel.Factory::class)
class ConversationSettingsViewModel @AssistedInject constructor(
    @Assisted private val threadId: Long,
    @ApplicationContext private val context: Context,
    private val avatarUtils: AvatarUtils,
    private val repository: ConversationRepository,
    private val configFactory: ConfigFactoryProtocol,
    private val storage: StorageProtocol,
    private val textSecurePreferences: TextSecurePreferences,
) : ViewModel() {

    private val _uiState: MutableStateFlow<UIState> = MutableStateFlow(
        UIState(
            avatarUIData = AvatarUIData(emptyList())
        )
    )
    val uiState: StateFlow<UIState> = _uiState

    var recipient: Recipient? = null

    init {
        viewModelScope.launch(Dispatchers.Default) {
            repository.recipientUpdateFlow(threadId).collect{
                recipient = it
                getStateFromRecipient()
            }
        }
    }

    private suspend fun getStateFromRecipient(){
        val conversation = recipient ?: return
        val configContact = configFactory.withUserConfigs { configs ->
            configs.contacts.get(conversation.address.toString())
        }

        // admin
        val isAdmin =  when {
            // for Groups V2
            conversation.isGroupV2Recipient -> {
                configFactory.getGroup(AccountId(conversation.address.toString()))
                    ?.hasAdminKey() == true
            }

            // for communities the the `isUserModerator` field
            conversation.isCommunityRecipient -> {
                storage.getOpenGroup(threadId)?.let { openGroup ->
                    val userPublicKey = textSecurePreferences.getLocalNumber() ?: return@let false
                    val keyPair = storage.getUserED25519KeyPair() ?: return@let false
                    val blindedPublicKey = openGroup.publicKey.let { SodiumUtilities.blindedKeyPair(it, keyPair)?.publicKey?.asBytes }
                        ?.let { AccountId(IdPrefix.BLINDED, it) }?.hexString
                    OpenGroupManager.isUserModerator(context, openGroup.id, userPublicKey, blindedPublicKey)
                } ?: false
            }

            // false in other cases
            else -> false
        }

        // edit name - Can edit name for 1on1, or if admin of a groupV2
        val canEditName = when {
            conversation.is1on1 -> true
            conversation.isGroupV2Recipient && isAdmin -> true
            else -> false
        }

        // description / display name
        val description = when{
            // for 1on1, if the user has a nickname it should be displayed as the
            // main name, and the description should show the real name in parentheses
            conversation.is1on1 -> {
                if(configContact?.nickname?.isNotEmpty() == true &&
                    configContact.name.isNotEmpty()) {
                   "(${configContact.name})"
                } else null
            }

            //todo UCS add description for other types

            else -> null
        }

        // account ID
        val accountId = when{
            conversation.is1on1 || conversation.isLocalNumber -> conversation.address.toString()
            else -> null
        }

        _uiState.update {
            _uiState.value.copy(
                name = conversation.takeUnless { it.isLocalNumber }?.name ?: context.getString(
                    R.string.noteToSelf),
                canEditName = canEditName,
                description = description,
                accountId = accountId,
                avatarUIData = avatarUtils.getUIDataFromRecipient(conversation)
            )
        }
    }

    private fun copyAccountId(){
        //todo UCS implement
    }

    @AssistedFactory
    interface Factory {
        fun create(threadId: Long): ConversationSettingsViewModel
    }

    val optionCopyAccountId: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.accountIDCopy),
            icon = R.drawable.ic_copy,
            qaTag = R.string.qa_conversation_settings_copy_account,
            onClick = ::copyAccountId
        )
    }

    val optionSearch: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.searchConversation),
            icon = R.drawable.ic_search,
            qaTag = R.string.qa_conversation_settings_search,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    //todo UCS will the subtitle need to be dynamic in order to update when the option changes?
    val optionDisappearingMessage: OptionsItem by lazy {
        val expiration = storage.getExpirationConfiguration(threadId)
        val subtitle = if(expiration?.isEnabled == true) {
            // Get the type of disappearing message and the abbreviated duration..
            val dmTypeString = when (expiration.expiryMode) {
                is ExpiryMode.AfterRead -> R.string.disappearingMessagesDisappearAfterReadState
                else -> R.string.disappearingMessagesDisappearAfterSendState
            }
            val durationAbbreviated =
                ExpirationUtil.getExpirationAbbreviatedDisplayValue(expiration.expiryMode.expirySeconds)

            // ..then substitute into the string..
            context.getSubbedString(
                dmTypeString,
                TIME_KEY to durationAbbreviated
            )
        } else null

        OptionsItem(
            name = context.getString(R.string.disappearingMessages),
            subtitle = subtitle,
            icon = R.drawable.ic_timer,
            qaTag = R.string.qa_conversation_settings_disappearing,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    val optionPin: OptionsItem by lazy {
        OptionsItem(
            name = context.getString(R.string.pinConversation),
            icon = R.drawable.ic_pin,
            qaTag = R.string.qa_conversation_settings_pin,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    val optionUnpin: OptionsItem by lazy {
        OptionsItem(
            name = context.getString(R.string.pinUnpinConversation),
            icon = R.drawable.ic_pin_off,
            qaTag = R.string.qa_conversation_settings_pin, //todo UCS check with emily if this needs its own
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    fun optionNotifications(subtitle: String?): OptionsItem {
        return OptionsItem(
            name = context.getString(R.string.sessionNotifications),
            subtitle = subtitle,
            icon = R.drawable.ic_volume_2,
            qaTag = R.string.qa_conversation_settings_notifications,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    val optionAttachments: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.attachments),
            icon = R.drawable.ic_file,
            qaTag = R.string.qa_conversation_settings_attachments,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    val optionBlock: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.block),
            icon = R.drawable.ic_ban,
            qaTag = R.string.qa_conversation_settings_block,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    val optionClearMessages: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.clearMessages),
            icon = R.drawable.ic_message_trash_custom,
            qaTag = R.string.qa_conversation_settings_clear_messages,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    val optionDeleteConversation: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.conversationsDelete),
            icon = R.drawable.ic_trash_2,
            qaTag = R.string.qa_conversation_settings_delete_conversation,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    val optionDeleteContact: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.contactDelete),
            icon = R.drawable.ic_user_round_trash,
            qaTag = R.string.qa_conversation_settings_delete_contact,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    val optionHideNTS: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.noteToSelfHide),
            icon = R.drawable.ic_eye_off,
            qaTag = R.string.qa_conversation_settings_hide_nts,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    // Groups
    val optionGroupMembers: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.groupMembers),
            icon = R.drawable.ic_users_round,
            qaTag = R.string.qa_conversation_settings_group_members,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    val optionInviteMembers: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.membersInvite),
            icon = R.drawable.ic_user_round_plus,
            qaTag = R.string.qa_conversation_settings_invite_contacts,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    val optionManageMembers: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.manageMembers),
            icon = R.drawable.ic_user_round_pen,
            qaTag = R.string.qa_conversation_settings_manage_members,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    val optionLeaveGroup: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.groupLeave),
            icon = R.drawable.ic_log_out,
            qaTag = R.string.qa_conversation_settings_leave_group,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    val optionDeleteGroup: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.groupDelete),
            icon = R.drawable.ic_trash_2,
            qaTag = R.string.qa_conversation_settings_delete_group,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    // Community
    val optionCopyCommunityURL: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.communityUrlCopy),
            icon = R.drawable.ic_copy,
            qaTag = R.string.qa_conversation_settings_copy_community_url,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    val optionLeaveCommunty: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.communityLeave),
            icon = R.drawable.ic_log_out,
            qaTag = R.string.qa_conversation_settings_leave_community,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    data class UIState(
        val avatarUIData: AvatarUIData,
        val name: String = "",
        val canEditName: Boolean = false,
        val description: String? = null,
        val accountId: String? = null,
        val categories: List<OptionsCategory> = emptyList()
    )

    data class OptionsCategory(
        val name: String? = null,
        val items: List<OptionsSubCategory> = emptyList()
    )

    data class OptionsSubCategory(
        val color: Color,
        val items: List<OptionsItem> = emptyList()
    )

    data class OptionsItem(
        val name: String,
        @DrawableRes val icon: Int,
        @StringRes val qaTag: Int? = null,
        val subtitle: String? = null,
        val onClick: () -> Unit
    )
}
