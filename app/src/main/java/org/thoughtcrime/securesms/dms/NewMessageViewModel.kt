package org.thoughtcrime.securesms.dms

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.loki.messenger.R
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.session.libsession.snode.SnodeAPI
import org.session.libsignal.utilities.PublicKeyValidation
import org.thoughtcrime.securesms.ui.GetString
import javax.inject.Inject

@HiltViewModel
class NewMessageViewModel @Inject constructor(
    private val application: Application
): AndroidViewModel(application), Callbacks {

    private val _state = MutableStateFlow(
        State()
    )
    val state = _state.asStateFlow()

    private val _event = Channel<Event>()
    val event = _event.receiveAsFlow()

    override fun onChange(value: String) {
        _state.update { it.copy(
            newMessageIdOrOns = value,
            error = null
        ) }
    }
    override fun onContinue() {
        createPrivateChatIfPossible(state.value.newMessageIdOrOns)
    }

    override fun onScan(value: String) {
        createPrivateChatIfPossible(value)
    }

    private fun createPrivateChatIfPossible(onsNameOrPublicKey: String) {
        if (PublicKeyValidation.isValid(onsNameOrPublicKey, isPrefixRequired = false)) {
            if (PublicKeyValidation.hasValidPrefix(onsNameOrPublicKey)) {
                onPublicKey(onsNameOrPublicKey)
            } else {
                _state.update { it.copy(error = GetString(R.string.accountIdErrorInvalid), loading = false) }
            }
        } else {
            // This could be an ONS name
            _state.update { it.copy(error = null, loading = true) }

            SnodeAPI.getSessionID(onsNameOrPublicKey).successUi { hexEncodedPublicKey ->
                _state.update { it.copy(loading = false) }
                onPublicKey(onsNameOrPublicKey)
            }.failUi { exception ->
                _state.update { it.copy(loading = false, error = GetString(exception) { it.toMessage() }) }
            }
        }
    }

    private fun onPublicKey(onsNameOrPublicKey: String) {
        viewModelScope.launch { _event.send(Event.Success(onsNameOrPublicKey)) }
    }

    private fun Exception.toMessage() = when (this) {
        is SnodeAPI.Error.Generic -> application.getString(R.string.onsErrorNotRecognized)
        else -> localizedMessage ?: application.getString(R.string.fragment_enter_public_key_error_message)
    }
}

data class State(
    val newMessageIdOrOns: String = "",
    val error: GetString? = null,
    val loading: Boolean = false
)

sealed interface Event {
    data class Success(val key: String): Event
}
