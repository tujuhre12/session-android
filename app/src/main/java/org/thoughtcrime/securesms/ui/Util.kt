package org.thoughtcrime.securesms.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView

fun Activity.setComposeContent(content: @Composable () -> Unit) {
    ComposeView(this)
        .apply { setContent { AppTheme { content() } } }
        .let(::setContentView)
}
