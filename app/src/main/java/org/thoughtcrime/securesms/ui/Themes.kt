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
 * Apply a given [Palette], and our typography and shapes as a Material 2 Compose Theme.
 **/
@Composable
fun SessionMaterialTheme(
    palette: Palette,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = palette.toMaterialColors(),
        typography = sessionTypography,
        shapes = sessionShapes,
    ) {
        val textSelectionColors = TextSelectionColors(
            handleColor = LocalPalette.current.primary,
            backgroundColor = LocalPalette.current.primary.copy(alpha = 0.5f)
        )

        CompositionLocalProvider(
            LocalPalette provides palette,
            LocalContentColor provides palette.text,
            LocalTextSelectionColors provides textSelectionColors
        ) {
            content()
        }
    }
}

private fun Palette.toMaterialColors() = Colors(
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
    palette: Palette = LocalPalette.current,
    content: @Composable () -> Unit
) {
    SessionMaterialTheme(palette) {
        Box(modifier = Modifier.background(color = LocalPalette.current.background)) {
            content()
        }
    }
}

class SessionColorsParameterProvider : PreviewParameterProvider<Palette> {
    override val values = sequenceOf(ClassicDark(), ClassicLight(), OceanDark(), OceanLight())
}
