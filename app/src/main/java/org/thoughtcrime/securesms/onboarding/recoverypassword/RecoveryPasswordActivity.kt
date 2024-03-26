package org.thoughtcrime.securesms.onboarding.recoverypassword

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.CellWithPaddingAndMargin
import org.thoughtcrime.securesms.ui.LocalExtraColors
import org.thoughtcrime.securesms.ui.OutlineButton
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionShieldIcon
import org.thoughtcrime.securesms.ui.ThemeResPreviewParameterProvider
import org.thoughtcrime.securesms.ui.classicDarkColors
import org.thoughtcrime.securesms.ui.colorDestructive
import org.thoughtcrime.securesms.ui.h8
import org.thoughtcrime.securesms.ui.small

class RecoveryPasswordActivity : BaseActionBarActivity() {

    private val viewModel: RecoveryPasswordViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.title = resources.getString(R.string.sessionRecoveryPassword)

        ComposeView(this).apply {
            setContent {
                RecoveryPassword(viewModel.seed, viewModel.qrBitmap, { viewModel.copySeed(context) }) { onHide() }
            }
        }.let(::setContentView)
    }

    private fun onHide() {
        showSessionDialog {
            title("Hide Recovery Password Permanently")
            text("Without your recovery password, you cannot load your account on new devices.\n" +
                "\n" +
                "We strongly recommend you save your recovery password in a safe and secure place before continuing.")
            destructiveButton(R.string.continue_2) { onHideConfirm() }
            button(R.string.cancel) {}
        }
    }

    private fun onHideConfirm() {
        showSessionDialog {
            title("Hide Recovery Password Permanently")
            text("Are you sure you want to permanently hide your recovery password on this device? This cannot be undone.")
            button(R.string.cancel) {}
            destructiveButton(R.string.yes) {
                viewModel.permanentlyHidePassword()
                finish()
            }
        }
    }
}

@Preview
@Composable
fun PreviewMessageDetails(
    @PreviewParameter(ThemeResPreviewParameterProvider::class) themeResId: Int
) {
    PreviewTheme(themeResId) {
        RecoveryPassword(seed = "Voyage  urban  toyed  maverick peculiar tuxedo penguin tree grass building listen speak withdraw terminal plane")
    }
}

@Composable
fun RecoveryPassword(
    seed: String = "",
    qrBitmap: Bitmap? = null,
    copySeed:() -> Unit = {},
    onHide:() -> Unit = {}
) {
    AppTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            RecoveryPasswordCell(seed, qrBitmap, copySeed)
            HideRecoveryPasswordCell(onHide)
        }
    }
}

@Composable
fun RecoveryPasswordCell(seed: String = "", qrBitmap: Bitmap? = null, copySeed:() -> Unit = {}) {
    val showQr = remember {
        mutableStateOf(false)
    }

    val copied = remember {
        mutableStateOf(false)
    }

    CellWithPaddingAndMargin {
        Column {
            Row {
                Text("Recovery Password")
                Spacer(Modifier.width(8.dp))
                SessionShieldIcon()
            }

            Text("Use your recovery password to load your account on new devices.\n\nYour account cannot be recovered without your recovery password. Make sure it's stored somewhere safe and secure — and don't share it with anyone.")

            AnimatedVisibility(!showQr.value) {
                Text(
                    seed,
                    modifier = Modifier
                            .padding(vertical = 24.dp)
                            .border(
                                    width = 1.dp,
                                    color = classicDarkColors[3],
                                    shape = RoundedCornerShape(11.dp)
                            )
                            .padding(24.dp),
                    style = MaterialTheme.typography.small.copy(fontFamily = FontFamily.Monospace),
                    color = LocalExtraColors.current.prominentButtonColor,
                )
            }

            AnimatedVisibility(showQr.value, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Card(
                    backgroundColor = LocalExtraColors.current.lightCell,
                    elevation = 0.dp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 24.dp)
                ) {
                    qrBitmap?.let {
                        QrImage(
                            bitmap = it,
                            contentDescription = "QR code of your recovery password",
                        )
                    }
                }
            }

            AnimatedVisibility(!showQr.value) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Crossfade(targetState = if (copied.value) R.string.copied else R.string.copy, modifier = Modifier.weight(1f), label = "Copy to Copied CrossFade") {
                        OutlineButton(text = stringResource(it), modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colors.onPrimary) { copySeed(); copied.value = true }
                    }
                    OutlineButton(text = "View QR", modifier = Modifier.weight(1f), color = MaterialTheme.colors.onPrimary) { showQr.toggle() }
                }
            }

            AnimatedVisibility(showQr.value, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                OutlineButton(
                    text = "View Password",
                    color = MaterialTheme.colors.onPrimary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { showQr.toggle() }
            }
        }
    }
}

@Composable
fun QrImage(bitmap: Bitmap, contentDescription: String, icon: Int = R.drawable.session_shield) {
    Box {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(LocalExtraColors.current.onLightCell)
        )

        Icon(
            painter = painterResource(id = icon),
            contentDescription = "",
            tint = LocalExtraColors.current.onLightCell,
            modifier = Modifier
                .align(Alignment.Center)
                .width(46.dp)
                .height(56.dp)
                .background(color = LocalExtraColors.current.lightCell)
                .padding(horizontal = 3.dp, vertical = 1.dp)
        )
    }
}

private fun MutableState<Boolean>.toggle() { value = !value }

@Composable
fun HideRecoveryPasswordCell(onHide: () -> Unit = {}) {
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
            ) { onHide() }
        }
    }
}

fun Context.startRecoveryPasswordActivity() {
    Intent(this, RecoveryPasswordActivity::class.java).also(::startActivity)
}
