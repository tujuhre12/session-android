package org.thoughtcrime.securesms.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter

interface ThemeColors {
    // properties to override for each theme
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
    val qrCodeContent: Color
    val qrCodeBackground: Color
    val primaryButtonFill: Color
    val primaryButtonFillText: Color
}

// extra functions and properties that work for all themes
val ThemeColors.textSelectionColors get() = TextSelectionColors(
    handleColor = primary,
    backgroundColor = primary.copy(alpha = 0.5f)
)

val ThemeColors.divider get() = text.copy(alpha = TabRowDefaults.DividerOpacity)

fun ThemeColors.text(isError: Boolean): Color = if (isError) danger else text
fun ThemeColors.textSecondary(isError: Boolean): Color = if (isError) danger else textSecondary
fun ThemeColors.borders(isError: Boolean): Color = if (isError) danger else borders

fun ThemeColors.toMaterialColors() = androidx.compose.material.Colors(
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


@Composable
fun ThemeColors.radioButtonColors() = RadioButtonDefaults.colors(
    selectedColor = primary,
    unselectedColor = text,
    disabledColor = disabled
)

@Composable
fun transparentButtonColors() = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent)

@Composable
fun dangerButtonColors() = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent, contentColor = LocalColors.current.danger)


// Our themes
data class ClassicDark(override val primary: Color = primaryGreen): ThemeColors {
    override val isLight = false
    override val danger = dangerDark
    override val disabled = disabledDark
    override val background = classicDark0
    override val backgroundSecondary = classicDark1
    override val text = classicDark6
    override val textSecondary = classicDark5
    override val borders = classicDark3
    override val textBubbleSent = Color.Black
    override val backgroundBubbleReceived = classicDark2
    override val textBubbleReceived = Color.White
    override val qrCodeContent = background
    override val qrCodeBackground = text
    override val primaryButtonFill = primary
    override val primaryButtonFillText = Color.Black
}

data class ClassicLight(override val primary: Color = primaryGreen): ThemeColors {
    override val isLight = true
    override val danger = dangerLight
    override val disabled = disabledLight
    override val background = classicLight6
    override val backgroundSecondary = classicLight5
    override val text = classicLight0
    override val textSecondary = classicLight1
    override val borders = classicLight3
    override val textBubbleSent = text
    override val backgroundBubbleReceived = classicLight4
    override val textBubbleReceived = classicLight4
    override val qrCodeContent = text
    override val qrCodeBackground = backgroundSecondary
    override val primaryButtonFill = text
    override val primaryButtonFillText = Color.White
}

data class OceanDark(override val primary: Color = primaryBlue): ThemeColors {
    override val isLight = false
    override val danger = dangerDark
    override val disabled = disabledDark
    override val background = oceanDark2
    override val backgroundSecondary = oceanDark1
    override val text = oceanDark7
    override val textSecondary = oceanDark5
    override val borders = oceanDark4
    override val textBubbleSent = Color.Black
    override val backgroundBubbleReceived = oceanDark4
    override val textBubbleReceived = oceanDark4
    override val qrCodeContent = background
    override val qrCodeBackground = text
    override val primaryButtonFill = primary
    override val primaryButtonFillText = Color.Black
}

data class OceanLight(override val primary: Color = primaryBlue): ThemeColors {
    override val isLight = true
    override val danger = dangerLight
    override val disabled = disabledLight
    override val background = oceanLight7
    override val backgroundSecondary = oceanLight6
    override val text = oceanLight1
    override val textSecondary = oceanLight2
    override val borders = oceanLight3
    override val textBubbleSent = text
    override val backgroundBubbleReceived = oceanLight4
    override val textBubbleReceived = oceanLight1
    override val qrCodeContent = text
    override val qrCodeBackground = backgroundSecondary
    override val primaryButtonFill = text
    override val primaryButtonFillText = Color.White
}

@Preview
@Composable
fun PreviewThemeColors(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) { ThemeColors() }
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