package org.thoughtcrime.securesms.onboarding.messagenotifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import network.loki.messenger.R
import org.thoughtcrime.securesms.onboarding.ui.ContinueButton
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.components.NotificationRadioButton
import org.thoughtcrime.securesms.ui.h4

@Preview
@Composable
private fun MessageNotificationsScreenPreview(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
) {
    PreviewTheme(colors) {
        MessageNotificationsScreen()
    }
}

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
            Spacer(Modifier.height(LocalDimensions.current.marginExtraSmall))
            NotificationRadioButton(
                R.string.activity_pn_mode_fast_mode,
                R.string.activity_pn_mode_fast_mode_explanation,
                R.string.activity_pn_mode_recommended_option_tag,
                contentDescription = R.string.AccessibilityId_fast_mode_notifications_button,
                selected = state.pushEnabled,
                onClick = { setEnabled(true) }
            )
            Spacer(Modifier.height(LocalDimensions.current.marginExtraSmall))
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
