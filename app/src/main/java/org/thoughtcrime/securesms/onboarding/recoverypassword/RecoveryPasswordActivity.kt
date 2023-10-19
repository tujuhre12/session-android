package org.thoughtcrime.securesms.onboarding.recoverypassword

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.utilities.hexEncodedPrivateKey
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.MnemonicUtilities
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.CellWithPaddingAndMargin
import org.thoughtcrime.securesms.ui.OutlineButton
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionShieldIcon
import org.thoughtcrime.securesms.ui.ThemeResPreviewParameterProvider
import org.thoughtcrime.securesms.ui.classicDarkColors
import org.thoughtcrime.securesms.ui.colorDestructive
import org.thoughtcrime.securesms.ui.extraSmall
import org.thoughtcrime.securesms.ui.h8

class RecoveryPasswordActivity : BaseActionBarActivity() {

    private val seed by lazy {
        var hexEncodedSeed = IdentityKeyUtil.retrieve(this, IdentityKeyUtil.LOKI_SEED)
        if (hexEncodedSeed == null) {
            hexEncodedSeed = IdentityKeyUtil.getIdentityKeyPair(this).hexEncodedPrivateKey // Legacy account
        }
        val loadFileContents: (String) -> String = { fileName ->
            MnemonicUtilities.loadFileContents(this, fileName)
        }
        MnemonicCodec(loadFileContents).encode(hexEncodedSeed!!, MnemonicCodec.Language.Configuration.english)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.title = resources.getString(R.string.activity_recovery_password)

        ComposeView(this)
            .apply { setContent { RecoveryPassword() } }
            .let(::setContentView)
    }

    private fun revealSeed() {
        TextSecurePreferences.setHasViewedSeed(this, true)
    }

    private fun copySeed() {
        revealSeed()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Seed", seed)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}

@Preview
@Composable
fun PreviewMessageDetails(
    @PreviewParameter(ThemeResPreviewParameterProvider::class) themeResId: Int
) {
    PreviewTheme(themeResId) {
        RecoveryPassword()
    }
}

@Composable
fun RecoveryPassword() {
    AppTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RecoveryPasswordCell()
            HideRecoveryPasswordCell()
        }
    }
}


@Composable
fun RecoveryPasswordCell() {
    CellWithPaddingAndMargin {
        Column {
            Row {
                Text("Recovery Password")
                Spacer(Modifier.width(8.dp))
                SessionShieldIcon()
            }

            Text("Use your recovery password to load your account on new devices.\n\nYour account cannot be recovered without your recovery password. Make sure it's stored somewhere safe and secure — and don't share it with anyone.")

            Text(
                "Voyage  urban  toyed  maverick peculiar tuxedo penguin tree grass building listen speak withdraw terminal plane",
                modifier = Modifier
                    .padding(vertical = 24.dp)
                    .border(
                        width = 1.dp,
                        color = classicDarkColors[3],
                        shape = RoundedCornerShape(11.dp)
                    )
                    .padding(24.dp),
                style = MaterialTheme.typography.extraSmall.copy(fontFamily = FontFamily.Monospace)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                OutlineButton(text = stringResource(R.string.copy), modifier = Modifier.weight(1f), color = MaterialTheme.colors.onPrimary) {}
                OutlineButton(text = "View QR", modifier = Modifier.weight(1f), color = MaterialTheme.colors.onPrimary) {}
            }
        }
    }
}

@Composable
fun HideRecoveryPasswordCell() {
    CellWithPaddingAndMargin {
        Row {
            Column(Modifier.weight(1f)) {
                Text(text = "Hide Recovery Password", style = MaterialTheme.typography.h8)
                Text(text = "Permanently hide your recovery password on this device.")
            }
            OutlineButton(
                "Hide",
                modifier = Modifier.align(Alignment.CenterVertically),
                color = colorDestructive
            ) {}
        }
    }
}

fun Context.startRecoveryPasswordActivity() {
    Intent(this, RecoveryPasswordActivity::class.java).also(::startActivity)
}
