package org.thoughtcrime.securesms.onboarding.recoverypassword

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.ui.CellWithPaddingAndMargin
import org.thoughtcrime.securesms.ui.LocalColors
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColors
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.SessionMaterialTheme
import org.thoughtcrime.securesms.ui.SessionShieldIcon
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.components.OutlineButton
import org.thoughtcrime.securesms.ui.components.OutlineCopyButton
import org.thoughtcrime.securesms.ui.components.QrImage
import org.thoughtcrime.securesms.ui.components.SmallButtons
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.extraSmallMonospace
import org.thoughtcrime.securesms.ui.h8

class RecoveryPasswordActivity : BaseActionBarActivity() {

    private val viewModel: RecoveryPasswordViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.title = resources.getString(R.string.sessionRecoveryPassword)

        ComposeView(this).apply {
            setContent {
                RecoveryPasswordScreen(
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
            destructiveButton(R.string.continue_2, R.string.AccessibilityId_continue) { onHideConfirm() }
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
fun PreviewRecoveryPasswordScreen(
    @PreviewParameter(SessionColorsParameterProvider::class) sessionColors: SessionColors
) {
    PreviewTheme(sessionColors) {
        RecoveryPasswordScreen(seed = "Voyage  urban  toyed  maverick peculiar tuxedo penguin tree grass building listen speak withdraw terminal plane")
    }
}

@Composable
fun RecoveryPasswordScreen(
    seed: String = "",
    copySeed:() -> Unit = {},
    onHide:() -> Unit = {}
) {
    SessionMaterialTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.marginExtraSmall),
            modifier = Modifier
                .contentDescription(R.string.AccessibilityId_recovery_password)
                .verticalScroll(rememberScrollState())
                .padding(bottom = LocalDimensions.current.marginExtraSmall)
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
                Text(
                    stringResource(R.string.sessionRecoveryPassword),
                    style = MaterialTheme.typography.h8
                )
                Spacer(Modifier.width(LocalDimensions.current.itemSpacingExtraSmall))
                SessionShieldIcon()
            }

            Spacer(modifier = Modifier.height(LocalDimensions.current.marginTiny))

            Text(
                stringResource(R.string.recoveryPasswordDescription),
                style = MaterialTheme.typography.base
            )

            AnimatedVisibility(!showQr) {
                RecoveryPassword(seed)
            }

            AnimatedVisibility(
                showQr,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                QrImage(
                    seed,
                    modifier = Modifier
                        .padding(vertical = LocalDimensions.current.marginSmall)
                        .contentDescription(R.string.AccessibilityId_qr_code),
                    icon = R.drawable.session_shield
                )
            }

            AnimatedVisibility(!showQr) {
                Row(horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.marginMedium)) {
                    OutlineCopyButton(
                        Modifier
                            .weight(1f)
                            .contentDescription(R.string.AccessibilityId_copy_button),
                        color = LocalColors.current.text,
                        onClick = copySeed
                    )
                    OutlineButton(
                        textId = R.string.qrView,
                        modifier = Modifier.weight(1f),
                        color = LocalColors.current.text,
                        onClick = { showQr = !showQr }
                    )
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
private fun RecoveryPassword(seed: String) {
    Text(
        seed,
        modifier = Modifier
            .contentDescription(R.string.AccessibilityId_recovery_password_container)
            .padding(vertical = LocalDimensions.current.marginSmall)
            .border(
                width = 1.dp,
                color = LocalColors.current.borders,
                shape = RoundedCornerShape(11.dp)
            )
            .padding(LocalDimensions.current.marginSmall),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.extraSmallMonospace,
        color = LocalColors.current.run { if (isLight) text else primary },
    )
}

@Composable
private fun HideRecoveryPasswordCell(onHide: () -> Unit = {}) {
    CellWithPaddingAndMargin {
        Row {
            Column(
                Modifier.weight(1f)
            ) {
                Text(
                    stringResource(R.string.recoveryPasswordHideRecoveryPassword),
                    style = MaterialTheme.typography.h8
                )
                Text(
                    stringResource(R.string.recoveryPasswordHideRecoveryPasswordDescription),
                    style = MaterialTheme.typography.base
                )
            }
            Spacer(modifier = Modifier.width(LocalDimensions.current.marginExtraExtraSmall))
            OutlineButton(
                textId = R.string.hide,
                modifier = Modifier
                    .wrapContentWidth()
                    .align(Alignment.CenterVertically)
                    .contentDescription(R.string.AccessibilityId_hide_recovery_password_button),
                color = LocalColors.current.danger,
                onClick = onHide
            )
        }
    }
}
