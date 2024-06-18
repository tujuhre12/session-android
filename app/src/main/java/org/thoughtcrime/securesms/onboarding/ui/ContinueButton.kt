package org.thoughtcrime.securesms.onboarding.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.components.OutlineButton
import org.thoughtcrime.securesms.ui.contentDescription

@Composable
fun ContinueButton(modifier: Modifier, onContinue: () -> Unit) {
    OutlineButton(
        stringResource(R.string.continue_2),
        modifier = modifier
            .contentDescription(R.string.AccessibilityId_continue)
            .fillMaxWidth()
            .padding(horizontal = LocalDimensions.current.largeMargin)
            .padding(bottom = LocalDimensions.current.xxsMargin),
        onClick = onContinue,
    )
}

@Composable
fun OnboardingButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    OutlineButton(
        text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LocalDimensions.current.largeMargin)
            .padding(bottom = LocalDimensions.current.xxsMargin),
        onClick = onClick,
    )
}
