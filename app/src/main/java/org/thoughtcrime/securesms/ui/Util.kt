package org.thoughtcrime.securesms.ui

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.ui.theme.SessionMaterialTheme

fun Activity.setComposeContent(content: @Composable () -> Unit) {
    ComposeView(this)
        .apply { setThemedContent(content) }
        .let(::setContentView)
}

fun Fragment.createThemedComposeView(content: @Composable () -> Unit): ComposeView = requireContext().createThemedComposeView(content)
fun Context.createThemedComposeView(content: @Composable () -> Unit): ComposeView = ComposeView(this).apply {
    setThemedContent(content)
}

fun ComposeView.setThemedContent(content: @Composable () -> Unit) = setContent {
    SessionMaterialTheme {
        content()
    }
}

/**
 * This is used to set the test tag that the QA team can use to retrieve an element in appium
 * In order to do so we need to set the testTagsAsResourceId to true, which ideally should be done only once
 * in the root composable, but our app is currently made up of  multiple isolated composables
 * set up in the old activity/fragment view system
 * As such we need to repeat it for every component that wants to use testTag, until such
 * a time as we have one root composable
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.qaTag(tag: String) = semantics { testTagsAsResourceId = true }.testTag(tag)