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
import kotlinx.coroutines.launch
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.protos.SignalServiceProtos.Content.ExpirationType
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.preferences.RadioOption

class ExpirationSettingsViewModel(
    private val threadId: Long,
    private val afterReadOptions: List<RadioOption>,
    private val afterSendOptions: List<RadioOption>,
    private val threadDb: ThreadDatabase,
    private val storage: Storage
) : ViewModel() {

    var showExpirationTypeSelector: Boolean = false
        private set

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
            showExpirationTypeSelector = !ExpirationConfiguration.isNewConfigEnabled || (recipient?.isContactRecipient == true && !recipient.isLocalNumber)
            if (ExpirationConfiguration.isNewConfigEnabled && (recipient?.isLocalNumber == true || recipient?.isClosedGroupRecipient == true)) {
                _selectedExpirationType.value = ExpirationType.DELETE_AFTER_SEND.number
            } else {
                _selectedExpirationType.value = expirationConfig?.expirationTypeValue ?: -1
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
                options
            } else {
                options.slice(1 until options.size)
            }
        }.launchIn(viewModelScope)
    }

    fun onExpirationTypeSelected(option: RadioOption) {
        _selectedExpirationType.value = option.value.toIntOrNull() ?: -1
    }

    fun onExpirationTimerSelected(option: RadioOption) {
        _selectedExpirationTimer.value = option
    }

    fun onSetClick() = viewModelScope.launch {
        val expiresIn = _selectedExpirationTimer.value?.value?.toIntOrNull() ?: 0
        val expiryType = _selectedExpirationType.value
        val expiryChangeTimestampMs = System.currentTimeMillis()
        storage.setExpirationConfiguration(ExpirationConfiguration(threadId, expiresIn, expiryType, expiryChangeTimestampMs))

        val message = ExpirationTimerUpdate(expiresIn)
        val address = recipient.value?.address ?: return@launch
        message.recipient = address.serialize()
        message.sentTimestamp = System.currentTimeMillis()
        MessageSender.send(message, address)
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
        private val threadDb: ThreadDatabase,
        private val storage: Storage
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ExpirationSettingsViewModel(
                threadId,
                afterReadOptions,
                afterSendOptions,
                threadDb,
                storage
            ) as T
        }
    }
}