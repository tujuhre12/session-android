package org.thoughtcrime.securesms.conversation.v2.settings

import android.content.Context
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
import org.session.libsession.database.StorageProtocol
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.UsernameUtils
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.repository.ConversationRepository
import org.thoughtcrime.securesms.util.AvatarUIData
import org.thoughtcrime.securesms.util.AvatarUtils


@HiltViewModel(assistedFactory = ConversationSettingsViewModel.Factory::class)
class ConversationSettingsViewModel @AssistedInject constructor(
    @Assisted private val threadId: Long,
    @ApplicationContext private val context: Context,
    private val avatarUtils: AvatarUtils,
    private val repository: ConversationRepository,
    private val configFactory: ConfigFactoryProtocol,
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
                canEditName = true, //todo UCS determine real value here << TEMP test only
                description = description,
                accountId = accountId,
                avatarUIData = avatarUtils.getUIDataFromRecipient(conversation)
            )
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(threadId: Long): ConversationSettingsViewModel
    }

    data class UIState(
        val avatarUIData: AvatarUIData,
        val name: String = "",
        val canEditName: Boolean = false,
        val description: String? = null,
        val accountId: String? = null,
    )
}
