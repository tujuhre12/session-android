package org.thoughtcrime.securesms.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.color.LocalColors

interface ButtonType {
    val contentPadding: PaddingValues get() = ButtonDefaults.ContentPadding

    @Composable
    fun border(color: Color, enabled: Boolean): BorderStroke?
    @Composable
    fun buttonColors(color: Color): ButtonColors

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
    }

    object Borderless: ButtonType {
        override val contentPadding: PaddingValues
            get() = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        @Composable
        override fun border(color: Color, enabled: Boolean) = null
        @Composable
        override fun buttonColors(color: Color) = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            backgroundColor = Color.Transparent,
            disabledContentColor = LocalColors.current.disabled
        )
    }
}
