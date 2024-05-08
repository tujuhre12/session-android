package org.thoughtcrime.securesms.onboarding.recoverypassword

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
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
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionShieldIcon
import org.thoughtcrime.securesms.ui.ThemeResPreviewParameterProvider
import org.thoughtcrime.securesms.ui.classicDarkColors
import org.thoughtcrime.securesms.ui.components.DestructiveButtons
import org.thoughtcrime.securesms.ui.components.OutlineButton
import org.thoughtcrime.securesms.ui.components.QrImage
import org.thoughtcrime.securesms.ui.components.SmallButtons
import org.thoughtcrime.securesms.ui.components.TemporaryStateButton
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.h8
import org.thoughtcrime.securesms.ui.small

class RecoveryPasswordActivity : BaseActionBarActivity() {

    private val viewModel: RecoveryPasswordViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.title = resources.getString(R.string.sessionRecoveryPassword)

        ComposeView(this).apply {
            setContent {
                RecoveryPassword(
                    viewModel.seed,
                    { viewModel.copySeed(context) }
                ) { onHide() }
            }
        }.let(::setContentView)
    }

    private fun onHide() {
        showSessionDialog {
            title(R.string.recoveryPasswordHidePermanently)
            htmlText(R.string.recoveryPasswordHidePermanentlyDescription1)
            destructiveButton(R.string.continue_2) { onHideConfirm() }
            cancelButton()
        }
    }

    private fun onHideConfirm() {
        showSessionDialog {
            title(R.string.recoveryPasswordHidePermanently)
            text(R.string.recoveryPasswordHidePermanentlyDescription2)
            cancelButton()
            destructiveButton(
                R.string.yes,
                contentDescription = R.string.AccessibilityId_confirm_button
            ) {
                viewModel.permanentlyHidePassword()
                finish()
            }
        }
    }
}

@Preview
@Composable
fun PreviewRecoveryPassword(
    @PreviewParameter(ThemeResPreviewParameterProvider::class) themeResId: Int
) {
    PreviewTheme(themeResId) {
        RecoveryPassword(seed = "Voyage  urban  toyed  maverick peculiar tuxedo penguin tree grass building listen speak withdraw terminal plane")
    }
}

@Composable
fun RecoveryPassword(
    seed: String = "",
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
            SmallButtons {
                RecoveryPasswordCell(seed, copySeed)
                HideRecoveryPasswordCell(onHide)
            }
        }
    }
}

@Composable
fun RecoveryPasswordCell(seed: String, copySeed:() -> Unit = {}) {
    var showQr by remember {
        mutableStateOf(false)
    }

    CellWithPaddingAndMargin {
        Column {
            Row {
                Text(stringResource(R.string.sessionRecoveryPassword))
                Spacer(Modifier.width(8.dp))
                SessionShieldIcon()
            }

            Text(stringResource(R.string.recoveryPasswordDescription))

            AnimatedVisibility(!showQr) {
                Text(
                    seed,
                    modifier = Modifier
                        .contentDescription(R.string.AccessibilityId_hide_recovery_password_button)
                        .padding(vertical = 24.dp)
                        .border(
                            width = 1.dp,
                            color = classicDarkColors[3],
                            shape = RoundedCornerShape(11.dp)
                        )
                        .padding(24.dp),
                    style = MaterialTheme.typography.small.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colors.run { if (isLight) onSurface else secondary },
                )
            }

            AnimatedVisibility(
                showQr,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                QrImage(
                    seed,
                    modifier = Modifier.padding(vertical = 24.dp),
                    contentDescription = "QR code of your recovery password",
                    icon = R.drawable.session_shield
                )
            }

            AnimatedVisibility(!showQr) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    TemporaryStateButton { source, temporary ->
                        OutlineButton(
                            modifier = Modifier.weight(1f),
                            interactionSource = source,
                            onClick = { copySeed() },
                        ) {
                            AnimatedVisibility(temporary) { Text(stringResource(R.string.copied)) }
                            AnimatedVisibility(!temporary) { Text(stringResource(R.string.copy)) }
                        }
                    }
                    OutlineButton(textId = R.string.qrView, modifier = Modifier.weight(1f), onClick = { showQr = !showQr })
                }
            }

            AnimatedVisibility(showQr, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                OutlineButton(
                    textId = R.string.recoveryPasswordView,
                    onClick = { showQr = !showQr }
                )
            }
        }
    }
}

@Composable
fun HideRecoveryPasswordCell(onHide: () -> Unit = {}) {
    CellWithPaddingAndMargin {
        Row {
            Column(
                Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.recoveryPasswordHideRecoveryPassword), style = MaterialTheme.typography.h8)
                Text(text = stringResource(R.string.recoveryPasswordHideRecoveryPasswordDescription))
            }
            DestructiveButtons {
                OutlineButton(
                    textId = R.string.hide,
                    modifier = Modifier
                        .wrapContentWidth()
                        .align(Alignment.CenterVertically)
                        .contentDescription(R.string.AccessibilityId_hide_recovery_password_button),
                    onClick = onHide
                )
            }
        }
    }
}
