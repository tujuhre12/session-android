package org.thoughtcrime.securesms.onboarding.messagenotifications

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.thoughtcrime.securesms.onboarding.ui.ContinuePrimaryOutlineButton
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.color.LocalColors
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

        Column(modifier = Modifier.padding(horizontal = LocalDimensions.current.onboardingMargin)) {
            Text(stringResource(R.string.notificationsMessage), style = h4)
            Spacer(Modifier.height(LocalDimensions.current.xsMargin))
            Text(stringResource(R.string.onboardingMessageNotificationExplaination), style = base)
            Spacer(Modifier.height(LocalDimensions.current.itemSpacing))
            NotificationRadioButton(
                R.string.activity_pn_mode_fast_mode,
                R.string.activity_pn_mode_fast_mode_explanation,
                R.string.activity_pn_mode_recommended_option_tag,
                contentDescription = R.string.AccessibilityId_fast_mode_notifications_button,
                selected = state.pushEnabled,
                onClick = { setEnabled(true) }
            )
            Spacer(Modifier.height(LocalDimensions.current.xsItemSpacing))
            NotificationRadioButton(
                R.string.activity_pn_mode_slow_mode,
                R.string.activity_pn_mode_slow_mode_explanation,
                contentDescription = R.string.AccessibilityId_slow_mode_notifications_button,
                selected = state.pushDisabled,
                radioEnterTransition = slideInVertically { -it },
                radioExitTransition = slideOutVertically { -it },
                onClick = { setEnabled(false) }
            )
        }

        Spacer(Modifier.weight(1f))

        ContinuePrimaryOutlineButton(Modifier.align(Alignment.CenterHorizontally), onContinue)
    }
}

@Composable
private fun NotificationRadioButton(
    @StringRes title: Int,
    @StringRes explanation: Int,
    @StringRes tag: Int? = null,
    @StringRes contentDescription: Int? = null,
    selected: Boolean = false,
    radioEnterTransition: EnterTransition = slideInVertically { it },
    radioExitTransition: ExitTransition = slideOutVertically { it },
    onClick: () -> Unit = {}
) {
    Row {
        Button(
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .contentDescription(contentDescription),
            type = ButtonType.Outline(
                contentColor = LocalColors.current.text,
                borderColor = LocalColors.current.borders
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 10.dp, bottom = 11.dp)
        ) {
            Column {
                Text(stringResource(title), style = h8)

                Text(stringResource(explanation), style = small, modifier = Modifier.padding(top = 7.dp))
                tag?.let {
                    Text(
                        stringResource(it),
                        modifier = Modifier.padding(top = 6.dp),
                        color = LocalColors.current.primary,
                        style = h9
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(20.dp))
        RadioButton(
            selected = selected,
            modifier = Modifier
                .size(22.dp)
                .align(Alignment.CenterVertically),
            enter = radioEnterTransition,
            exit = radioExitTransition,
            onClick = onClick
        )
    }
}

@Composable
private fun RadioButton(
    selected: Boolean,
    modifier: Modifier,
    enter: EnterTransition = slideInVertically { it },
    exit: ExitTransition = slideOutVertically { it },
    onClick: () -> Unit
) {
    IconButton(modifier = modifier, onClick = onClick) {
        AnimatedVisibility(
            selected,
            modifier = Modifier.padding(2.5.dp)
                .clip(CircleShape),
            enter = enter,
            exit = exit
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = LocalColors.current.primary,
                        shape = CircleShape
                    )
            )
        }
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .border(
                    width = LocalDimensions.current.borderStroke,
                    color = LocalColors.current.text,
                    shape = CircleShape
                )
        ) {}
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
