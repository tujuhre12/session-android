package org.thoughtcrime.securesms.conversation.v2.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity.CLIPBOARD_SERVICE
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
import network.loki.messenger.libsession_util.util.GroupInfo
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.StringSubstitutionConstants.TIME_KEY
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.getGroup
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
    private val navigator: ConversationSettingsNavigator
) : ViewModel() {

    private val _uiState: MutableStateFlow<UIState> = MutableStateFlow(
        UIState(
            avatarUIData = AvatarUIData(emptyList())
        )
    )
    val uiState: StateFlow<UIState> = _uiState

    private var recipient: Recipient? = null

    private val groupV2: GroupInfo.ClosedGroupInfo? by lazy {
        if(recipient == null) return@lazy null
        configFactory.getGroup(AccountId(recipient!!.address.toString()))
    }

    private val community: OpenGroup? by lazy {
        storage.getOpenGroup(threadId)
    }

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
        val isAdmin: Boolean =  when {
            // for Groups V2
            conversation.isGroupV2Recipient -> groupV2?.hasAdminKey() == true

            // for communities the the `isUserModerator` field
            conversation.isCommunityRecipient -> isCommunityAdmin()

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
        val description: String? = when{
            // for 1on1, if the user has a nickname it should be displayed as the
            // main name, and the description should show the real name in parentheses
            conversation.is1on1 -> {
                if(configContact?.nickname?.isNotEmpty() == true &&
                    configContact.name.isNotEmpty()) {
                   "(${configContact.name})"
                } else null
            }

            conversation.isGroupV2Recipient -> {
                if(groupV2 == null) null
                else {
                    configFactory.withGroupConfigs(AccountId(groupV2!!.groupAccountId)){
                        it.groupInfo.getDescription()
                    }
                }
            }

            conversation.isCommunityRecipient -> {
                community?.description
            }

            else -> null
        }

        // account ID
        val accountId = when{
            conversation.is1on1 || conversation.isLocalNumber -> conversation.address.toString()
            else -> null
        }

        // organise the setting options
        val optionData = when {
            conversation.isLocalNumber -> {
                val mainOptions = mutableListOf<OptionsItem>()
                val dangerOptions = mutableListOf<OptionsItem>()

                mainOptions.addAll(listOf(
                    optionCopyAccountId,
                    optionSearch,
                    optionDisappearingMessage,
                    optionPin, //todo UCS pin/unpin logic
                    optionAttachments,
                ))

                dangerOptions.addAll(listOf(
                    optionHideNTS,
                    optionClearMessages,
                ))

                listOf(
                    OptionsCategory(
                        items = listOf(
                            OptionsSubCategory(items = mainOptions),
                            OptionsSubCategory(
                                danger = true,
                                items = dangerOptions
                            )
                        )
                    )
                )
            }

            conversation.is1on1 -> {
                val mainOptions = mutableListOf<OptionsItem>()
                val dangerOptions = mutableListOf<OptionsItem>()

                mainOptions.addAll(listOf(
                    optionCopyAccountId,
                    optionSearch,
                    optionDisappearingMessage,
                    optionPin, //todo UCS pin/unpin logic
                    optionNotifications(null), //todo UCS notifications logic
                    optionAttachments,
                ))

                dangerOptions.addAll(listOf(
                    optionBlock,
                    optionClearMessages,
                    optionDeleteConversation,
                    optionDeleteContact
                ))

                listOf(
                    OptionsCategory(
                        items = listOf(
                            OptionsSubCategory(items = mainOptions),
                            OptionsSubCategory(
                                danger = true,
                                items = dangerOptions
                            )
                        )
                    )
                )
            }

            conversation.isGroupV2Recipient -> {
                val mainOptions = mutableListOf<OptionsItem>()
                val adminOptions = mutableListOf<OptionsItem>()
                val dangerOptions = mutableListOf<OptionsItem>()

                mainOptions.add(optionSearch)

                // for non admins, disappearing messages is in the non admin section
                if(!isAdmin){
                    mainOptions.add(optionDisappearingMessage)
                }

                mainOptions.addAll(listOf(
                    optionPin, //todo UCS pin/unpin logic
                    optionNotifications(null), //todo UCS notifications logic
                    optionGroupMembers,
                    optionAttachments,
                ))

                // apply different options depending on admin status
                if(isAdmin){
                    dangerOptions.addAll(
                        listOf(
                            optionClearMessages,
                            optionDeleteGroup
                        )
                    )

                    // admin options
                    adminOptions.addAll(listOf(
                        optionManageMembers,
                        optionDisappearingMessage
                    ))

                    // the returned options for group admins
                    listOf(
                        OptionsCategory(
                            items = listOf(
                                OptionsSubCategory(items = mainOptions),
                            )
                        ),
                        OptionsCategory(
                            name = context.getString(R.string.adminSettings),
                            items = listOf(
                                OptionsSubCategory(items = adminOptions),
                                OptionsSubCategory(
                                    danger = true,
                                    items = dangerOptions
                                )
                            )
                        )
                    )
                } else {
                    dangerOptions.addAll(
                        listOf(
                            optionClearMessages,
                            optionLeaveGroup
                        )
                    )

                    // the returned options for group non-admins
                    listOf(
                        OptionsCategory(
                            items = listOf(
                                OptionsSubCategory(items = mainOptions),
                                OptionsSubCategory(
                                    danger = true,
                                    items = dangerOptions
                                )
                            )
                        )
                    )
                }
            }

            conversation.isCommunityRecipient -> {
                val mainOptions = mutableListOf<OptionsItem>()
                val dangerOptions = mutableListOf<OptionsItem>()

                mainOptions.addAll(listOf(
                    optionCopyCommunityURL,
                    optionSearch,
                    optionPin, //todo UCS pin/unpin logic
                    optionNotifications(null), //todo UCS notifications logic
                    optionInviteMembers,
                    optionAttachments,
                ))

                dangerOptions.addAll(listOf(
                    optionClearMessages,
                    optionLeaveCommunity
                ))

                listOf(
                    OptionsCategory(
                        items = listOf(
                            OptionsSubCategory(items = mainOptions),
                            OptionsSubCategory(
                                danger = true,
                                items = dangerOptions
                            )
                        )
                    )
                )
            }

            //todo UCS handle groupsV2 and community

            else -> emptyList()
        }

        _uiState.update {
            _uiState.value.copy(
                name = conversation.takeUnless { it.isLocalNumber }?.name ?: context.getString(
                    R.string.noteToSelf),
                canEditName = canEditName,
                description = description,
                accountId = accountId,
                avatarUIData = avatarUtils.getUIDataFromRecipient(conversation),
                categories = optionData
            )
        }
    }

    private fun copyAccountId(){
        val accountID = recipient?.address?.toString() ?: ""
        val clip = ClipData.newPlainText("Account ID", accountID)
        val manager = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(clip)
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun isCommunityAdmin(): Boolean {
        if(community == null) return false
        else{
            val userPublicKey = textSecurePreferences.getLocalNumber() ?: return false
            val keyPair = storage.getUserED25519KeyPair() ?: return false
            val blindedPublicKey = community!!.publicKey.let { SodiumUtilities.blindedKeyPair(it, keyPair)?.publicKey?.asBytes }
                ?.let { AccountId(IdPrefix.BLINDED, it) }?.hexString
            return OpenGroupManager.isUserModerator(context, community!!.id, userPublicKey, blindedPublicKey)
        }
    }

    fun onCommand(command: Commands) {
        when (command) {
            is Commands.CopyAccountId -> copyAccountId()
        }
    }

    private fun navigateTo(destination: ConversationSettingsDestination){
        viewModelScope.launch {
            navigator.navigate(destination)
        }
    }

    sealed interface Commands {
        data object CopyAccountId : Commands
    }

    @AssistedFactory
    interface Factory {
        fun create(threadId: Long): ConversationSettingsViewModel
    }

    private val optionCopyAccountId: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.accountIDCopy),
            icon = R.drawable.ic_copy,
            qaTag = R.string.qa_conversation_settings_copy_account,
            onClick = ::copyAccountId
        )
    }

    private val optionSearch: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.searchConversation),
            icon = R.drawable.ic_search,
            qaTag = R.string.qa_conversation_settings_search,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    //todo UCS will the subtitle need to be dynamic in order to update when the option changes?
    private val optionDisappearingMessage: OptionsItem by lazy {
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

    private val optionPin: OptionsItem by lazy {
        OptionsItem(
            name = context.getString(R.string.pinConversation),
            icon = R.drawable.ic_pin,
            qaTag = R.string.qa_conversation_settings_pin,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    private val optionUnpin: OptionsItem by lazy {
        OptionsItem(
            name = context.getString(R.string.pinUnpinConversation),
            icon = R.drawable.ic_pin_off,
            qaTag = R.string.qa_conversation_settings_pin, //todo UCS check with emily if this needs its own
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    private fun optionNotifications(subtitle: String?): OptionsItem {
        return OptionsItem(
            name = context.getString(R.string.sessionNotifications),
            subtitle = subtitle,
            icon = R.drawable.ic_volume_2,
            qaTag = R.string.qa_conversation_settings_notifications,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    private val optionAttachments: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.attachments),
            icon = R.drawable.ic_file,
            qaTag = R.string.qa_conversation_settings_attachments,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    private val optionBlock: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.block),
            icon = R.drawable.ic_ban,
            qaTag = R.string.qa_conversation_settings_block,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    private val optionClearMessages: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.clearMessages),
            icon = R.drawable.ic_message_trash_custom,
            qaTag = R.string.qa_conversation_settings_clear_messages,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    private val optionDeleteConversation: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.conversationsDelete),
            icon = R.drawable.ic_trash_2,
            qaTag = R.string.qa_conversation_settings_delete_conversation,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    private val optionDeleteContact: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.contactDelete),
            icon = R.drawable.ic_user_round_trash,
            qaTag = R.string.qa_conversation_settings_delete_contact,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    private val optionHideNTS: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.noteToSelfHide),
            icon = R.drawable.ic_eye_off,
            qaTag = R.string.qa_conversation_settings_hide_nts,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    // Groups
    private val optionGroupMembers: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.groupMembers),
            icon = R.drawable.ic_users_round,
            qaTag = R.string.qa_conversation_settings_group_members,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    private val optionInviteMembers: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.membersInvite),
            icon = R.drawable.ic_user_round_plus,
            qaTag = R.string.qa_conversation_settings_invite_contacts,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    private val optionManageMembers: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.manageMembers),
            icon = R.drawable.ic_user_round_pen,
            qaTag = R.string.qa_conversation_settings_manage_members,
            onClick = {
                navigateTo(ConversationSettingsDestination.RouteManageMembers(
                    groupId = groupV2?.groupAccountId ?: "")
                )
            }
        )
    }

    private val optionLeaveGroup: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.groupLeave),
            icon = R.drawable.ic_log_out,
            qaTag = R.string.qa_conversation_settings_leave_group,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    private val optionDeleteGroup: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.groupDelete),
            icon = R.drawable.ic_trash_2,
            qaTag = R.string.qa_conversation_settings_delete_group,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    // Community
    private val optionCopyCommunityURL: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.communityUrlCopy),
            icon = R.drawable.ic_copy,
            qaTag = R.string.qa_conversation_settings_copy_community_url,
            onClick = ::copyAccountId //todo UCS get proper method
        )
    }

    private val optionLeaveCommunity: OptionsItem by lazy{
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
        val danger: Boolean = false,
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
