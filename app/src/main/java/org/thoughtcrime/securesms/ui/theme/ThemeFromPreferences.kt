package org.thoughtcrime.securesms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.BLUE_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.ORANGE_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.PINK_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.PURPLE_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.RED_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.YELLOW_ACCENT


/**
 * Returns the compose theme based on saved preferences
 * Some behaviour is hardcoded to cater for legacy usage of people with themes already set
 * But future themes will be picked and set directly from the "Appearance" screen
 */
fun TextSecurePreferences.getColorSet(): ThemeColorSet {
    val selectedTheme = getThemeStyle()

    // get the chosen primary color from the preferences
    val selectedPrimary = primaryColor()

    val createLight = if ("ocean" in selectedTheme) ::OceanLight else ::ClassicLight
    val createDark = if ("ocean" in selectedTheme) ::OceanDark else ::ClassicDark

    val followSystemSettings = getFollowSystemSettings()

    return if (followSystemSettings) ThemeColorSet(
        colorsWhenSystemInLight = createLight(selectedPrimary),
        colorsWhenSystemInDark = createDark(selectedPrimary)
    ) else {
        val both = if ("light" in selectedTheme) createLight(selectedPrimary) else createDark(selectedPrimary)

        ThemeColorSet(
            colorsWhenSystemInLight = both,
            colorsWhenSystemInDark = both
        )
    }
}

fun TextSecurePreferences.primaryColor(): Color = when(getSelectedAccentColor()) {
    BLUE_ACCENT -> primaryBlue
    PURPLE_ACCENT -> primaryPurple
    PINK_ACCENT -> primaryPink
    RED_ACCENT -> primaryRed
    ORANGE_ACCENT -> primaryOrange
    YELLOW_ACCENT -> primaryYellow
    else -> primaryGreen
}



