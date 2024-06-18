package org.thoughtcrime.securesms.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ButtonElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.color.LocalColors

interface ButtonType {
    @Composable
    fun border(color: Color, enabled: Boolean): BorderStroke?
    @Composable
    fun buttonColors(color: Color): ButtonColors
    val elevation: ButtonElevation? @Composable get

    object Outline: ButtonType {
        @Composable
        override fun border(color: Color, enabled: Boolean) =
            BorderStroke(
                width = LocalDimensions.current.borderStroke,
                color = if (enabled) color else LocalColors.current.disabled
            )
        @Composable
        override fun buttonColors(color: Color) = ButtonDefaults.buttonColors(
            contentColor = color,
            backgroundColor = Color.Unspecified,
            disabledContentColor = LocalColors.current.disabled,
            disabledBackgroundColor = Color.Unspecified
        )
        override val elevation: ButtonElevation? @Composable get() = null
    }

    object Fill: ButtonType {
        @Composable
        override fun border(color: Color, enabled: Boolean) = null
        @Composable
        override fun buttonColors(color: Color) = ButtonDefaults.buttonColors(
            contentColor = LocalColors.current.background,
            backgroundColor = color,
            disabledContentColor = LocalColors.current.disabled,
            disabledBackgroundColor = Color.Unspecified
        )
        override val elevation: ButtonElevation @Composable get() = ButtonDefaults.elevation()
    }
}
