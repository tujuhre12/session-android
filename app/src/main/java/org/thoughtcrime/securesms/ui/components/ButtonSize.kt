package org.thoughtcrime.securesms.ui.components

import android.annotation.SuppressLint
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.ui.baseBold
import org.thoughtcrime.securesms.ui.extraSmallBold

interface ButtonSize {
    @OptIn(ExperimentalMaterialApi::class)
    @SuppressLint("ComposableNaming")
    @Composable fun applyButtonConstraints(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentEnforcement provides false,
            content = content
        )
    }

    @SuppressLint("ComposableNaming")
    @Composable fun applyTextConstraints(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalTextStyle provides textStyle,
            content = content
        )
    }

    val textStyle: TextStyle
    val minHeight: Dp

    object Large: ButtonSize {
        override val textStyle = baseBold
        override val minHeight = 41.dp
    }

    object Slim: ButtonSize {
        override val textStyle = extraSmallBold
        override val minHeight = 29.dp
    }

}
