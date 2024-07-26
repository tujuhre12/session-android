package org.thoughtcrime.securesms.ui.theme

/**
 * This class holds two instances of [ThemeColors], [light] representing the [ThemeColors] to use when the system is in a
 * light theme, and [dark] representing the [ThemeColors] to use when the system is in a dark theme.
 */
data class ThemeColorSet(
    val colorsWhenSystemInLight: ThemeColors,
    val colorsWhenSystemInDark: ThemeColors
) {
    fun get(isDark: Boolean): ThemeColors = if (isDark) colorsWhenSystemInDark else colorsWhenSystemInLight
}
