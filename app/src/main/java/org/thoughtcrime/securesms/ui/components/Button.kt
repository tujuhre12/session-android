package org.thoughtcrime.securesms.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.LaunchedEffectAsync
import org.thoughtcrime.securesms.ui.LocalColors
import org.thoughtcrime.securesms.ui.baseBold
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.extraSmall
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val LocalButtonSize = staticCompositionLocalOf { mediumButton }

@Composable
fun Modifier.applyButtonSize() = then(LocalButtonSize.current)

val mediumButton = Modifier.height(41.dp)
val smallButton = Modifier.wrapContentHeight()

@Composable
fun SessionButtonText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalColors.current.text,
    enabled: Boolean = true
) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.baseBold,
        color = if (enabled) color else LocalColors.current.disabled
    )
}

@Composable
fun OutlineButton(
    @StringRes textId: Int,
    modifier: Modifier = Modifier,
    color: Color = LocalColors.current.button,
    onClick: () -> Unit
) { OutlineButton(stringResource(textId), modifier, color, onClick) }

@Composable
fun OutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalColors.current.button,
    onClick: () -> Unit
) {
    OutlineButton(
        modifier = modifier,
        color = color,
        onClick = onClick
    ) {
        SessionButtonText(text = text, color = color)
    }
}

@Composable
fun OutlineButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    color: Color = LocalColors.current.button,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {}
) {
    OutlinedButton(
        modifier = modifier.applyButtonSize(),
        enabled = enabled,
        interactionSource = interactionSource,
        onClick = onClick,
        border = BorderStroke(1.dp, if (enabled) color else LocalColors.current.disabled),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (enabled) color else Color.Unspecified,
            backgroundColor = Color.Unspecified
        )
    ) {
        content()
    }
}

@Composable
fun OutlineCopyButton(
    modifier: Modifier = Modifier,
    color: Color = LocalColors.current.button,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }

    OutlineButton(
        modifier = modifier,
        interactionSource = interactionSource,
        color = color,
        onClick = onClick
    ) {
        TemporaryClickedContent(
            interactionSource = interactionSource,
            content = {
                // using a centered box because the outline button uses rowscope internally and it shows the crossfade
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SessionButtonText(
                        text = stringResource(R.string.copy),
                        modifier = Modifier.align(Alignment.Center),
                        color = color
                    )
                }
            },
            temporaryContent = {
                // using a centered box because the outline button uses rowscope internally and it shows the crossfade
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SessionButtonText(
                        text = stringResource(R.string.copied),
                        modifier = Modifier.align(Alignment.Center),
                        color = color
                    )
                }
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

    Crossfade(
        modifier = modifier,
        targetState = clicked
    ) {
        when(it) {
            false -> content()
            true -> temporaryContent()
        }
    }
}

@Composable
fun FilledButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = LocalColors.current.background,
            backgroundColor = LocalColors.current.primary
        )
    ) {
        SessionButtonText(text, color = LocalColors.current.background)
    }
}

@Composable
fun BorderlessButton(
    modifier: Modifier = Modifier,
    contentColor: Color = LocalColors.current.text,
    backgroundColor: Color = Color.Transparent,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            backgroundColor = backgroundColor
        )
    ) { content() }
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
            style = MaterialTheme.typography.extraSmall,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun BorderlessButtonWithIcon(
    text: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.baseBold,
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
            style = MaterialTheme.typography.extraSmall,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

private val MutableInteractionSource.releases
    get() = interactions.filter { it is PressInteraction.Release }

@Composable
fun SmallButtons(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalButtonSize provides smallButton) { content() }
}
