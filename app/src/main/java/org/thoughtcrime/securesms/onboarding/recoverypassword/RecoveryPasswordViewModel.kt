package org.thoughtcrime.securesms.onboarding.recoverypassword

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.utilities.hexEncodedPrivateKey
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.MnemonicUtilities
import org.thoughtcrime.securesms.util.QRCodeUtilities
import org.thoughtcrime.securesms.util.toPx
import javax.inject.Inject

@HiltViewModel
class RecoveryPasswordViewModel @Inject constructor(
    private val application: Application
): AndroidViewModel(application) {

    val seed by lazy {
        val hexEncodedSeed = IdentityKeyUtil.retrieve(application, IdentityKeyUtil.LOKI_SEED)
            ?: IdentityKeyUtil.getIdentityKeyPair(application).hexEncodedPrivateKey // Legacy account
        MnemonicCodec { MnemonicUtilities.loadFileContents(application, it) }
            .encode(hexEncodedSeed, MnemonicCodec.Language.Configuration.english)
    }

    val qrBitmap by lazy {
        QRCodeUtilities.encode(
            data = seed,
            size = toPx(280, application.resources),
            isInverted = false,
            hasTransparentBackground = true
        )
    }
}
