package org.thoughtcrime.securesms.onboarding.loadaccount

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.crypto.MnemonicCodec.DecodingError.InputTooShort
import org.session.libsignal.crypto.MnemonicCodec.DecodingError.InvalidWord
import org.session.libsignal.utilities.Hex
import org.thoughtcrime.securesms.crypto.MnemonicUtilities
import javax.inject.Inject

class LoadAccountEvent(val mnemonic: ByteArray)

internal data class State(
    val recoveryPhrase: String = "",
    val error: String? = null
)

@HiltViewModel
internal class LinkDeviceViewModel @Inject constructor(
    private val application: Application
): AndroidViewModel(application) {
    private val state = MutableStateFlow(State())
    val stateFlow = state.asStateFlow()

    private val _events = MutableSharedFlow<LoadAccountEvent>()
    val events = _events.asSharedFlow()

    private val _qrErrors = MutableSharedFlow<Throwable>()
    val qrErrors = _qrErrors.asSharedFlow()
        .mapNotNull { application.getString(R.string.qrNotRecoveryPassword) }

    private val codec by lazy { MnemonicCodec { MnemonicUtilities.loadFileContents(getApplication(), it) } }

    fun onContinue() {
        viewModelScope.launch {
            runDecodeCatching(state.value.recoveryPhrase)
                .onSuccess(::onSuccess)
                .onFailure(::onFailure)
        }
    }

    fun onScanQrCode(string: String) {
        viewModelScope.launch {
            runDecodeCatching(string)
                .onSuccess(::onSuccess)
                .onFailure(::onQrCodeScanFailure)
        }
    }

    fun onChange(recoveryPhrase: String) {
        state.value = State(recoveryPhrase)
    }
    private fun onSuccess(seed: ByteArray) {
        viewModelScope.launch { _events.emit(LoadAccountEvent(seed)) }
    }

    private fun onFailure(error: Throwable) {
        state.update {
            it.copy(
                error = when (error) {
                    is InputTooShort -> R.string.recoveryPasswordErrorMessageShort
                    is InvalidWord -> R.string.recoveryPasswordErrorMessageIncorrect
                    else -> R.string.recoveryPasswordErrorMessageGeneric
                }.let(application::getString)
            )
        }
    }

    private fun onQrCodeScanFailure(error: Throwable) {
        viewModelScope.launch { _qrErrors.emit(error) }
    }

    private fun runDecodeCatching(mnemonic: String) = runCatching {
        decode(mnemonic)
    }
    private fun decode(mnemonic: String) = codec.decode(mnemonic).let(Hex::fromStringCondensed)!!
}
