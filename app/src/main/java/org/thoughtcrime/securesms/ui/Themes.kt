package org.thoughtcrime.securesms.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.Colors
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Text
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import network.loki.messenger.R
import org.session.libsession.utilities.AppTextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.util.ThemeState
import org.thoughtcrime.securesms.util.themeState

/**
 * Apply a Material2 compose theme based on user selections in SharedPreferences.
 */
@Composable
fun SessionMaterialTheme(
    content: @Composable () -> Unit
) {
    SessionMaterialTheme(LocalContext.current.composeTheme()) { content() }
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
        typography = sessionTypography.asMaterialTypography(),
        shapes = sessionShapes,
    ) {
        val textSelectionColors = TextSelectionColors(
            handleColor = LocalColors.current.primary,
            backgroundColor = LocalColors.current.primary.copy(alpha = 0.5f)
        )

        CompositionLocalProvider(
            LocalColors provides sessionColors,
            LocalType provides sessionTypography,
            LocalContentColor provides sessionColors.text,
            LocalTextSelectionColors provides textSelectionColors
        ) {
            content()
        }
    }
}

// Compose theme holder
val LocalColors = compositionLocalOf { classicDark }
val LocalType = compositionLocalOf { sessionTypography }

// Our themes
val classicDark = SessionColors(
    isLight = false,
    primary = primaryGreen,
    danger = dangerDark,
    disabled = disabledDark,
    background = Color.Black,
    backgroundSecondary = classicDark1,
    text = Color.White,
    textSecondary = classicDark5,
    borders = classicDark3,
    textBubbleSent = Color.Black,
    backgroundBubbleReceived = classicDark2,
    textBubbleReceived = Color.White,
)

val classicLight = SessionColors(
    isLight = true,
    primary = primaryGreen,
    danger = dangerLight,
    disabled = disabledLight,
    background = Color.White,
    backgroundSecondary = classicLight5,
    text = Color.Black,
    textSecondary = classicLight1,
    borders = classicLight3,
    textBubbleSent = Color.Black,
    backgroundBubbleReceived = classicLight4,
    textBubbleReceived = classicLight4,
)

val oceanDark = SessionColors(
    isLight = false,
    primary = primaryBlue,
    danger = dangerDark,
    disabled = disabledDark,
    background = oceanDark2,
    backgroundSecondary = oceanDark1,
    text = Color.White,
    textSecondary = oceanDark5,
    borders = oceanDark4,
    textBubbleSent = Color.Black,
    backgroundBubbleReceived = oceanDark4,
    textBubbleReceived = oceanDark4,
)

val oceanLight = SessionColors(
    isLight = true,
    primary = primaryBlue,
    danger = dangerLight,
    disabled = disabledLight,
    background = oceanLight7,
    backgroundSecondary = oceanLight6,
    text = oceanLight1,
    textSecondary = oceanLight2,
    borders = oceanLight3,
    textBubbleSent = oceanLight1,
    backgroundBubbleReceived = oceanLight4,
    textBubbleReceived = oceanLight4
)

val classicTheme = SessionColorSet(
    lightTheme = classicLight,
    darkTheme = classicDark
)

val oceanTheme = SessionColorSet(
    lightTheme = oceanLight,
    darkTheme = oceanDark
)

@Composable private fun Context.composeTheme() = AppTextSecurePreferences(this).themeState().composeTheme()
// We still need to match xml values for now but once we go full Compose all of this can go
@Composable private fun ThemeState.composeTheme(): SessionColors {
    // pick the theme based on xml value
    val colorSet = when (theme) {
        R.style.Ocean_Light,
        R.style.Ocean_Dark -> oceanTheme

        else -> classicTheme
    }

    // get the mode (light/dark/system) from settings
    val colorMode =
        when {
            followSystem -> { // user decided to 'match system settings'
                if(isSystemInDarkTheme()) colorSet.darkTheme else colorSet.lightTheme
            }
            isLight -> colorSet.lightTheme
            else -> colorSet.darkTheme
        }

    // set the accent as per user choice
    return colorMode.copy(primary = accent)
}

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
        classicDark, classicLight, oceanDark, oceanLight
    )
}

@Preview
@Composable
fun PreviewThemeColors(
    @PreviewParameter(SessionColorsParameterProvider::class) sessionColors: SessionColors
) {
    PreviewTheme(sessionColors) { ThemeColors() }
}

@Composable
private fun ThemeColors() {
    Column {
        Box(Modifier.background(LocalColors.current.primary)) {
            Text("primary", style = LocalType.current.base)
        }
        Box(Modifier.background(LocalColors.current.background)) {
            Text("background", style = LocalType.current.base)
        }
        Box(Modifier.background(LocalColors.current.backgroundSecondary)) {
            Text("backgroundSecondary", style = LocalType.current.base)
        }
        Box(Modifier.background(LocalColors.current.text)) {
            Text("text", style = LocalType.current.base)
        }
        Box(Modifier.background(LocalColors.current.textSecondary)) {
            Text("textSecondary", style = LocalType.current.base)
        }
        Box(Modifier.background(LocalColors.current.danger)) {
            Text("danger", style = LocalType.current.base)
        }
        Box(Modifier.background(LocalColors.current.borders)) {
            Text("border", style = LocalType.current.base)
        }
    }
}
