package org.thoughtcrime.securesms.recoverypassword

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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.CellWithPaddingAndMargin
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.SessionShieldIcon
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.components.QrImage
import org.thoughtcrime.securesms.ui.components.SlimOutlineButton
import org.thoughtcrime.securesms.ui.components.SlimOutlineCopyButton
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.extraSmallMonospace
import org.thoughtcrime.securesms.ui.h8

@Preview
@Composable
private fun PreviewRecoveryPasswordScreen(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
) {
    PreviewTheme(colors) {
        RecoveryPasswordScreen(seed = "Voyage  urban  toyed  maverick peculiar tuxedo penguin tree grass building listen speak withdraw terminal plane")
    }
}

@Composable
internal fun RecoveryPasswordScreen(
    seed: String = "",
    copySeed:() -> Unit = {},
    onHide:() -> Unit = {}
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.marginExtraSmall),
        modifier = Modifier
            .contentDescription(R.string.AccessibilityId_recovery_password)
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalDimensions.current.marginExtraSmall)
    ) {
        RecoveryPasswordCell(seed, copySeed)
        HideRecoveryPasswordCell(onHide)
    }
}

@Composable
private fun RecoveryPasswordCell(seed: String, copySeed:() -> Unit = {}) {
    var showQr by remember {
        mutableStateOf(false)
    }

    CellWithPaddingAndMargin {
        Column {
            Row {
                Text(
                    stringResource(R.string.sessionRecoveryPassword),
                    style = h8
                )
                Spacer(Modifier.width(LocalDimensions.current.itemSpacingExtraSmall))
                SessionShieldIcon()
            }

            Spacer(modifier = Modifier.height(LocalDimensions.current.marginTiny))

            Text(
                stringResource(R.string.recoveryPasswordDescription),
                style = base
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
                    SlimOutlineCopyButton(
                        Modifier.weight(1f),
                        onClick = copySeed
                    )
                    SlimOutlineButton(
                        stringResource(R.string.qrView),
                        Modifier.weight(1f),
                    ) { showQr = !showQr }
                }
            }

            AnimatedVisibility(showQr, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                SlimOutlineButton(
                    stringResource(R.string.recoveryPasswordView),
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
        style = extraSmallMonospace,
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
                    style = h8
                )
                Text(
                    stringResource(R.string.recoveryPasswordHideRecoveryPasswordDescription),
                    style = base
                )
            }
            Spacer(modifier = Modifier.width(LocalDimensions.current.marginExtraExtraSmall))
            SlimOutlineButton(
                text = stringResource(R.string.hide),
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
