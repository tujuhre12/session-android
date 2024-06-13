package org.thoughtcrime.securesms.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.ButtonDefaults
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

val blackAlpha40 = Color.Black.copy(alpha = 0.4f)

val LocalPalette = staticCompositionLocalOf<Palette> { ClassicDark() }

interface Palette {
    @Composable
    fun outlinedButtonColors(color: Color) = ButtonDefaults.outlinedButtonColors(
        contentColor = color,
        backgroundColor = Color.Unspecified,
        disabledContentColor = disabled
    )

    @Composable
    fun filledButtonColors() = ButtonDefaults.outlinedButtonColors(
        contentColor = background,
        backgroundColor = primary,
        disabledContentColor = disabled
    )

    val isLight: Boolean
    val primary: Color
    val danger: Color
    val disabled: Color
    val background: Color
    val backgroundSecondary: Color
    val text: Color
    val textSecondary: Color
    val borders: Color
    val textBubbleSent: Color
    val backgroundBubbleReceived: Color
    val textBubbleReceived: Color
    val backgroundBubbleSent: Color get() = primary
    val buttonFilled: Color
    val buttonOutline: Color
    val qrCodeContent: Color
    val qrCodeBackground: Color
}

fun sessionColors(
    isLight: Boolean,
    isClassic: Boolean,
    primary: Color
): Palette = when {
    isClassic && isLight -> ::ClassicLight
    isLight -> ::OceanLight
    isClassic -> ::ClassicDark
    else -> ::OceanDark
}(primary)

data class ClassicDark(override val primary: Color = primaryGreen): Palette {
    override val isLight = false
    override val danger = dangerDark
    override val disabled = disabledDark
    override val background = Color.Black
    override val backgroundSecondary = classicDark1
    override val text = Color.White
    override val textSecondary = classicDark5
    override val borders = classicDark3
    override val textBubbleSent = Color.Black
    override val backgroundBubbleReceived = classicDark2
    override val textBubbleReceived = Color.White
    override val buttonFilled = primary
    override val buttonOutline = primary
    override val qrCodeContent = background
    override val qrCodeBackground = text
}

data class ClassicLight(override val primary: Color = primaryGreen): Palette {
    override val isLight = true
    override val danger = dangerLight
    override val disabled = disabledLight
    override val background = Color.White
    override val backgroundSecondary = classicLight5
    override val text = Color.Black
    override val textSecondary = classicLight1
    override val borders = classicLight3
    override val textBubbleSent = Color.Black
    override val backgroundBubbleReceived = classicLight4
    override val textBubbleReceived = classicLight4
    override val buttonFilled = primary
    override val buttonOutline = Color.Black
    override val qrCodeContent = text
    override val qrCodeBackground = backgroundSecondary
}

data class OceanDark(override val primary: Color = primaryBlue): Palette {
    override val isLight = false
    override val danger = dangerDark
    override val disabled = disabledDark
    override val background = oceanDark2
    override val backgroundSecondary = oceanDark1
    override val text = Color.White
    override val textSecondary = oceanDark5
    override val borders = oceanDark4
    override val textBubbleSent = Color.Black
    override val backgroundBubbleReceived = oceanDark4
    override val textBubbleReceived = oceanDark4
    override val buttonFilled = primary
    override val buttonOutline = text
    override val qrCodeContent = background
    override val qrCodeBackground = text
}

data class OceanLight(override val primary: Color = primaryBlue): Palette {
    override val isLight = true
    override val danger = dangerLight
    override val disabled = disabledLight
    override val background = oceanLight7
    override val backgroundSecondary = oceanLight6
    override val text = oceanLight1
    override val textSecondary = oceanLight2
    override val borders = oceanLight3
    override val textBubbleSent = oceanLight1
    override val backgroundBubbleReceived = oceanLight4
    override val textBubbleReceived = oceanLight1
    override val buttonFilled = text
    override val buttonOutline = oceanLight1
    override val qrCodeContent = text
    override val qrCodeBackground = backgroundSecondary
}

@Composable
fun transparentButtonColors() = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent)

@Composable
fun destructiveButtonColors() = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent, contentColor = LocalPalette.current.danger)

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
    @PreviewParameter(SessionColorsParameterProvider::class) palette: Palette
) {
    PreviewTheme(palette) { ThemeColors() }
}

@Composable
private fun ThemeColors() {
    Column {
        Box(Modifier.background(MaterialTheme.colors.primary)) {
            Text("primary", style = base)
        }
        Box(Modifier.background(MaterialTheme.colors.primaryVariant)) {
            Text("primaryVariant", style = base)
        }
        Box(Modifier.background(MaterialTheme.colors.secondary)) {
            Text("secondary", style = base)
        }
        Box(Modifier.background(MaterialTheme.colors.secondaryVariant)) {
            Text("secondaryVariant", style = base)
        }
        Box(Modifier.background(MaterialTheme.colors.surface)) {
            Text("surface", style = base)
        }
        Box(Modifier.background(MaterialTheme.colors.primarySurface)) {
            Text("primarySurface", style = base)
        }
        Box(Modifier.background(MaterialTheme.colors.background)) {
            Text("background", style = base)
        }
        Box(Modifier.background(MaterialTheme.colors.error)) {
            Text("error", style = base)
        }
    }
}

@Composable
fun Palette.outlinedTextFieldColors(
    isError: Boolean
) = TextFieldDefaults.outlinedTextFieldColors(
    textColor = if (isError) danger else text,
    cursorColor = if (isError) danger else text,
    focusedBorderColor = borders,
    unfocusedBorderColor = borders,
    placeholderColor = if (isError) danger else textSecondary
)

val Palette.divider get() = text.copy(alpha = TabRowDefaults.DividerOpacity)

@Composable
fun Palette.radioButtonColors() = RadioButtonDefaults.colors(
    selectedColor = primary,
    unselectedColor = text,
    disabledColor = disabled
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
