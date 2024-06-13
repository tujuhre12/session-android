package org.thoughtcrime.securesms.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment

fun Activity.setComposeContent(content: @Composable () -> Unit) {
    ComposeView(this)
        .apply { setContent { SessionMaterialTheme { content() } } }
        .let(::setContentView)
}

fun Fragment.onCreateView(content: @Composable () -> Unit): ComposeView = ComposeView(requireContext()).apply {
    setContent {
        SessionMaterialTheme {
            content()
        }
    }
}

fun ComposeView.setContentWithTheme(content: @Composable () -> Unit) = setContent {
    SessionMaterialTheme {
        content()
    }
}
