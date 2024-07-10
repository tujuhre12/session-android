package org.thoughtcrime.securesms.ui.color

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.BLUE_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.CLASSIC_DARK
import org.session.libsession.utilities.TextSecurePreferences.Companion.CLASSIC_LIGHT
import org.session.libsession.utilities.TextSecurePreferences.Companion.GREEN_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.OCEAN_LIGHT
import org.session.libsession.utilities.TextSecurePreferences.Companion.ORANGE_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.PINK_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.PURPLE_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.RED_ACCENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.YELLOW_ACCENT
import org.thoughtcrime.securesms.ui.theme.ThemeColors
import org.thoughtcrime.securesms.ui.theme.LightDarkColors
import org.thoughtcrime.securesms.ui.theme.primaryBlue
import org.thoughtcrime.securesms.ui.theme.primaryGreen
import org.thoughtcrime.securesms.ui.theme.primaryOrange
import org.thoughtcrime.securesms.ui.theme.primaryPink
import org.thoughtcrime.securesms.ui.theme.primaryPurple
import org.thoughtcrime.securesms.ui.theme.primaryRed
import org.thoughtcrime.securesms.ui.theme.primaryYellow

/**
 * Retrieve the current [ThemeColors] from [TextSecurePreferences] and current system settings.
 */
@Composable
fun TextSecurePreferences.colors(): ThemeColors = lightDarkColors().colors()
private fun TextSecurePreferences.lightDarkColors() = LightDarkColors(isClassic(), isLight(), getFollowSystemSettings(), primaryColor())
private fun TextSecurePreferences.isLight(): Boolean = getThemeStyle() in setOf(CLASSIC_LIGHT, OCEAN_LIGHT)
private fun TextSecurePreferences.isClassic(): Boolean = getThemeStyle() in setOf(CLASSIC_DARK, CLASSIC_LIGHT)
private fun TextSecurePreferences.primaryColor(): Color = when(getSelectedAccentColor()) {
    GREEN_ACCENT -> primaryGreen
    BLUE_ACCENT -> primaryBlue
    PURPLE_ACCENT -> primaryPurple
    PINK_ACCENT -> primaryPink
    RED_ACCENT -> primaryRed
    ORANGE_ACCENT -> primaryOrange
    YELLOW_ACCENT -> primaryYellow
    else -> Color.Unspecified
}
