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
import org.thoughtcrime.securesms.ui.ThemeColors
import org.thoughtcrime.securesms.ui.LightDarkColors

/**
 * Retrieve the current [ThemeColors] from [TextSecurePreferences] and current system settings.
 */
@Composable
fun TextSecurePreferences.colors(): ThemeColors = lightDarkColors().colors()
private fun TextSecurePreferences.lightDarkColors() = LightDarkColors(isClassic(), isLight(), getFollowSystemSettings(), primaryColor())
private fun TextSecurePreferences.isLight(): Boolean = getThemeStyle() in setOf(CLASSIC_LIGHT, OCEAN_LIGHT)
private fun TextSecurePreferences.isClassic(): Boolean = getThemeStyle() in setOf(CLASSIC_DARK, CLASSIC_LIGHT)
private fun TextSecurePreferences.primaryColor(): Color = when(getSelectedAccentColor()) {
    GREEN_ACCENT -> org.thoughtcrime.securesms.ui.primaryGreen
    BLUE_ACCENT -> org.thoughtcrime.securesms.ui.primaryBlue
    PURPLE_ACCENT -> org.thoughtcrime.securesms.ui.primaryPurple
    PINK_ACCENT -> org.thoughtcrime.securesms.ui.primaryPink
    RED_ACCENT -> org.thoughtcrime.securesms.ui.primaryRed
    ORANGE_ACCENT -> org.thoughtcrime.securesms.ui.primaryOrange
    YELLOW_ACCENT -> org.thoughtcrime.securesms.ui.primaryYellow
    else -> Color.Unspecified
}
