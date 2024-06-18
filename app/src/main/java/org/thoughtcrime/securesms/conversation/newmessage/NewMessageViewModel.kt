package org.thoughtcrime.securesms.conversation.newmessage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import network.loki.messenger.R
import org.session.libsession.snode.SnodeAPI
import org.session.libsignal.utilities.PublicKeyValidation
import org.thoughtcrime.securesms.ui.GetString
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
internal class NewMessageViewModel @Inject constructor(
    private val application: Application
): AndroidViewModel(application), Callbacks {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _success = MutableSharedFlow<Success>()
    val success get() = _success.asSharedFlow()

    private val _qrErrors = MutableSharedFlow<String>()
    val qrErrors = _qrErrors.asSharedFlow()

    private var loadOnsJob: Job? = null

    override fun onChange(value: String) {
        loadOnsJob?.cancel()
        loadOnsJob = null

        _state.update { State(newMessageIdOrOns = value) }
    }

    override fun onContinue() {
        val idOrONS = state.value.newMessageIdOrOns

        if (PublicKeyValidation.isValid(idOrONS, isPrefixRequired = false)) {
            onUnvalidatedPublicKey(idOrONS)
        } else {
            resolveONS(idOrONS)
        }
    }

    override fun onScanQrCode(value: String) {
        if (PublicKeyValidation.isValid(value, isPrefixRequired = false) && PublicKeyValidation.hasValidPrefix(value)) {
            onPublicKey(value)
        } else {
            _qrErrors.tryEmit(application.getString(R.string.this_qr_code_does_not_contain_an_account_id))
        }
    }

    private fun resolveONS(ons: String) {
        if (loadOnsJob?.isActive == true) return

        // This could be an ONS name
        _state.update { it.copy(error = null, loading = true) }

        loadOnsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val publicKey = withTimeout(30.seconds) { SnodeAPI.getSessionID(ons).get() }
                onPublicKey(publicKey)
            } catch (e: TimeoutCancellationException) {
                onError(e)
            } catch (e: CancellationException) {
                // Attempting to just ignore internal JobCancellationException, which is called
                // when we cancel the job, state update is handled there.
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    private fun onError(e: Exception) {
        _state.update { it.copy(loading = false, error = GetString(e) { it.toMessage() }) }
    }

    private fun onPublicKey(publicKey: String) {
        _state.update { it.copy(loading = false) }
        viewModelScope.launch { _success.emit(Success(publicKey)) }
    }

    private fun onUnvalidatedPublicKey(publicKey: String) {
        if (PublicKeyValidation.hasValidPrefix(publicKey)) {
            onPublicKey(publicKey)
        } else {
            _state.update { it.copy(error = GetString(R.string.accountIdErrorInvalid), loading = false) }
        }
    }

    private fun Exception.toMessage() = when (this) {
        is SnodeAPI.Error.Generic -> application.getString(R.string.onsErrorNotRecognized)
        else -> localizedMessage ?: application.getString(R.string.fragment_enter_public_key_error_message)
    }
}

internal data class State(
    val newMessageIdOrOns: String = "",
    val error: GetString? = null,
    val loading: Boolean = false
) {
    val isNextButtonEnabled: Boolean get() = newMessageIdOrOns.isNotBlank()
}

internal data class Success(val publicKey: String)
