package org.thoughtcrime.securesms.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.LaunchedEffectAsync
import org.thoughtcrime.securesms.ui.LocalButtonColor
import org.thoughtcrime.securesms.ui.baseBold
import org.thoughtcrime.securesms.ui.colorDestructive
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.extraSmall

val LocalButtonSize = staticCompositionLocalOf { mediumButton }

@Composable
fun Modifier.applyButtonSize() = then(LocalButtonSize.current)

val mediumButton = Modifier.height(41.dp)
val smallButton = Modifier.wrapContentHeight()

@Composable
fun SessionButtonText(
    text: String,
    modifier: Modifier = Modifier
){
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.baseBold
    )
}

@Composable
fun OutlineButton(
    @StringRes textId: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) { OutlineButton(stringResource(textId), modifier, onClick) }

@Composable
fun OutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlineButton(
        modifier = modifier,
        onClick = onClick
    ) {
        SessionButtonText(text = text)
    }
}

@Composable
fun OutlineButton(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
    content: @Composable () -> Unit = {}
) {
    OutlinedButton(
        modifier = modifier.applyButtonSize(),
        interactionSource = interactionSource,
        onClick = onClick,
        border = BorderStroke(1.dp, LocalButtonColor.current),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = LocalButtonColor.current,
            backgroundColor = Color.Unspecified
        )
    ) {
        content()
    }
}

@Composable
fun OutlineCopyButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }

    OutlineButton(
        modifier = modifier,
        interactionSource = interactionSource,
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
                        modifier = Modifier.align(Alignment.Center),
                        text = stringResource(R.string.copy)
                    )
                }
            },
            temporaryContent = {
                // using a centered box because the outline button uses rowscope internally and it shows the crossfade
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SessionButtonText(
                        modifier = Modifier.align(Alignment.Center),
                        text = stringResource(R.string.copied)
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
    temporaryDelay: Long = 2000
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
            contentColor = MaterialTheme.colors.background,
            backgroundColor = LocalButtonColor.current
        )
    ) {
        SessionButtonText(text)
    }
}

@Composable
fun BorderlessButtonSecondary(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    BorderlessButton(
        text,
        modifier = modifier,
        contentColor = MaterialTheme.colors.onSurface.copy(ContentAlpha.medium),
        onClick = onClick
    )
}

@Composable
fun BorderlessButton(
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colors.onBackground,
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
    contentColor: Color = MaterialTheme.colors.onBackground,
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
fun BorderlessHtmlButton(
    textId: Int,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colors.onBackground,
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

@Composable
fun DestructiveButtons(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalButtonColor provides colorDestructive) { content() }
}

@Composable
fun OnPrimaryButtons(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalButtonColor provides MaterialTheme.colors.onPrimary) { content() }
}
