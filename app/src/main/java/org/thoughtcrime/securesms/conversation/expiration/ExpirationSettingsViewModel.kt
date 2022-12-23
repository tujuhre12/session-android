package org.thoughtcrime.securesms.conversation.expiration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.SSKEnvironment.MessageExpirationManagerProtocol
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.protos.SignalServiceProtos.Content.ExpirationType
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.preferences.RadioOption

class ExpirationSettingsViewModel(
    private val threadId: Long,
    private val afterReadOptions: List<RadioOption>,
    private val afterSendOptions: List<RadioOption>,
    private val textSecurePreferences: TextSecurePreferences,
    private val messageExpirationManager: MessageExpirationManagerProtocol,
    private val threadDb: ThreadDatabase,
    private val groupDb: GroupDatabase,
    private val storage: Storage
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpirationSettingsUiState())
    val uiState: StateFlow<ExpirationSettingsUiState> = _uiState

    private var expirationConfig: ExpirationConfiguration? = null

    private val _recipient = MutableStateFlow<Recipient?>(null)
    val recipient: StateFlow<Recipient?> = _recipient

    private val _selectedExpirationType = MutableStateFlow(-1)
    val selectedExpirationType: StateFlow<Int> = _selectedExpirationType

    private val _selectedExpirationTimer = MutableStateFlow(afterSendOptions.firstOrNull())
    val selectedExpirationTimer: StateFlow<RadioOption?> = _selectedExpirationTimer

    private val _expirationTimerOptions = MutableStateFlow<List<RadioOption>>(emptyList())
    val expirationTimerOptions: StateFlow<List<RadioOption>> = _expirationTimerOptions

    init {
        viewModelScope.launch {
            expirationConfig = storage.getExpirationConfiguration(threadId)
            val recipient = threadDb.getRecipientForThreadId(threadId)
            _recipient.value = recipient
            val groupInfo = if (recipient?.isClosedGroupRecipient == true) {
                groupDb.getGroup(recipient.address.toGroupString()).orNull()
            } else null
            _uiState.update { currentUiState ->
                currentUiState.copy(
                    isSelfAdmin = groupInfo == null || groupInfo.admins.any{ it.serialize() == textSecurePreferences.getLocalNumber() },
                    showExpirationTypeSelector = !ExpirationConfiguration.isNewConfigEnabled || (recipient?.isContactRecipient == true && !recipient.isLocalNumber)
                )
            }
            _selectedExpirationType.value = if (ExpirationConfiguration.isNewConfigEnabled) {
                if (recipient?.isLocalNumber == true || recipient?.isClosedGroupRecipient == true) {
                    ExpirationType.DELETE_AFTER_SEND.number
                } else {
                    expirationConfig?.expirationTypeValue ?: -1
                }
            } else {
                expirationConfig?.expirationTypeValue?.let { 0 /* Legacy */ } ?: -1
            }
            _selectedExpirationTimer.value = when(expirationConfig?.expirationType) {
                ExpirationType.DELETE_AFTER_SEND -> afterSendOptions.find { it.value.toIntOrNull() == expirationConfig?.durationSeconds }
                ExpirationType.DELETE_AFTER_READ -> afterReadOptions.find { it.value.toIntOrNull() == expirationConfig?.durationSeconds }
                else -> afterSendOptions.firstOrNull()
            }
        }
        selectedExpirationType.mapLatest {
            when (it) {
                0, ExpirationType.DELETE_AFTER_SEND.number -> afterSendOptions
                ExpirationType.DELETE_AFTER_READ.number -> afterReadOptions
                else -> emptyList()
            }
        }.onEach { options ->
            _expirationTimerOptions.value = if (ExpirationConfiguration.isNewConfigEnabled && (recipient.value?.isLocalNumber == true || recipient.value?.isClosedGroupRecipient == true)) {
                options.map { it.copy(enabled = _uiState.value.isSelfAdmin) }
            } else {
                options.slice(1 until options.size).map { it.copy(enabled = _uiState.value.isSelfAdmin) }
            }
        }.launchIn(viewModelScope)
    }

    fun onExpirationTypeSelected(option: RadioOption) {
        _selectedExpirationType.value = option.value.toIntOrNull() ?: -1
        _selectedExpirationTimer.value = _expirationTimerOptions.value.firstOrNull()
    }

    fun onExpirationTimerSelected(option: RadioOption) {
        _selectedExpirationTimer.value = option
    }

    fun onSetClick() = viewModelScope.launch {
        val expiryType = _selectedExpirationType.value
        val expirationTimer = _selectedExpirationTimer.value?.value?.toIntOrNull() ?: 0
        val address = recipient.value?.address
        if (address == null || (expirationConfig?.expirationTypeValue == expiryType && expirationConfig?.durationSeconds == expirationTimer)) {
            _uiState.update {
                it.copy(settingsSaved = false)
            }
            return@launch
        }

        val expiryChangeTimestampMs = System.currentTimeMillis() + SnodeAPI.clockOffset
        storage.setExpirationConfiguration(ExpirationConfiguration(threadId, expirationTimer, expiryType, expiryChangeTimestampMs))

        val message = ExpirationTimerUpdate(expirationTimer)
        message.sender = textSecurePreferences.getLocalNumber()
        message.recipient = address.serialize()
        message.sentTimestamp = expiryChangeTimestampMs
        messageExpirationManager.setExpirationTimer(message, expiryType)

        MessageSender.send(message, address)
        _uiState.update {
            it.copy(settingsSaved = true)
        }
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(
            threadId: Long,
            @Assisted("afterRead") afterReadOptions: List<RadioOption>,
            @Assisted("afterSend") afterSendOptions: List<RadioOption>
        ): Factory
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @AssistedInject constructor(
        @Assisted private val threadId: Long,
        @Assisted("afterRead") private val afterReadOptions: List<RadioOption>,
        @Assisted("afterSend") private val afterSendOptions: List<RadioOption>,
        private val textSecurePreferences: TextSecurePreferences,
        private val messageExpirationManager: MessageExpirationManagerProtocol,
        private val threadDb: ThreadDatabase,
        private val groupDb: GroupDatabase,
        private val storage: Storage
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ExpirationSettingsViewModel(
                threadId,
                afterReadOptions,
                afterSendOptions,
                textSecurePreferences,
                messageExpirationManager,
                threadDb,
                groupDb,
                storage
            ) as T
        }
    }
}

data class ExpirationSettingsUiState(
    val isSelfAdmin: Boolean = false,
    val showExpirationTypeSelector: Boolean = false,
    val settingsSaved: Boolean? = null
)
