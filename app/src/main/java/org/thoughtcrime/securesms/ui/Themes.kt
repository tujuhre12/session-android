package org.thoughtcrime.securesms.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.Colors
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import org.session.libsession.utilities.AppTextSecurePreferences
import org.thoughtcrime.securesms.util.ThemeState
import org.thoughtcrime.securesms.util.themeState

/**
 * Apply a Material2 compose theme based on user selections in SharedPreferences.
 */
@Composable
fun SessionMaterialTheme(
    content: @Composable () -> Unit
) {
    SessionMaterialTheme(LocalContext.current.sessionColors()) { content() }
}

/**
 * Apply a given [SessionColors], and our typography and shapes as a Material 2 Compose Theme.
 **/
@Composable
fun SessionMaterialTheme(
    sessionColors: SessionColors,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = sessionColors.toMaterialColors(),
        typography = sessionTypography,
        shapes = sessionShapes,
    ) {
        val textSelectionColors = TextSelectionColors(
            handleColor = LocalColors.current.primary,
            backgroundColor = LocalColors.current.primary.copy(alpha = 0.5f)
        )

        CompositionLocalProvider(
            LocalColors provides sessionColors,
            LocalContentColor provides sessionColors.text,
            LocalTextSelectionColors provides textSelectionColors
        ) {
            content()
        }
    }
}

private fun SessionColors.toMaterialColors() = Colors(
    primary = background,
    primaryVariant = backgroundSecondary,
    secondary = background,
    secondaryVariant = background,
    background = background,
    surface = background,
    error = danger,
    onPrimary = text,
    onSecondary = text,
    onBackground = text,
    onSurface = text,
    onError = text,
    isLight = isLight
)

@Composable private fun Context.sessionColors() = AppTextSecurePreferences(this).themeState().sessionColors()
@Composable private fun ThemeState.sessionColors() = sessionColors(if (followSystem) !isSystemInDarkTheme() else isLight, isClassic, accent)

val sessionShapes = Shapes(
    small = RoundedCornerShape(50)
)

/**
 * Set the Material 2 theme and a background for Compose Previews.
 */
@Composable
fun PreviewTheme(
    sessionColors: SessionColors = LocalColors.current,
    content: @Composable () -> Unit
) {
    SessionMaterialTheme(sessionColors) {
        Box(modifier = Modifier.background(color = LocalColors.current.background)) {
            content()
        }
    }
}

class SessionColorsParameterProvider : PreviewParameterProvider<SessionColors> {
    override val values = sequenceOf(
        sessionColors(isLight = false, isClassic = true),
        sessionColors(isLight = true, isClassic = true),
        sessionColors(isLight = false, isClassic = false),
        sessionColors(isLight = true, isClassic = false),
    )
}
