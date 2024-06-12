package org.thoughtcrime.securesms.ui

import android.content.Context
import androidx.annotation.AttrRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
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
import org.thoughtcrime.securesms.util.themeState

val LocalCellColor = staticCompositionLocalOf { Color.Black }
val LocalButtonColor = staticCompositionLocalOf { Color.Black }
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

val LocalColors = staticCompositionLocalOf { SessionColors() }

data class SessionColors(
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

private class UnresolvedSessionColors(
    val danger: UnresolvedColor = UnresolvedColor(dark = dangerDark, light = dangerLight),
    val disabled: UnresolvedColor = UnresolvedColor(dark = disabledDark, light = disabledLioht),
    val background: UnresolvedColor = UnresolvedColor(Color.Black, Color.White, oceanDarkColors[2], oceanLightColors[7]),
    val backgroundSecondary: UnresolvedColor = UnresolvedColor(classicDarkColors[1], classicLightColors[5], oceanDarkColors[1], oceanLightColors[6]),
    val text: UnresolvedColor = UnresolvedColor(Color.White, Color.Black, oceanDarkColors[1], oceanLightColors[1]),
    val textSecondary: UnresolvedColor = UnresolvedColor(classicDarkColors[5], classicLightColors[1], oceanDarkColors[5], oceanLightColors[2]),
    val borders: UnresolvedColor = UnresolvedColor(classicDarkColors[3], classicLightColors[3], oceanDarkColors[4], oceanLightColors[3]),
    val textBubbleSent: UnresolvedColor = UnresolvedColor(Color.Black, Color.Black, Color.Black, oceanLightColors[1]),
    val backgroundBubbleReceived: UnresolvedColor = UnresolvedColor(classicDarkColors[2], classicLightColors[4], oceanDarkColors[4], oceanLightColors[4]),
    val textBubbleReceived: UnresolvedColor = UnresolvedColor(Color.White, classicLightColors[4], oceanDarkColors[4], oceanLightColors[4]),
) {
    operator fun invoke(primary: Color, isLight: Boolean, isClassic: Boolean) = SessionColors(
        primary = primary,
        danger = danger(isLight, isClassic),
        disabled = disabled(isLight, isClassic),
        background = background(isLight, isClassic),
        backgroundSecondary = backgroundSecondary(isLight, isClassic),
        text = text(isLight, isClassic),
        textSecondary = textSecondary(isLight, isClassic),
        borders = borders(isLight, isClassic),
        textBubbleSent = textBubbleSent(isLight, isClassic),
        backgroundBubbleReceived = backgroundBubbleReceived(isLight, isClassic),
        textBubbleReceived = textBubbleReceived(isLight, isClassic),
    )
}

/**
 * Converts current Theme to Compose Theme.
 */
@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val surface = context.getColorFromTheme(R.attr.colorSettingsBackground)

    val themeState = AppTextSecurePreferences(context).themeState()

    val sessionColors = UnresolvedSessionColors()(themeState.accent, themeState.isLight, themeState.isClassic)

    val textSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colors.secondary,
        backgroundColor = MaterialTheme.colors.secondary.copy(alpha = 0.5f)
    )

    CompositionLocalProvider(
        *listOf(
            LocalCellColor to R.attr.colorSettingsBackground,
            LocalButtonColor to R.attr.prominentButtonColor,
            LocalLightCell to R.attr.lightCell,
            LocalOnLightCell to R.attr.onLightCell,
        ).map { (local, attr) -> local provides context.getColorFromTheme(attr) }.toTypedArray()
    ) {
        AppCompatTheme(surface = surface) {
            CompositionLocalProvider(
                LocalColors provides sessionColors,
                LocalTextSelectionColors provides textSelectionColors
            ) {
                content()
            }
        }
    }
}

@Composable
fun AppCompatTheme(
    context: Context = LocalContext.current,
    readColors: Boolean = true,
    typography: Typography = sessionTypography,
    shapes: Shapes = MaterialTheme.shapes,
    surface: Color? = null,
    content: @Composable () -> Unit
) {
    val themeParams = remember(context.theme) {
        context.createAppCompatTheme(
            readColors = readColors,
            readTypography = false
        )
    }

    val colors = themeParams.colors ?: MaterialTheme.colors

    MaterialTheme(
        colors = colors.copy(
            surface = surface ?: colors.surface
        ),
        typography = typography,
        shapes = shapes.copy(
            small = RoundedCornerShape(50)
        ),
    ) {
        // We update the LocalContentColor to match our onBackground. This allows the default
        // content color to be more appropriate to the theme background
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colors.onBackground,
            content = content
        )
    }
}

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
    themeResId: Int,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalContext provides ContextThemeWrapper(LocalContext.current, themeResId)
    ) {
        AppTheme {
            Box(modifier = Modifier.background(color = MaterialTheme.colors.background)) {
                content()
            }
        }
    }
}

class ThemeResPreviewParameterProvider : PreviewParameterProvider<Int> {
    override val values = sequenceOf(
        R.style.Classic_Dark,
        R.style.Classic_Light,
        R.style.Ocean_Dark,
        R.style.Ocean_Light,
    )
}
