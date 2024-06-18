package org.thoughtcrime.securesms.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.LaunchedEffectAsync
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.baseBold
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.extraSmall
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    size: ButtonStyle = ButtonStyle.Large,
    shape: Shape = MaterialTheme.shapes.small,
    border: BorderStroke? = type.border(color, enabled),
    colors: ButtonColors = type.buttonColors(color),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    size.applyButtonConstraints {
        androidx.compose.material.Button(
            onClick,
            modifier.heightIn(min = size.minHeight),
            enabled,
            interactionSource,
            elevation = null,
            shape,
            border,
            colors
        ) {
            // Button sets LocalTextStyle, so text style is applied inside to override that.
            size.applyTextConstraints {
                content()
            }
        }
    }
}

/**
 * Courtesy [Button] implementation for buttons that just display text.
 */
@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    color: Color,
    type: ButtonType,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonStyle = ButtonStyle.Large,
    shape: Shape = MaterialTheme.shapes.small,
    border: BorderStroke? = type.border(color, enabled),
    colors: ButtonColors = type.buttonColors(color),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    Button(onClick, color, type, modifier, enabled, size, shape, border, colors, interactionSource) {
        Text(text)
    }
}

@Composable fun FillButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(text, onClick, LocalColors.current.buttonOutline, ButtonType.Fill, modifier, enabled)
}

@Composable fun PrimaryFillButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(text, onClick, LocalColors.current.primary, ButtonType.Fill, modifier, enabled)
}

@Composable fun OutlineButton(text: String, modifier: Modifier = Modifier, color: Color = LocalColors.current.buttonOutline, enabled: Boolean = true, onClick: () -> Unit) {
    Button(text, onClick, color, ButtonType.Outline, modifier, enabled)
}

@Composable fun OutlineButton(modifier: Modifier = Modifier, color: Color = LocalColors.current.buttonOutline, enabled: Boolean = true, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Button(
        onClick = onClick,
        color = color,
        type = ButtonType.Outline,
        modifier = modifier,
        enabled = enabled,
        content = content
    )
}

@Composable fun PrimaryOutlineButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(text, onClick, LocalColors.current.primary, ButtonType.Outline, modifier, enabled)
}

@Composable fun SlimOutlineButton(onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = LocalColors.current.text, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(onClick, color, ButtonType.Outline, modifier, enabled, ButtonStyle.Slim, content = content)
}

/**
 * Courtesy [SlimOutlineButton] implementation for buttons that just display text.
 */
@Composable fun SlimOutlineButton(text: String, modifier: Modifier = Modifier, color: Color = LocalColors.current.text, enabled: Boolean = true, onClick: () -> Unit) {
    Button(text, onClick, color, ButtonType.Outline, modifier, enabled, ButtonStyle.Slim)
}

@Composable
fun SlimOutlineCopyButton(
    modifier: Modifier = Modifier,
    color: Color = LocalColors.current.text,
    onClick: () -> Unit
) {
    OutlineCopyButton(modifier, ButtonStyle.Slim, color, onClick)
}

@Composable
fun OutlineCopyButton(
    modifier: Modifier = Modifier,
    size: ButtonStyle = ButtonStyle.Large,
    color: Color = LocalColors.current.buttonOutline,
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
    interactionSource: MutableInteractionSource,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
    temporaryContent: @Composable AnimatedVisibilityScope.() -> Unit,
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
        AnimatedVisibility(!clicked, enter = fadeIn(), exit = fadeOut(), content = content)
        AnimatedVisibility(clicked, enter = fadeIn(), exit = fadeOut(), content = temporaryContent)
    }
}

@Composable
fun BorderlessButton(
    modifier: Modifier = Modifier,
    contentColor: Color = LocalColors.current.text,
    backgroundColor: Color = Color.Transparent,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            backgroundColor = backgroundColor
        ),
        content = content
    )
}

@Composable
fun BorderlessButton(
    text: String,
    modifier: Modifier = Modifier,
    contentDescription: GetString = GetString(text),
    contentColor: Color = LocalColors.current.text,
    backgroundColor: Color = Color.Transparent,
    onClick: () -> Unit
) {
    BorderlessButton(
        modifier = modifier.contentDescription(contentDescription),
        contentColor = contentColor,
        backgroundColor = backgroundColor,
        onClick = onClick
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = extraSmall,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun BorderlessButtonWithIcon(
    text: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = baseBold,
    contentColor: Color = LocalColors.current.text,
    backgroundColor: Color = Color.Transparent,
    onClick: () -> Unit
) {
    BorderlessButton(
        modifier = modifier,
        contentColor = contentColor,
        backgroundColor = backgroundColor,
        onClick = onClick
    ) {
        AnnotatedTextWithIcon(text, iconRes, style = style)
    }
}

@Composable
fun BorderlessHtmlButton(
    textId: Int,
    modifier: Modifier = Modifier,
    contentColor: Color = LocalColors.current.text,
    backgroundColor: Color = Color.Transparent,
    onClick: () -> Unit
) {
    BorderlessButton(
        modifier,
        contentColor = contentColor,
        backgroundColor = backgroundColor,
        onClick = onClick
    ) {
        Text(
            text = annotatedStringResource(textId),
            textAlign = TextAlign.Center,
            style = extraSmall,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

val MutableInteractionSource.releases
    get() = interactions.filter { it is PressInteraction.Release }

@Preview
@Composable
private fun VariousButtons(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
) {
    PreviewTheme(colors) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrimaryFillButton("Primary Fill") {}
            OutlineButton("Outline Button") {}
            SlimOutlineButton("Slim Outline") {}
            SlimOutlineButton("Slim Primary", color = LocalColors.current.buttonOutline) {}
            SlimOutlineButton("Slim Danger", color = LocalColors.current.danger) {}
            SlimOutlineCopyButton {}
        }
    }
}
