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
val TextSecurePreferences.colors: @Composable () -> ThemeColors get() {
    val selectedTheme = getThemeStyle()

    // get the chosen primary color from the preferences
    val selectedPrimary = primaryColor()

    val isOcean = "ocean" in selectedTheme

    val createLight = if (isOcean) ::OceanLight else ::ClassicLight
    val createDark = if (isOcean) ::OceanDark else ::ClassicDark

    // create the light and dark themes outside the lambda to avoid creating them every time
    // [SessionMaterialTheme] is called. Creating both when we don't followSystemSettings is but a
    // minor inefficiency that increases readability.
    val light = createLight(selectedPrimary)
    val dark = createDark(selectedPrimary)

    return when {
        getFollowSystemSettings() -> { { if (isSystemInDarkTheme()) dark else light } }
        "light" in selectedTheme -> { { light } }
        else -> { { dark } }
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



