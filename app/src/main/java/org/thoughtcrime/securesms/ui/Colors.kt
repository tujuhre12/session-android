package org.thoughtcrime.securesms.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Colors
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import org.session.libsession.utilities.AppTextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.util.ThemeState
import org.thoughtcrime.securesms.util.themeState

const val classicDark0 = 0xff111111
const val classicDark1 = 0xff1B1B1B
const val classicDark2 = 0xff2D2D2D
const val classicDark3 = 0xff414141
const val classicDark4 = 0xff767676
const val classicDark5 = 0xffA1A2A1
const val classicDark6 = 0xffFFFFFF

const val classicLight0 = 0xff000000
const val classicLight1 = 0xff6D6D6D
const val classicLight2 = 0xffA1A2A1
const val classicLight3 = 0xffDFDFDF
const val classicLight4 = 0xffF0F0F0
const val classicLight5 = 0xffF9F9F9
const val classicLight6 = 0xffFFFFFF

const val oceanDark0 = 0xff000000
const val oceanDark1 = 0xff1A1C28
const val oceanDark2 = 0xff252735
const val oceanDark3 = 0xff2B2D40
const val oceanDark4 = 0xff3D4A5D
const val oceanDark5 = 0xffA6A9CE
const val oceanDark6 = 0xff5CAACC
const val oceanDark7 = 0xffFFFFFF

const val oceanLight0 = 0xff000000
const val oceanLight1 = 0xff19345D
const val oceanLight2 = 0xff6A6E90
const val oceanLight3 = 0xff5CAACC
const val oceanLight4 = 0xffB3EDF2
const val oceanLight5 = 0xffE7F3F4
const val oceanLight6 = 0xffECFAFB
const val oceanLight7 = 0xffFCFFFF

val Colors.disabled @Composable get() = onSurface.copy(alpha = ContentAlpha.disabled)

val oceanLights = arrayOf(oceanLight0, oceanLight1, oceanLight2, oceanLight3, oceanLight4, oceanLight5, oceanLight6, oceanLight7)
val oceanDarks = arrayOf(oceanDark0, oceanDark1, oceanDark2, oceanDark3, oceanDark4, oceanDark5, oceanDark6, oceanDark7)
val classicLights = arrayOf(classicLight0, classicLight1, classicLight2, classicLight3, classicLight4, classicLight5, classicLight6)
val classicDarks = arrayOf(classicDark0, classicDark1, classicDark2, classicDark3, classicDark4, classicDark5, classicDark6)

val oceanLightColors = oceanLights.map(::Color)
val oceanDarkColors = oceanDarks.map(::Color)
val classicLightColors = classicLights.map(::Color)
val classicDarkColors = classicDarks.map(::Color)

val blackAlpha40 = Color.Black.copy(alpha = 0.4f)

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
    val backgroundLight get() = if (isLight) backgroundSecondary else Color.White
    val onBackgroundLight get() = if (isLight) text else background
    val button get() = if (isLight) text else primary
    val divider get() = text.copy(alpha = TabRowDefaults.DividerOpacity)
    val backgroundBubbleSent get() = primary
    @Composable fun radioButtonColors() = RadioButtonDefaults.colors(selectedColor = primary, unselectedColor = text, disabledColor = disabled)
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

fun sessionColors(
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
        text = listOf(Color.White, Color.Black, Color.White, oceanLightColors[1])[index],
        textSecondary = listOf(classicDarkColors[5], classicLightColors[1], oceanDarkColors[5], oceanLightColors[2])[index],
        borders = listOf(classicDarkColors[3], classicLightColors[3], oceanDarkColors[4], oceanLightColors[3])[index],
        textBubbleSent = listOf(Color.Black, Color.Black, Color.Black, oceanLightColors[1])[index],
        backgroundBubbleReceived = listOf(classicDarkColors[2], classicLightColors[4], oceanDarkColors[4], oceanLightColors[4])[index],
        textBubbleReceived = listOf(Color.White, classicLightColors[4], oceanDarkColors[4], oceanLightColors[4])[index],
    )
}

@Composable
fun transparentButtonColors() = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent)

@Composable
fun destructiveButtonColors() = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent, contentColor = LocalColors.current.danger)

@Composable
fun Colors(name: String, colors: List<Color>) {
    Column {
        colors.forEachIndexed { i, it ->
            Box(Modifier.background(it)) {
                Text("$name: $i")
            }
        }
    }
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
        Box(Modifier.background(MaterialTheme.colors.primary)) {
            Text("primary", style = MaterialTheme.typography.base)
        }
        Box(Modifier.background(MaterialTheme.colors.primaryVariant)) {
            Text("primaryVariant", style = MaterialTheme.typography.base)
        }
        Box(Modifier.background(MaterialTheme.colors.secondary)) {
            Text("secondary", style = MaterialTheme.typography.base)
        }
        Box(Modifier.background(MaterialTheme.colors.secondaryVariant)) {
            Text("secondaryVariant", style = MaterialTheme.typography.base)
        }
        Box(Modifier.background(MaterialTheme.colors.surface)) {
            Text("surface", style = MaterialTheme.typography.base)
        }
        Box(Modifier.background(MaterialTheme.colors.primarySurface)) {
            Text("primarySurface", style = MaterialTheme.typography.base)
        }
        Box(Modifier.background(MaterialTheme.colors.background)) {
            Text("background", style = MaterialTheme.typography.base)
        }
        Box(Modifier.background(MaterialTheme.colors.error)) {
            Text("error", style = MaterialTheme.typography.base)
        }
    }
}

@Composable
fun outlinedTextFieldColors(
    isError: Boolean
) = TextFieldDefaults.outlinedTextFieldColors(
    textColor = if (isError) LocalColors.current.danger else LocalContentColor.current,
    cursorColor = if (isError) LocalColors.current.danger else LocalContentColor.current,
    focusedBorderColor = LocalColors.current.borders,
    unfocusedBorderColor = LocalColors.current.borders,
    placeholderColor = if (isError) LocalColors.current.danger else LocalColors.current.textSecondary
)

fun TextSecurePreferences.Companion.getAccentColor(context: Context): Color = when (getAccentColorName(context)) {
    BLUE_ACCENT -> primaryBlue
    PURPLE_ACCENT -> primaryPurple
    PINK_ACCENT -> primaryPink
    RED_ACCENT -> primaryRed
    ORANGE_ACCENT -> primaryOrange
    YELLOW_ACCENT -> primaryYellow
    else -> primaryGreen
}
