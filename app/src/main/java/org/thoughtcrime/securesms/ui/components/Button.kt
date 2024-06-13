package org.thoughtcrime.securesms.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.LocalPalette
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.baseBold
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.extraSmall
import org.thoughtcrime.securesms.ui.h8
import org.thoughtcrime.securesms.ui.h9
import org.thoughtcrime.securesms.ui.radioButtonColors
import org.thoughtcrime.securesms.ui.small

val LocalButtonTextStyle = staticCompositionLocalOf { baseBold }

/**
 * Text to be used in buttons.
 *
 * This text gets its style from [LocalButtonTextStyle] which may vary if button size is changed
 * by passing in a [ButtonSize] to some Session Button.
 */
@Composable
fun SessionButtonText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalButtonTextStyle.current,
    color: Color = LocalPalette.current.buttonOutline,
    enabled: Boolean = true
) {
    Text(
        modifier = modifier,
        text = text,
        style = style,
        color = if (enabled) color else LocalPalette.current.disabled
    )
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
        colors = LocalPalette.current.filledButtonColors()
    ) {
        SessionButtonText(text, color = LocalPalette.current.background)
    }
}

@Composable
fun BorderlessButton(
    modifier: Modifier = Modifier,
    contentColor: Color = LocalPalette.current.text,
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
    contentColor: Color = LocalPalette.current.text,
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
    contentColor: Color = LocalPalette.current.text,
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
    contentColor: Color = LocalPalette.current.text,
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

@Composable
fun NotificationRadioButton(
    @StringRes title: Int,
    @StringRes explanation: Int,
    @StringRes tag: Int? = null,
    @StringRes contentDescription: Int? = null,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row {
        SessionOutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .contentDescription(contentDescription),
            color = LocalPalette.current.text,
            border = BorderStroke(
                width = ButtonDefaults.OutlinedBorderSize,
                color = if (selected) LocalPalette.current.primary else LocalPalette.current.borders
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(stringResource(title), style = h8)
                Text(stringResource(explanation), style = small)
                tag?.let {
                    Text(
                        stringResource(it),
                        color = LocalPalette.current.primary,
                        style = h9
                    )
                }
            }
        }
        RadioButton(
            selected = selected,
            modifier = Modifier.align(Alignment.CenterVertically),
            onClick = onClick,
            colors = LocalPalette.current.radioButtonColors()
        )
    }
}

val MutableInteractionSource.releases
    get() = interactions.filter { it is PressInteraction.Release }
