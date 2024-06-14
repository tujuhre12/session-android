package org.thoughtcrime.securesms.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ButtonElevation
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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

//@Composable
//fun OutlineButton(
//    text: String,
//    modifier: Modifier = Modifier,
//    size: ButtonSize = LargeButtonSize,
//    color: Color = LocalPalette.current.buttonOutline,
//    onClick: () -> Unit
//) {
//    OutlineButton(
//        modifier = modifier,
//        size = size,
//        color = color,
//        onClick = onClick
//    ) {
//        SessionButtonText(text = text, style = size.textStyle, color = color)
//    }
//}
//
///**
// * Base implementation of [SessionOutlinedButton]
// */
//@Composable
//fun OutlineButton(
//    modifier: Modifier = Modifier,
//    size: ButtonSize = LargeButtonSize,
//    enabled: Boolean = true,
//    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
//    color: Color = LocalPalette.current.buttonOutline,
//    border: BorderStroke = BorderStroke(1.dp, if (enabled) color else LocalPalette.current.disabled),
//    shape: Shape = MaterialTheme.shapes.small,
//    onClick: () -> Unit,
//    content: @Composable () -> Unit = {}
//) {
//    Button(
//        modifier = modifier.heightIn(min = size.minHeight),
//        enabled = enabled,
//        interactionSource = interactionSource,
//        onClick = onClick,
//        border = border,
//        shape = shape,
//        type = ButtonType.Outline,
//        color = color
//    ) {
//        size.applyTextStyle {
//            content()
//        }
//    }
//}

@Composable
fun SlimOutlineCopyButton(
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.buttonOutline,
    onClick: () -> Unit
) {
    OutlineCopyButton(modifier, SlimButtonSize, color, onClick)
}

@Composable
fun OutlineCopyButton(
    modifier: Modifier = Modifier,
    size: ButtonSize = LargeButtonSize,
    color: Color = LocalPalette.current.buttonOutline,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        modifier = modifier.contentDescription(R.string.AccessibilityId_copy_button),
        interactionSource = interactionSource,
        size = size,
        type = ButtonType.Outline,
        color = color,
        onClick = onClick
    ) {
        TemporaryClickedContent(
            interactionSource = interactionSource,
            content = { Text(stringResource(R.string.copy)) },
            temporaryContent = { Text(stringResource(R.string.copied)) }
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

interface ButtonType {
    @Composable fun border(color: Color, enabled: Boolean): BorderStroke?
    @Composable fun buttonColors(color: Color): ButtonColors
    val elevation: ButtonElevation? @Composable get

    object Outline: ButtonType {
        @Composable override fun border(color: Color, enabled: Boolean) = BorderStroke(1.dp, if (enabled) color else LocalPalette.current.disabled)
        @Composable override fun buttonColors(color: Color) = ButtonDefaults.buttonColors(
            contentColor = color,
            backgroundColor = Color.Unspecified,
            disabledContentColor = LocalPalette.current.disabled,
            disabledBackgroundColor = Color.Unspecified
        )
        override val elevation: ButtonElevation? @Composable get() = null
    }
    object Fill: ButtonType {
        @Composable override fun border(color: Color, enabled: Boolean) = null
        @Composable override fun buttonColors(color: Color) = ButtonDefaults.buttonColors(
            contentColor = LocalPalette.current.background,
            backgroundColor = color,
            disabledContentColor = LocalPalette.current.disabled,
            disabledBackgroundColor = Color.Unspecified
        )
        override val elevation: ButtonElevation? @Composable get() = ButtonDefaults.elevation()
    }
}

/**
 * Base [Button] implementation
 */
@Composable
fun Button(
    onClick: () -> Unit,
    color: Color,
    type: ButtonType,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = LargeButtonSize,
    elevation: ButtonElevation? = type.elevation,
    shape: Shape = MaterialTheme.shapes.small,
    border: BorderStroke? = type.border(color, enabled),
    colors: ButtonColors = type.buttonColors(color),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    size.applyConstraints {
        androidx.compose.material.Button(
            onClick,
            modifier.heightIn(min = size.minHeight),
            enabled,
            interactionSource,
            elevation,
            shape,
            border,
            colors,
            content = content
        )
    }
}

/**
 * Courtesy [Button] implementation
 */
@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    color: Color,
    type: ButtonType,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = LargeButtonSize,
    elevation: ButtonElevation? = type.elevation,
    shape: Shape = MaterialTheme.shapes.small,
    border: BorderStroke? = type.border(color, enabled),
    colors: ButtonColors = type.buttonColors(color),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    Button(onClick, color, type, modifier, enabled, size, elevation, shape, border, colors, interactionSource) {
        Text(text)
    }
}

@Composable fun FillButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(text, onClick, LocalPalette.current.buttonOutline, ButtonType.Fill, modifier, enabled)
}

@Composable fun PrimaryFillButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(text, onClick, LocalPalette.current.primary, ButtonType.Fill, modifier, enabled)
}

@Composable fun OutlineButton(text: String, modifier: Modifier = Modifier, color: Color = LocalPalette.current.buttonOutline, enabled: Boolean = true, onClick: () -> Unit) {
    Button(text, onClick, color, ButtonType.Outline, modifier, enabled)
}

@Composable fun PrimaryOutlineButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(text, onClick, LocalPalette.current.primary, ButtonType.Outline, modifier, enabled)
}

@Composable fun SlimOutlineButton(text: String, modifier: Modifier = Modifier, color: Color = LocalPalette.current.buttonOutline, enabled: Boolean = true, onClick: () -> Unit) {
    Button(text, onClick, color, ButtonType.Outline, modifier, enabled, SlimButtonSize)
}

@Composable fun SlimOutlineButton(onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = LocalPalette.current.buttonOutline, enabled: Boolean = true, content: @Composable () -> Unit) {
    Button(onClick, color, ButtonType.Outline, modifier, enabled, SlimButtonSize) { content() }
}
