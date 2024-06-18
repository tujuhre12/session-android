package org.thoughtcrime.securesms.onboarding.messagenotifications

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.thoughtcrime.securesms.onboarding.ui.ContinueButton
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.color.radioButtonColors
import org.thoughtcrime.securesms.ui.components.Button
import org.thoughtcrime.securesms.ui.components.ButtonType
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.h4
import org.thoughtcrime.securesms.ui.h8
import org.thoughtcrime.securesms.ui.h9
import org.thoughtcrime.securesms.ui.small

@Composable
internal fun MessageNotificationsScreen(
    state: MessageNotificationsState = MessageNotificationsState(),
    setEnabled: (Boolean) -> Unit = {},
    onContinue: () -> Unit = {}
) {
    Column {
        Spacer(Modifier.weight(1f))

        Column(modifier = Modifier.padding(horizontal = LocalDimensions.current.marginOnboarding)) {
            Text(stringResource(R.string.notificationsMessage), style = h4)
            Spacer(Modifier.height(LocalDimensions.current.marginExtraSmall))
            Text(stringResource(R.string.onboardingMessageNotificationExplaination), style = base)
            Spacer(Modifier.height(LocalDimensions.current.itemSpacingMedium))
            NotificationRadioButton(
                R.string.activity_pn_mode_fast_mode,
                R.string.activity_pn_mode_fast_mode_explanation,
                R.string.activity_pn_mode_recommended_option_tag,
                contentDescription = R.string.AccessibilityId_fast_mode_notifications_button,
                selected = state.pushEnabled,
                onClick = { setEnabled(true) }
            )
            Spacer(Modifier.height(LocalDimensions.current.itemSpacingXSmall))
            NotificationRadioButton(
                R.string.activity_pn_mode_slow_mode,
                R.string.activity_pn_mode_slow_mode_explanation,
                contentDescription = R.string.AccessibilityId_slow_mode_notifications_button,
                selected = state.pushDisabled,
                onClick = { setEnabled(false) }
            )
        }

        Spacer(Modifier.weight(1f))

        ContinueButton(Modifier.align(Alignment.CenterHorizontally), onContinue)
    }
}

@Composable
private fun NotificationRadioButton(
    @StringRes title: Int,
    @StringRes explanation: Int,
    @StringRes tag: Int? = null,
    @StringRes contentDescription: Int? = null,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row {
        Button(
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .contentDescription(contentDescription),
            type = ButtonType.Outline,
            color = LocalColors.current.text,
            border = BorderStroke(
                width = ButtonDefaults.OutlinedBorderSize,
                color = if (selected) LocalColors.current.primary else LocalColors.current.borders
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.itemSpacingXXSmall)
            ) {
                Text(stringResource(title), style = h8)
                Text(stringResource(explanation), style = small)
                tag?.let {
                    Text(
                        stringResource(it),
                        color = LocalColors.current.primary,
                        style = h9
                    )
                }
            }
        }
        RadioButton(
            selected = selected,
            modifier = Modifier.align(Alignment.CenterVertically),
            onClick = onClick,
            colors = LocalColors.current.radioButtonColors()
        )
    }
}

@Preview
@Composable
private fun MessageNotificationsScreenPreview(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
) {
    PreviewTheme(colors) {
        MessageNotificationsScreen()
    }
}
