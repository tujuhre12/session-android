package org.thoughtcrime.securesms.onboarding

import android.app.Application
import androidx.compose.runtime.snapshots.SnapshotApplyResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.utilities.Hex
import org.thoughtcrime.securesms.crypto.MnemonicUtilities
import javax.inject.Inject

class LinkDeviceEvent(val mnemonic: ByteArray)

@HiltViewModel
class LinkDeviceViewModel @Inject constructor(
    application: Application
): AndroidViewModel(application) {
    private val state = MutableStateFlow(LinkDeviceState())
    val stateFlow = state.asStateFlow()

    private val qrErrors = Channel<Throwable>()
    private val event = Channel<LinkDeviceEvent>()
    val eventFlow = event.receiveAsFlow()

    private val phrases = Channel<String>()

    init {
        viewModelScope.launch {
            phrases.receiveAsFlow().map {
                runCatching {
                    MnemonicCodec { MnemonicUtilities.loadFileContents(getApplication(), it) }
                        .decode(it)
                        .let(Hex::fromStringCondensed)
                }
            }.takeWhile {
                it.getOrNull()?.let(::LinkDeviceEvent)?.let { event.send(it) }
                it.exceptionOrNull()?.let { qrErrors.send(it) }
                it.isFailure
            }.collect()
        }
    }

    fun tryPhrase(string: String = state.value.recoveryPhrase) {
        viewModelScope.launch {
            phrases.send(string)
        }
    }

    fun onChange(recoveryPhrase: String) {
        state.value = LinkDeviceState(recoveryPhrase)
    }
}
