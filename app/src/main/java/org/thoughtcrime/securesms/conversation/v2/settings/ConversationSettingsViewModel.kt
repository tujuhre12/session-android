package org.thoughtcrime.securesms.conversation.v2.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity.CLIPBOARD_SERVICE
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.copper.flow.observeQuery
import com.bumptech.glide.Glide
import com.squareup.phrase.Phrase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_HIDDEN
import network.loki.messenger.libsession_util.ConfigBase.Companion.PRIORITY_VISIBLE
import network.loki.messenger.libsession_util.util.ExpiryMode
import network.loki.messenger.libsession_util.util.GroupInfo
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.groups.GroupManagerV2
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.TIME_KEY
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.getGroup
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.IdPrefix
import org.thoughtcrime.securesms.database.DatabaseContentProviders
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.home.HomeActivity
import org.thoughtcrime.securesms.repository.ConversationRepository
import org.thoughtcrime.securesms.ui.getSubbedString
import org.thoughtcrime.securesms.util.AvatarUIData
import org.thoughtcrime.securesms.util.AvatarUtils
import org.thoughtcrime.securesms.util.avatarOptions
import org.thoughtcrime.securesms.util.observeChanges
import kotlin.math.min


@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ConversationSettingsViewModel.Factory::class)
class ConversationSettingsViewModel @AssistedInject constructor(
    @Assisted private val threadId: Long,
    @ApplicationContext private val context: Context,
    private val avatarUtils: AvatarUtils,
    private val repository: ConversationRepository,
    private val configFactory: ConfigFactoryProtocol,
    private val storage: StorageProtocol,
    private val textSecurePreferences: TextSecurePreferences,
    private val navigator: ConversationSettingsNavigator,
    private val threadDb: ThreadDatabase,
    private val groupManagerV2: GroupManagerV2,
    private val prefs: TextSecurePreferences,
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
        // update data when we have a recipient and update when there are changes from the thread or recipient
        viewModelScope.launch(Dispatchers.Default) {
            repository.recipientUpdateFlow(threadId) // get the recipient
                .flatMapLatest { recipient -> // get updates from the thread or recipient
                    merge(
                        context.contentResolver
                            .observeQuery(DatabaseContentProviders.Recipient.CONTENT_URI), // recipient updates
                        (context.contentResolver.observeChanges(
                            DatabaseContentProviders.Conversation.getUriForThread(threadId)
                        ) as Flow<*>) // thread updates
                    ).map {
                        recipient // return the recipient
                    }
                        .debounce(200L)
                        .onStart { emit(recipient) } // make sure there's a value straight away
                }
                .collect {
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

        // description / display name with QA tags
        val (description: String?, descriptionQaTag: String?) = when{
            // for 1on1, if the user has a nickname it should be displayed as the
            // main name, and the description should show the real name in parentheses
            conversation.is1on1 -> {
                if(configContact?.nickname?.isNotEmpty() == true && configContact.name.isNotEmpty()) {
                    (
                        "(${configContact.name})" to // description
                        context.getString(R.string.qa_conversation_settings_description_1on1) // description qa tag
                    )
                } else (null to null)
            }

            conversation.isGroupV2Recipient -> {
                if(groupV2 == null) (null to null)
                else {
                    (
                        configFactory.withGroupConfigs(AccountId(groupV2!!.groupAccountId)){
                            it.groupInfo.getDescription()
                        } to // description
                        context.getString(R.string.qa_conversation_settings_description_groups) // description qa tag
                    )
                }
            }

            conversation.isCommunityRecipient -> { //todo UCS currently this property is null for existing communities and is never updated if the community was already added before caring for the description
                (
                    community?.description to // description
                    context.getString(R.string.qa_conversation_settings_description_community) // description qa tag
                )
            }

            else -> (null to null)
        }

        // account ID
        val accountId = when{
            conversation.is1on1 || conversation.isLocalNumber -> conversation.address.toString()
            else -> null
        }

        // disappearing message type
        val expiration = storage.getExpirationConfiguration(threadId)
        val disappearingSubtitle = if(expiration?.isEnabled == true) {
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

        val pinned = threadDb.isPinned(threadId)

        // organise the setting options
        val optionData = when {
            conversation.isLocalNumber -> {
                val mainOptions = mutableListOf<OptionsItem>()
                val dangerOptions = mutableListOf<OptionsItem>()

                val ntsHidden = prefs.hasHiddenNoteToSelf()

                mainOptions.addAll(listOf(
                    optionCopyAccountId,
                    optionSearch,
                    optionDisappearingMessage(disappearingSubtitle),
                    if(pinned) optionUnpin else optionPin,
                    optionAttachments,
                ))

                if(ntsHidden) mainOptions.add(optionShowNTS)
                else dangerOptions.add(optionHideNTS)

                dangerOptions.addAll(listOf(
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
                    optionDisappearingMessage(disappearingSubtitle),
                    if(pinned) optionUnpin else optionPin,
                    optionNotifications(null), //todo UCS notifications logic
                    optionAttachments,
                ))

                dangerOptions.addAll(listOf(
                    if(recipient?.isBlocked == true) optionUnblock else optionBlock,
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
                    mainOptions.add(optionDisappearingMessage(disappearingSubtitle))
                }

                mainOptions.addAll(listOf(
                    if(pinned) optionUnpin else optionPin,
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
                        optionDisappearingMessage(disappearingSubtitle)
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
                    if(pinned) optionUnpin else optionPin,
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

            else -> emptyList()
        }

        val avatarData = avatarUtils.getUIDataFromRecipient(conversation)
        _uiState.update {
            _uiState.value.copy(
                name = conversation.takeUnless { it.isLocalNumber }?.name ?: context.getString(
                    R.string.noteToSelf),
                nameQaTag = when {
                    conversation.isLocalNumber -> context.getString(R.string.qa_conversation_settings_display_name_nts)
                    conversation.is1on1 -> context.getString(R.string.qa_conversation_settings_display_name_1on1)
                    conversation.isGroupV2Recipient -> context.getString(R.string.qa_conversation_settings_display_name_groups)
                    conversation.isCommunityRecipient -> context.getString(R.string.qa_conversation_settings_display_name_community)
                    else -> null
                },
                canEditName = canEditName,
                description = description,
                descriptionQaTag = descriptionQaTag,
                accountId = accountId,
                avatarUIData = avatarData,
                categories = optionData
            )
        }

        // also preload the larger version of the avatar in case the user goes to the fullscreen avatar
        avatarData.elements.mapNotNull { it.contactPhoto }.forEach {
            val  loadSize = min(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
            Glide.with(context).load(it)
                .avatarOptions(loadSize)
                .preload(loadSize, loadSize)
        }
    }

    private fun copyAccountId(){
        val accountID = recipient?.address?.toString() ?: ""
        val clip = ClipData.newPlainText("Account ID", accountID)
        val manager = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(clip)
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun copyCommunityUrl(){
        val url = community?.joinURL ?: return
        val clip = ClipData.newPlainText(context.getString(R.string.communityUrl), url)
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

    private fun pinConversation(){
        viewModelScope.launch {
            storage.setPinned(threadId, true)
        }
    }

    private fun unpinConversation(){
        viewModelScope.launch {
            storage.setPinned(threadId, false)
        }
    }

    private fun confirmBlockUser(){
        _uiState.update {
            it.copy(
                showSimpleDialog = Dialog(
                    title = context.getString(R.string.block),
                    message = Phrase.from(context, R.string.blockDescription)
                        .put(NAME_KEY, recipient?.name ?: "")
                        .format(),
                    positiveText = context.getString(R.string.block),
                    negativeText = context.getString(R.string.cancel),
                    positiveQaTag = context.getString(R.string.qa_conversation_settings_dialog_block_confirm),
                    negativeQaTag = context.getString(R.string.qa_conversation_settings_dialog_block_cancel),
                    onPositive = ::blockUser,
                    onNegative = {}
                )
            )
        }
    }

    private fun confirmUnblockUser(){
        _uiState.update {
            it.copy(
                showSimpleDialog = Dialog(
                    title = context.getString(R.string.blockUnblock),
                    message = Phrase.from(context, R.string.blockUnblockName)
                        .put(NAME_KEY, recipient?.name ?: "")
                        .format(),
                    positiveText = context.getString(R.string.blockUnblock),
                    negativeText = context.getString(R.string.cancel),
                    positiveQaTag = context.getString(R.string.qa_conversation_settings_dialog_unblock_confirm),
                    negativeQaTag = context.getString(R.string.qa_conversation_settings_dialog_unblock_cancel),
                    onPositive = ::unblockUser,
                    onNegative = {}
                )
            )
        }
    }

    private fun blockUser() {
        val conversation = recipient ?: return
        viewModelScope.launch {
            if (conversation.isContactRecipient || conversation.isGroupV2Recipient) {
                repository.setBlocked(conversation, true)
            }

            if (conversation.isGroupV2Recipient) {
                groupManagerV2.onBlocked(AccountId(conversation.address.toString()))
            }
        }
    }

    private fun unblockUser() {
        if(recipient == null) return
        viewModelScope.launch {
            repository.setBlocked(recipient!!, false)
        }
    }

    private fun confirmHideNTS(){
        _uiState.update {
            it.copy(
                showSimpleDialog = Dialog(
                    title = context.getString(R.string.noteToSelfHide),
                    message = context.getText(R.string.hideNoteToSelfDescription),
                    positiveText = context.getString(R.string.hide),
                    negativeText = context.getString(R.string.cancel),
                    positiveQaTag = context.getString(R.string.qa_conversation_settings_dialog_hide_nts_confirm),
                    negativeQaTag = context.getString(R.string.qa_conversation_settings_dialog_hide_nts_cancel),
                    onPositive = ::hideNoteToSelf,
                    onNegative = {}
                )
            )
        }
    }

    private fun confirmShowNTS(){
        _uiState.update {
            it.copy(
                showSimpleDialog = Dialog(
                    title = context.getString(R.string.showNoteToSelf),
                    message = context.getText(R.string.showNoteToSelfDescription),
                    positiveText = context.getString(R.string.show),
                    negativeText = context.getString(R.string.cancel),
                    positiveQaTag = context.getString(R.string.qa_conversation_settings_dialog_show_nts_confirm),
                    negativeQaTag = context.getString(R.string.qa_conversation_settings_dialog_show_nts_cancel),
                    positiveStyleDanger = false,
                    onPositive = ::showNoteToSelf,
                    onNegative = {}
                )
            )
        }
    }

    private fun hideNoteToSelf() {
        prefs.setHasHiddenNoteToSelf(true)
        configFactory.withMutableUserConfigs {
            it.userProfile.setNtsPriority(PRIORITY_HIDDEN)
        }
        // update state to reflect the change
        viewModelScope.launch {
            getStateFromRecipient()
        }
    }

    fun showNoteToSelf() {
        prefs.setHasHiddenNoteToSelf(false)
        configFactory.withMutableUserConfigs {
            it.userProfile.setNtsPriority(PRIORITY_VISIBLE)
        }
        // update state to reflect the change
        viewModelScope.launch {
            getStateFromRecipient()
        }
    }

    private fun confirmDeleteContact(){
        _uiState.update {
            it.copy(
                showSimpleDialog = Dialog(
                    title = context.getString(R.string.contactDelete),
                    message = Phrase.from(context, R.string.deleteContactDescription)
                        .put(NAME_KEY, recipient?.name ?: "")
                        .put(NAME_KEY, recipient?.name ?: "")
                        .format(),
                    positiveText = context.getString(R.string.delete),
                    negativeText = context.getString(R.string.cancel),
                    positiveQaTag = context.getString(R.string.qa_conversation_settings_dialog_delete_contact_confirm),
                    negativeQaTag = context.getString(R.string.qa_conversation_settings_dialog_delete_contact_cancel),
                    onPositive = ::deleteContact,
                    onNegative = {}
                )
            )
        }
    }

    private fun deleteContact() {
        val conversation = recipient ?: return
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                storage.deleteContactAndSyncConfig(conversation.address.toString())
            }

            goBackHome()
        }
    }

    private fun confirmDeleteConversation(){
        _uiState.update {
            it.copy(
                showSimpleDialog = Dialog(
                    title = context.getString(R.string.conversationsDelete),
                    message = Phrase.from(context, R.string.deleteConversationDescription)
                        .put(NAME_KEY, recipient?.name ?: "")
                        .format(),
                    positiveText = context.getString(R.string.delete),
                    negativeText = context.getString(R.string.cancel),
                    positiveQaTag = context.getString(R.string.qa_conversation_settings_dialog_delete_conversation_confirm),
                    negativeQaTag = context.getString(R.string.qa_conversation_settings_dialog_delete_conversation_cancel),
                    onPositive = ::deleteConversation,
                    onNegative = {}
                )
            )
        }
    }

    private fun deleteConversation() {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                storage.deleteConversation(threadId)
            }

            goBackHome()
        }
    }

    private suspend fun goBackHome(){
        navigator.navigateToIntent(
            Intent(context, HomeActivity::class.java).apply {
                // pop back to home activity
                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
        )
    }

    fun onCommand(command: Commands) {
        when (command) {
            is Commands.CopyAccountId -> copyAccountId()

            is Commands.HideSimpleDialog -> _uiState.update {
                it.copy(showSimpleDialog = null)
            }
        }
    }

    private fun navigateTo(destination: ConversationSettingsDestination){
        viewModelScope.launch {
            navigator.navigate(destination)
        }
    }

    sealed interface Commands {
        data object CopyAccountId : Commands
        data object HideSimpleDialog : Commands
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


    private fun optionDisappearingMessage(subtitle: String?): OptionsItem {
        return OptionsItem(
            name = context.getString(R.string.disappearingMessages),
            subtitle = subtitle,
            icon = R.drawable.ic_timer,
            qaTag = R.string.qa_conversation_settings_disappearing,
            onClick = {
                navigateTo(ConversationSettingsDestination.RouteDisappearingMessages)
            }
        )
    }

    private val optionPin: OptionsItem by lazy {
        OptionsItem(
            name = context.getString(R.string.pinConversation),
            icon = R.drawable.ic_pin,
            qaTag = R.string.qa_conversation_settings_pin,
            onClick = ::pinConversation
        )
    }

    private val optionUnpin: OptionsItem by lazy {
        OptionsItem(
            name = context.getString(R.string.pinUnpinConversation),
            icon = R.drawable.ic_pin_off,
            qaTag = R.string.qa_conversation_settings_pin,
            onClick = ::unpinConversation
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
            onClick = {
                navigateTo(ConversationSettingsDestination.RouteAllMedia)
            }
        )
    }

    private val optionBlock: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.block),
            icon = R.drawable.ic_user_round_x,
            qaTag = R.string.qa_conversation_settings_block,
            onClick = ::confirmBlockUser
        )
    }

    private val optionUnblock: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.blockUnblock),
            icon = R.drawable.ic_user_round_tick,
            qaTag = R.string.qa_conversation_settings_block,
            onClick = ::confirmUnblockUser
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
            onClick = ::confirmDeleteConversation
        )
    }

    private val optionDeleteContact: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.contactDelete),
            icon = R.drawable.ic_user_round_trash,
            qaTag = R.string.qa_conversation_settings_delete_contact,
            onClick = ::confirmDeleteContact
        )
    }

    private val optionHideNTS: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.noteToSelfHide),
            icon = R.drawable.ic_eye_off,
            qaTag = R.string.qa_conversation_settings_hide_nts,
            onClick = ::confirmHideNTS
        )
    }

    private val optionShowNTS: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.showNoteToSelf),
            icon = R.drawable.ic_eye,
            qaTag = R.string.qa_conversation_settings_hide_nts,
            onClick = ::confirmShowNTS
        )
    }

    // Groups
    private val optionGroupMembers: OptionsItem by lazy{
        OptionsItem(
            name = context.getString(R.string.groupMembers),
            icon = R.drawable.ic_users_round,
            qaTag = R.string.qa_conversation_settings_group_members,
            onClick = {
                navigateTo(ConversationSettingsDestination.RouteGroupMembers(
                    groupId = groupV2?.groupAccountId ?: "")
                )
            }
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
            onClick = ::copyCommunityUrl
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
        val nameQaTag: String? = null,
        val canEditName: Boolean = false,
        val description: String? = null,
        val descriptionQaTag: String? = null,
        val accountId: String? = null,
        val showSimpleDialog: Dialog? = null,
        val categories: List<OptionsCategory> = emptyList()
    )

    /**
     * Data to display a simple dialog
     */
    data class Dialog(
        val title: String,
        val message: CharSequence,
        val positiveText: String,
        val positiveStyleDanger: Boolean = true,
        val negativeText: String,
        val positiveQaTag: String?,
        val negativeQaTag: String?,
        val onPositive: () -> Unit,
        val onNegative: () -> Unit
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
