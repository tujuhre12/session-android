package org.thoughtcrime.securesms.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val event = Channel<LinkDeviceEvent>()
    val eventFlow = event.receiveAsFlow()

    fun onRecoveryPhrase() {
        val mnemonic = state.value.recoveryPhrase

        viewModelScope.launch(Dispatchers.IO) {
            try {
                MnemonicCodec { MnemonicUtilities.loadFileContents(getApplication(), it) }
                    .decode(mnemonic)
                    .let(Hex::fromStringCondensed)
                    .let(::LinkDeviceEvent)
                    .let { event.send(it) }
            } catch (exception: Exception) {
                when (exception) {
                    is MnemonicCodec.DecodingError -> exception.description
                    else -> "An error occurred."
                }.let { error -> state.update { it.copy(error = error) } }
            }
        }
    }

//    override fun handleQRCodeScanned(mnemonic: String) {
//        try {
//            val seed = Hex.fromStringCondensed(mnemonic)
//            continueWithSeed(seed)
//        } catch (e: Exception) {
//            Log.e("Loki","Error getting seed from QR code", e)
//            Toast.makeText(this, "An error occurred.", Toast.LENGTH_LONG).show()
//        }
//    }

//    fun continueWithMnemonic(mnemonic: String) {
//        val loadFileContents: (String) -> String = { fileName ->
//            MnemonicUtilities.loadFileContents(this, fileName)
//        }
//        try {
//            val hexEncodedSeed = MnemonicCodec(loadFileContents).decode(mnemonic)
//            val seed = Hex.fromStringCondensed(hexEncodedSeed)
//            continueWithSeed(seed)
//        } catch (error: Exception) {
//            val message = if (error is MnemonicCodec.DecodingError) {
//                error.description
//            } else {
//                "An error occurred."

    fun onChange(recoveryPhrase: String) {
        state.value = LinkDeviceState(recoveryPhrase)
    }
}
