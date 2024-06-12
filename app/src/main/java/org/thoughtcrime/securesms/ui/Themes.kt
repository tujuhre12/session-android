package org.thoughtcrime.securesms.ui

import android.content.Context
import androidx.annotation.AttrRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.Colors
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.themeadapter.appcompat.createAppCompatTheme
import com.google.android.material.color.MaterialColors
import network.loki.messenger.R
import org.session.libsession.utilities.AppTextSecurePreferences
import org.thoughtcrime.securesms.util.ThemeState
import org.thoughtcrime.securesms.util.themeState

val LocalLightCell = staticCompositionLocalOf { Color.Black }
val LocalOnLightCell = staticCompositionLocalOf { Color.Black }

val LocalDimensions = staticCompositionLocalOf { Dimensions() }

data class Dimensions(
    val itemSpacingTiny: Dp = 4.dp,
    val itemSpacingExtraSmall: Dp = 8.dp,
    val itemSpacingSmall: Dp = 16.dp,
    val itemSpacingMedium: Dp = 24.dp,
    val marginTiny: Dp = 8.dp,
    val marginExtraExtraSmall: Dp = 12.dp,
    val marginExtraSmall: Dp = 16.dp,
    val marginSmall: Dp = 24.dp,
    val marginMedium: Dp = 32.dp,
    val marginLarge: Dp = 64.dp,
    val dividerIndent: Dp = 80.dp,
)

val LocalColors = staticCompositionLocalOf { sessionColors(isLight = false, isClassic = true) }

data class SessionColors(
    val isLight: Boolean = false,
    val primary: Color = Color.Unspecified,
    val danger: Color = Color.Unspecified,
    val disabled: Color = Color.Unspecified,
    val background: Color = Color.Unspecified,
    val backgroundSecondary: Color = Color.Unspecified,
    val text: Color = Color.Unspecified,
    val textSecondary: Color = Color.Unspecified,
    val borders: Color = Color.Unspecified,
    val textBubbleSent: Color = Color.Unspecified,
    val backgroundBubbleReceived: Color = Color.Unspecified,
    val textBubbleReceived: Color = Color.Unspecified,
) {
    val divider get() = text.copy(alpha = TabRowDefaults.DividerOpacity)
    val backgroundBubbleSent get() = primary
}

val primaryGreen = Color(0xFF31F196)
val primaryBlue = Color(0xFF57C9FA)
val primaryPurple = Color(0xFFC993FF)
val primaryPink = Color(0xFFFF95EF)
val primaryRed = Color(0xFFFF9C8E)
val primaryOrange = Color(0xFFFCB159)
val primaryYellow = Color(0xFFFAD657)

val dangerDark = Color(0xFFFF3A3A)
val dangerLight = Color(0xFFE12D19)
val disabledDark = Color(0xFFA1A2A1)
val disabledLioht = Color(0xFF6D6D6D)

val primaryColors = listOf(
    primaryGreen,
    primaryBlue,
    primaryPurple,
    primaryPink,
    primaryRed,
    primaryOrange,
    primaryYellow,
)

private class UnresolvedColor(val function: (Boolean, Boolean) -> Color) {
    operator fun invoke(isLight: Boolean, isClassic: Boolean) = function(isLight, isClassic)

    constructor(light: Color, dark: Color): this(function = { isLight, _ -> if (isLight) light else dark })
    constructor(classicDark: Color, classicLight: Color, oceanDark: Color, oceanLight: Color): this(function = { isLight, isClassic -> if (isLight) if (isClassic) classicLight else oceanLight else if (isClassic) classicDark else oceanDark })
}

private fun sessionColors(
    isLight: Boolean,
    isClassic: Boolean,
    primary: Color = if (isClassic) primaryGreen else primaryBlue
): SessionColors {
    val index = (if (isLight) 1 else 0) + if (isClassic) 0 else 2
    return SessionColors(
        isLight = isLight,
        primary = primary,
        danger = if (isLight) dangerLight else dangerDark,
        disabled = if (isLight) disabledLioht else disabledDark,
        background = listOf(Color.Black, Color.White, oceanDarkColors[2], oceanLightColors[7])[index],
        backgroundSecondary = listOf(classicDarkColors[1], classicLightColors[5], oceanDarkColors[1], oceanLightColors[6])[index],
        text = listOf(Color.White, Color.Black, oceanDarkColors[1], oceanLightColors[1])[index],
        textSecondary = listOf(classicDarkColors[5], classicLightColors[1], oceanDarkColors[5], oceanLightColors[2])[index],
        borders = listOf(classicDarkColors[3], classicLightColors[3], oceanDarkColors[4], oceanLightColors[3])[index],
        textBubbleSent = listOf(Color.Black, Color.Black, Color.Black, oceanLightColors[1])[index],
        backgroundBubbleReceived = listOf(classicDarkColors[2], classicLightColors[4], oceanDarkColors[4], oceanLightColors[4])[index],
        textBubbleReceived = listOf(Color.White, classicLightColors[4], oceanDarkColors[4], oceanLightColors[4])[index],
    )
}


private fun Context.sessionColors() = AppTextSecurePreferences(this).themeState().sessionColors()
private fun ThemeState.sessionColors() = sessionColors(isLight, isClassic, accent)

/**
 * Sets a Material2 compose theme based on your selections in SharedPreferences.
 */
@Composable
fun SessionMaterialTheme(
    content: @Composable () -> Unit
) {
    SessionMaterialTheme(LocalContext.current.sessionColors()) { content() }
}


/**
 *
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
    onSurface = background,
    onError = text,
    isLight = isLight
)

val sessionShapes = Shapes(
    small = RoundedCornerShape(50)
)

fun boldStyle(size: TextUnit) = TextStyle.Default.copy(
    fontWeight = FontWeight.Bold,
    fontSize = size
)

fun defaultStyle(size: TextUnit) = TextStyle.Default.copy(
    fontSize = size,
    lineHeight = size * 1.2
)

val sessionTypography = Typography(
    h1 = boldStyle(36.sp),
    h2 = boldStyle(32.sp),
    h3 = boldStyle(29.sp),
    h4 = boldStyle(26.sp),
    h5 = boldStyle(23.sp),
    h6 = boldStyle(20.sp),
)

val Typography.xl get() = defaultStyle(18.sp)
val Typography.large get() = defaultStyle(16.sp)
val Typography.base get() = defaultStyle(14.sp)
val Typography.baseBold get() = boldStyle(14.sp)
val Typography.baseMonospace get() = defaultStyle(14.sp).copy(fontFamily = FontFamily.Monospace)
val Typography.small get() = defaultStyle(12.sp)
val Typography.smallMonospace get() = defaultStyle(12.sp).copy(fontFamily = FontFamily.Monospace)
val Typography.extraSmall get() = defaultStyle(11.sp)
val Typography.extraSmallMonospace get() = defaultStyle(11.sp).copy(fontFamily = FontFamily.Monospace)
val Typography.fine get() = defaultStyle(9.sp)

val Typography.h7 get() = boldStyle(18.sp)
val Typography.h8 get() = boldStyle(16.sp)
val Typography.h9 get() = boldStyle(14.sp)

fun Context.getColorFromTheme(@AttrRes attr: Int, defaultValue: Int = 0x0): Color =
    MaterialColors.getColor(this, attr, defaultValue).let(::Color)

/**
 * Set the theme and a background for Compose Previews.
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
