package org.thoughtcrime.securesms.ui

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import com.google.android.material.color.MaterialColors
import network.loki.messenger.R
import org.thoughtcrime.securesms.conversation.v2.PreviewMessageDetails

val LocalExtraColors = staticCompositionLocalOf<ExtraColors> { error("No Custom Attribute value provided") }

data class ExtraColors(
    val settingsBackground: Color,
)

fun Context.getColorFromTheme(@AttrRes attr: Int, defaultValue: Int = 0x0): Color = try {
    MaterialColors.getColor(this, attr, defaultValue).let(::Color)
} catch (e: Exception) {
    colorDestructive
}

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val extraColors = LocalContext.current.run {
        ExtraColors(
            settingsBackground = getColorFromTheme(R.attr.colorSettingsBackground),
        )
    }

    CompositionLocalProvider(LocalExtraColors provides extraColors) {
        AppCompatTheme {
            content()
        }
    }
}

@Preview
@Composable
fun PreviewMessageDetails(
    @PreviewParameter(ThemeResPreviewParameterProvider::class) themeResId: Int
) {
    Theme(themeResId) {
        Box(modifier = Modifier.background(color = MaterialTheme.colors.background)) {
            PreviewMessageDetails()
        }
    }
}

@Composable
fun Theme(@StyleRes themeResId: Int, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContext provides ContextThemeWrapper(LocalContext.current, themeResId)
    ) {
        AppTheme {
            content()
        }
    }
}

class ThemeResPreviewParameterProvider : PreviewParameterProvider<Int> {
    override val values = sequenceOf(
        R.style.Classic_Dark,
        R.style.Classic_Light,
        R.style.Ocean_Dark,
        R.style.Ocean_Light,
    )
}
