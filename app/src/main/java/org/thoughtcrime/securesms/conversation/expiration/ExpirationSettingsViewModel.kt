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
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.protos.SignalServiceProtos.Content.ExpirationType
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.preferences.RadioOption

class ExpirationSettingsViewModel(
    private val threadId: Long,
    private val expirationType: ExpirationType?,
    private val afterReadOptions: List<RadioOption>,
    private val afterSendOptions: List<RadioOption>,
    private val threadDb: ThreadDatabase
) : ViewModel() {

    val recipient: Recipient?
        get() = threadDb.getRecipientForThreadId(threadId)

    private val _selectedExpirationType = MutableStateFlow(expirationType)
    val selectedExpirationType: StateFlow<ExpirationType?> = _selectedExpirationType

    private val _expirationTimerOptions = MutableStateFlow<List<RadioOption>>(emptyList())
    val expirationTimerOptions: StateFlow<List<RadioOption>> = _expirationTimerOptions

    init {
        selectedExpirationType.mapLatest {
            when (it) {
                ExpirationType.DELETE_AFTER_SEND -> afterSendOptions
                ExpirationType.DELETE_AFTER_READ -> afterReadOptions
                else -> emptyList()
            }
        }.onEach {
            _expirationTimerOptions.value = it
        }.launchIn(viewModelScope)
    }

    fun onExpirationTypeSelected(option: RadioOption) {
        _selectedExpirationType.value = option.value.toIntOrNull()?.let { ExpirationType.valueOf(it) }
    }

    fun onExpirationTimerSelected(option: RadioOption) {

    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(
            threadId: Long,
            expirationType: ExpirationType?,
            @Assisted("afterRead") afterReadOptions: List<RadioOption>,
            @Assisted("afterSend") afterSendOptions: List<RadioOption>
        ): Factory
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @AssistedInject constructor(
        @Assisted private val threadId: Long,
        @Assisted private val expirationType: ExpirationType?,
        @Assisted("afterRead") private val afterReadOptions: List<RadioOption>,
        @Assisted("afterSend") private val afterSendOptions: List<RadioOption>,
        private val threadDb: ThreadDatabase
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ExpirationSettingsViewModel(
                threadId,
                expirationType,
                afterReadOptions,
                afterSendOptions,
                threadDb
            ) as T
        }
    }
}