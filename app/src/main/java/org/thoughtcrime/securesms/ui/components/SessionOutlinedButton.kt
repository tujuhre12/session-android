package org.thoughtcrime.securesms.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.LaunchedEffectAsync
import org.thoughtcrime.securesms.ui.LocalPalette
import org.thoughtcrime.securesms.ui.contentDescription
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
fun SessionOutlinedButton(
    text: String,
    modifier: Modifier = Modifier,
    size: ButtonSize = LargeButtonSize,
    color: Color = LocalPalette.current.buttonOutline,
    onClick: () -> Unit
) {
    SessionOutlinedButton(
        modifier = modifier,
        size = size,
        color = color,
        onClick = onClick
    ) {
        SessionButtonText(text = text, style = size.textStyle, color = color)
    }
}

/**
 * Base implementation of [SessionOutlinedButton]
 */
@Composable
fun SessionOutlinedButton(
    modifier: Modifier = Modifier,
    size: ButtonSize = LargeButtonSize,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    color: Color = LocalPalette.current.buttonOutline,
    border: BorderStroke = BorderStroke(1.dp, if (enabled) color else LocalPalette.current.disabled),
    shape: Shape = MaterialTheme.shapes.small,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {}
) {
    OutlinedButton(
        modifier = modifier.heightIn(min = size.minHeight),
        enabled = enabled,
        interactionSource = interactionSource,
        onClick = onClick,
        border = border,
        shape = shape,
        colors = LocalPalette.current.outlinedButtonColors(color)
    ) {
        size.applyTextStyle {
            content()
        }
    }
}

@Composable
fun SessionOutlinedCopyButton(
    modifier: Modifier = Modifier,
    size: ButtonSize = LargeButtonSize,
    color: Color = LocalPalette.current.buttonOutline,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    SessionOutlinedButton(
        modifier = modifier.contentDescription(R.string.AccessibilityId_copy_button),
        size = size,
        interactionSource = interactionSource,
        color = color,
        onClick = onClick
    ) {
        TemporaryClickedContent(
            interactionSource = interactionSource,
            content = {
                SessionButtonText(
                    text = stringResource(R.string.copy),
                    color = color
                )
            },
            temporaryContent = {
                SessionButtonText(
                    text = stringResource(R.string.copied),
                    color = color
                )
            }
        )
    }
}

@Composable
fun TemporaryClickedContent(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource,
    content: @Composable () -> Unit,
    temporaryContent: @Composable () -> Unit,
    temporaryDelay: Duration = 2.seconds
) {
    var clicked by remember { mutableStateOf(false) }

    LaunchedEffectAsync {
        interactionSource.releases.collectLatest {
            clicked = true
            delay(temporaryDelay)
            clicked = false
        }
    }

    // Using a Box because the Buttons add children in a Row
    // and they will jank as they are added and removed.
    Box(contentAlignment = Alignment.Center) {
        AnimatedVisibility(!clicked, enter = fadeIn(), exit = fadeOut()) {
            content()
        }
        AnimatedVisibility(clicked, enter = fadeIn(), exit = fadeOut()) {
            temporaryContent()
        }
    }
}
