package org.thoughtcrime.securesms.recoverypassword

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.session.libsession.utilities.AppTextSecurePreferences
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.utilities.hexEncodedPrivateKey
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.MnemonicUtilities

@HiltViewModel
class RecoveryPasswordViewModel @Inject constructor(
    private val application: Application
): AndroidViewModel(application) {
    val prefs = AppTextSecurePreferences(application)

    // Regex to remove any spurious characters from our recovery password mnemonic.
    // The regex matches are:
    //   - "\r"     - carriage return,
    //   - "\n"     - newline,
    //   - "\u2028" - unicode line separator,
    //   - "\u2029" - unicode paragraph separator,
    //   - "\s*"    - collapse multiple matches into a single character,
    //   - "\s{2,}" - replace any remaining instances of two or more spaces with a single space.
    val linebreakCollapseAndReplaceRegex = Regex("""\s*[\r\n\u2028\u2029]+\s*""")
    val linebreakFilterDoubleSpacesRegex = Regex(""""\s{2,}""")

    val seed = MutableStateFlow<String?>(null)
    val mnemonic = seed.filterNotNull()
        .map {
            MnemonicCodec {
                MnemonicUtilities.loadFileContents(application, it)
            }
            .encode(it, MnemonicCodec.Language.Configuration.english)
            .trim() // Remove any leading or trailing whitespace
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun copyMnemonic() {
        prefs.setHasViewedSeed(true)

        // Ensure that our mnemonic words are separated by single spaces only without any control characters
        val normalisedMnemonic = mnemonic.value
            .replace(linebreakCollapseAndReplaceRegex, " ")
            .replace(linebreakFilterDoubleSpacesRegex, " ") // Note: Order is important - do this AFTER we've collapsed things via the regex above.

        ClipData.newPlainText("Seed", normalisedMnemonic)
            .let(application.clipboard::setPrimaryClip)
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            seed.emit(IdentityKeyUtil.retrieve(application, IdentityKeyUtil.LOKI_SEED)
                ?: IdentityKeyUtil.getIdentityKeyPair(application).hexEncodedPrivateKey) // Legacy account
        }
    }
}

private val Context.clipboard get() = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
