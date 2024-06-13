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
import org.session.libsession.utilities.TextSecurePreferences

val classicDark0 = Color(0xff111111)
val classicDark1 = Color(0xff1B1B1B)
val classicDark2 = Color(0xff2D2D2D)
val classicDark3 = Color(0xff414141)
val classicDark4 = Color(0xff767676)
val classicDark5 = Color(0xffA1A2A1)
val classicDark6 = Color(0xffFFFFFF)

val classicLight0 = Color(0xff000000)
val classicLight1 = Color(0xff6D6D6D)
val classicLight2 = Color(0xffA1A2A1)
val classicLight3 = Color(0xffDFDFDF)
val classicLight4 = Color(0xffF0F0F0)
val classicLight5 = Color(0xffF9F9F9)
val classicLight6 = Color(0xffFFFFFF)

val oceanDark0 = Color(0xff000000)
val oceanDark1 = Color(0xff1A1C28)
val oceanDark2 = Color(0xff252735)
val oceanDark3 = Color(0xff2B2D40)
val oceanDark4 = Color(0xff3D4A5D)
val oceanDark5 = Color(0xffA6A9CE)
val oceanDark6 = Color(0xff5CAACC)
val oceanDark7 = Color(0xffFFFFFF)

val oceanLight0 = Color(0xff000000)
val oceanLight1 = Color(0xff19345D)
val oceanLight2 = Color(0xff6A6E90)
val oceanLight3 = Color(0xff5CAACC)
val oceanLight4 = Color(0xffB3EDF2)
val oceanLight5 = Color(0xffE7F3F4)
val oceanLight6 = Color(0xffECFAFB)
val oceanLight7 = Color(0xffFCFFFF)

val Colors.disabled @Composable get() = onSurface.copy(alpha = ContentAlpha.disabled)

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
val disabledLight = Color(0xFF6D6D6D)

fun sessionColors(
    isLight: Boolean,
    isClassic: Boolean,
    primary: Color = if (isClassic) primaryGreen else primaryBlue
): SessionColors = if (isClassic) {
    if (isLight) classicLight else classicDark
} else {
    if (isLight) oceanLight else oceanDark
}.copy(
    primary = primary
)

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
