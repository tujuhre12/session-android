package org.thoughtcrime.securesms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

fun interface MaybeFollowSystemColors {
    @Composable
    fun get(): ThemeColors
}

fun FollowSystemColors(light: ThemeColors, dark: ThemeColors) = MaybeFollowSystemColors {
    when {
        isSystemInDarkTheme() -> dark
        else -> light
    }
}

fun IgnoreSystemColors(colors: ThemeColors) = MaybeFollowSystemColors { colors }
