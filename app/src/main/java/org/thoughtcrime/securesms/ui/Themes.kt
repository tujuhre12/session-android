package org.thoughtcrime.securesms.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import org.session.libsession.utilities.AppTextSecurePreferences
import org.thoughtcrime.securesms.ui.color.colors

// Globally accessible composition local objects
val LocalColors = staticCompositionLocalOf<ThemeColors> { ClassicDark() }

/**
 * Apply a Material2 compose theme based on user selections in SharedPreferences.
 */
@Composable
fun SessionMaterialTheme(
    content: @Composable () -> Unit
) {
    SessionMaterialTheme(LocalContext.current.colors()) { content() }
}

/**
 * Apply a given [ThemeColors], and our typography and shapes as a Material 2 Compose Theme.
 **/
@Composable
fun SessionMaterialTheme(
    colors: ThemeColors,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = colors.toMaterialColors(),
        typography = sessionTypography,
        shapes = sessionShapes,
    ) {
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalContentColor provides colors.text,
            LocalTextSelectionColors provides colors.textSelectionColors,
        ) {
            content()
        }
    }
}

@Composable private fun Context.colors() = AppTextSecurePreferences(this).colors()

val pillShape = RoundedCornerShape(percent = 50)
val buttonShape = pillShape

val sessionShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp)
)

/**
 * Set the Material 2 theme and a background for Compose Previews.
 */
@Composable
fun PreviewTheme(
    colors: ThemeColors = LocalColors.current,
    content: @Composable () -> Unit
) {
    SessionMaterialTheme(colors) {
        Box(modifier = Modifier.background(color = LocalColors.current.background)) {
            content()
        }
    }
}

class SessionColorsParameterProvider : PreviewParameterProvider<ThemeColors> {
    override val values = sequenceOf(ClassicDark(), ClassicLight(), OceanDark(), OceanLight())
}
