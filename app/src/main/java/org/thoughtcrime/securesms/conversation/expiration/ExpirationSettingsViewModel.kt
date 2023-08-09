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
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.SSKEnvironment.MessageExpirationManagerProtocol
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.preferences.ExpirationRadioOption
import kotlin.reflect.KClass

class ExpirationSettingsViewModel(
    private val threadId: Long,
    private val afterReadOptions: List<ExpirationRadioOption>,
    private val afterSendOptions: List<ExpirationRadioOption>,
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

    private val _selectedExpirationType: MutableStateFlow<ExpiryMode> = MutableStateFlow(ExpiryMode.NONE)
    val selectedExpirationType: StateFlow<ExpiryMode> = _selectedExpirationType

    private val _selectedExpirationTimer = MutableStateFlow(afterSendOptions.firstOrNull())
    val selectedExpirationTimer: StateFlow<ExpirationRadioOption?> = _selectedExpirationTimer

    private val _expirationTimerOptions = MutableStateFlow<List<ExpirationRadioOption>>(emptyList())
    val expirationTimerOptions: StateFlow<List<ExpirationRadioOption>> = _expirationTimerOptions

    init {
        // SETUP
        viewModelScope.launch {
            expirationConfig = storage.getExpirationConfiguration(threadId)
            val expirationType = expirationConfig?.expiryMode
            val recipient = threadDb.getRecipientForThreadId(threadId)
            _recipient.value = recipient
            val groupInfo = if (recipient?.isClosedGroupRecipient == true) {
                groupDb.getGroup(recipient.address.toGroupString()).orNull()
            } else null
            _uiState.update { currentUiState ->
                currentUiState.copy(
                    isSelfAdmin = groupInfo == null || groupInfo.admins.any{ it.serialize() == textSecurePreferences.getLocalNumber() },
                    showExpirationTypeSelector = true
                )
            }
            _selectedExpirationType.value = if (ExpirationConfiguration.isNewConfigEnabled) {
                expirationType ?: ExpiryMode.NONE
            } else {
                if (expirationType != null && expirationType != ExpiryMode.NONE)
                    ExpiryMode.Legacy(expirationType.expirySeconds)
                else ExpiryMode.NONE
            }
            _selectedExpirationTimer.value = when(expirationType) {
                is ExpiryMode.AfterSend -> afterSendOptions.find { it.value == expirationType }
                is ExpiryMode.AfterRead -> afterReadOptions.find { it.value == expirationType }
                else -> afterSendOptions.firstOrNull()
            }
        }
        selectedExpirationType.mapLatest {
            when (it) {
                is ExpiryMode.Legacy, is ExpiryMode.AfterSend -> afterSendOptions
                is ExpiryMode.AfterRead -> afterReadOptions
                else -> emptyList()
            }
        }.onEach { options ->
            val enabled = _uiState.value.isSelfAdmin || recipient.value?.isClosedGroupRecipient == true
            _expirationTimerOptions.value = if (ExpirationConfiguration.isNewConfigEnabled && (recipient.value?.isLocalNumber == true || recipient.value?.isClosedGroupRecipient == true)) {
                options.map { it.copy(enabled = enabled) }
            } else {
                options.slice(1 until options.size).map { it.copy(enabled = enabled) }
            }
        }.launchIn(viewModelScope)
    }

    fun onExpirationTypeSelected(option: ExpirationRadioOption) {
        _selectedExpirationType.value = option.value
        _selectedExpirationTimer.value = _expirationTimerOptions.value.firstOrNull()
    }

    fun onExpirationTimerSelected(option: ExpirationRadioOption) {
        _selectedExpirationTimer.value = option
    }

    private fun KClass<out ExpiryMode>?.withTime(expirationTimer: Long) = when(this) {
        ExpiryMode.AfterRead::class -> ExpiryMode.AfterRead(expirationTimer)
        ExpiryMode.AfterSend::class -> ExpiryMode.AfterSend(expirationTimer)
        else -> ExpiryMode.NONE
    }

    fun onSetClick() = viewModelScope.launch {
        val expiryMode = _selectedExpirationTimer.value?.value ?: ExpiryMode.NONE
        val address = recipient.value?.address
        if (address == null || (expirationConfig?.expiryMode != expiryMode)) {
            _uiState.update {
                it.copy(settingsSaved = false)
            }
            return@launch
        }

        val expiryChangeTimestampMs = SnodeAPI.nowWithOffset
        storage.setExpirationConfiguration(ExpirationConfiguration(threadId, expiryMode, expiryChangeTimestampMs))

        val message = ExpirationTimerUpdate(expiryMode.expirySeconds.toInt())
        message.sender = textSecurePreferences.getLocalNumber()
        message.recipient = address.serialize()
        message.sentTimestamp = expiryChangeTimestampMs
        messageExpirationManager.setExpirationTimer(message, expiryMode)

        MessageSender.send(message, address)
        _uiState.update {
            it.copy(settingsSaved = true)
        }
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(
            threadId: Long,
            @Assisted("afterRead") afterReadOptions: List<ExpirationRadioOption>,
            @Assisted("afterSend") afterSendOptions: List<ExpirationRadioOption>
        ): Factory
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @AssistedInject constructor(
        @Assisted private val threadId: Long,
        @Assisted("afterRead") private val afterReadOptions: List<ExpirationRadioOption>,
        @Assisted("afterSend") private val afterSendOptions: List<ExpirationRadioOption>,
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
