package org.thoughtcrime.securesms.ui

import androidx.compose.material.Colors
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    // To delete and instead use figma terms and add them to the data class above ------
    val backgroundLight get() = if (isLight) backgroundSecondary else Color.White
    val onBackgroundLight get() = if (isLight) text else background
    val button get() = if (isLight) text else primary
    val backgroundBubbleSent get() = primary
    // --------------------------------------------------------------------------------

    @Composable
    fun radioButtonColors() = RadioButtonDefaults.colors(selectedColor = primary, unselectedColor = text, disabledColor = disabled)

    val divider get() = text.copy(alpha = TabRowDefaults.DividerOpacity)

    fun toMaterialColors() = Colors(
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
}