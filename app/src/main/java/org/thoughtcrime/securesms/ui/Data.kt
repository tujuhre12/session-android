package org.thoughtcrime.securesms.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Compatibility class to allow ViewModels to use strings and string resources interchangeably.
 */
sealed class GetString {
    @Composable
    abstract fun string(): String
    data class FromString(val string: String): GetString() {
        @Composable
        override fun string(): String = string
    }
    data class FromResId(@StringRes val resId: Int): GetString() {
        @Composable
        override fun string(): String = stringResource(resId)

    }
}

fun GetString(@StringRes resId: Int) = GetString.FromResId(resId)
fun GetString(string: String) = GetString.FromString(string)


/**
 * Represents some text with an associated title.
 */
data class TitledText(val title: GetString, val text: String) {
    constructor(title: String, text: String): this(GetString(title), text)
    constructor(@StringRes title: Int, text: String): this(GetString(title), text)
}
