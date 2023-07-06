package org.thoughtcrime.securesms.ui

import android.content.Context
import androidx.annotation.AttrRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import com.google.android.material.color.MaterialColors
import network.loki.messenger.R

val LocalExtraColors = staticCompositionLocalOf<ExtraColors> { error("No Custom Attribute value provided") }

data class ExtraColors(
    val cell: Color,
    val divider: Color,
    val settingsBackground: Color,
)

fun Context.getColorFromTheme(@AttrRes attr: Int, defaultValue: Int = 0x0): Color =
    MaterialColors.getColor(this, attr, defaultValue).let(::Color)

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val extraColors = LocalContext.current.run {
        ExtraColors(
            cell = getColorFromTheme(R.attr.colorCellBackground),
            divider = getColorFromTheme(R.attr.dividerHorizontal).copy(alpha = 0.15f),
            settingsBackground = getColorFromTheme(R.attr.colorSettingsBackground)
        )
    }

    CompositionLocalProvider(LocalExtraColors provides extraColors) {
        AppCompatTheme {
            content()
        }
    }
}
