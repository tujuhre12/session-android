package org.thoughtcrime.securesms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

fun interface MaybeFollowSystemColors {
    @Composable
    fun get(): ThemeColors
}

@Suppress("FunctionName")
fun FollowSystemColors(light: ThemeColors, dark: ThemeColors) = MaybeFollowSystemColors {
    when {
        isSystemInDarkTheme() -> dark
        else -> light
    }
}

@Suppress("FunctionName")
fun IgnoreSystemColors(colors: ThemeColors) = MaybeFollowSystemColors { colors }
