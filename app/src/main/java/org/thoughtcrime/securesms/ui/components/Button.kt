package org.thoughtcrime.securesms.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlin.time.Duration.Companion.seconds

val LocalButtonSize = staticCompositionLocalOf { mediumButton }

@Composable
fun Modifier.applyButtonSize() = then(LocalButtonSize.current)

val mediumButton = Modifier.height(41.dp)
val smallButton = Modifier.wrapContentHeight()

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
    ) { Text(text, style = MaterialTheme.typography.baseBold) }
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
    OutlineTemporaryStateButton(
        text = stringResource(R.string.copy),
        temporaryText = stringResource(R.string.copy),
        modifier = modifier.contentDescription(R.string.AccessibilityId_copy_button),
        onClick = onClick
    )
}

@Composable
fun OutlineTemporaryStateButton(
    text: String,
    temporaryText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    OutlineTemporaryStateButton(modifier, onClick) { isTemporary ->
        Text(
            if (isTemporary) temporaryText else text,
            style = MaterialTheme.typography.baseBold
        )
    }
}

@Composable
fun OutlineTemporaryStateButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable (Boolean) -> Unit,
) {
    TemporaryStateButton { source, isTemporary ->
        OutlineButton(
            modifier = modifier,
            interactionSource = source,
            onClick = onClick,
        ) {
            AnimatedVisibility(isTemporary) { content(true) }
            AnimatedVisibility(!isTemporary) { content(false) }
        }
    }
}

@Composable
fun TemporaryStateButton(
    content: @Composable (MutableInteractionSource, Boolean) -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }

    var clicked by remember { mutableStateOf(false) }

    content(interactions, clicked)

    LaunchedEffectAsync {
        interactions.releases.collectLatest {
            clicked = true
            delay(2.seconds)
            clicked = false
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
        Text(text = text, style = MaterialTheme.typography.baseBold)
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
    text: String,
    modifier: Modifier = Modifier,
    contentDescription: GetString = GetString(text),
    contentColor: Color = MaterialTheme.colors.onBackground,
    backgroundColor: Color = Color.Transparent,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.contentDescription(contentDescription),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            backgroundColor = backgroundColor
        )
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
