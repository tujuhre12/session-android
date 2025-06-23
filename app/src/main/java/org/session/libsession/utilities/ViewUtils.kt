package org.session.libsession.utilities

import android.content.Context
import android.text.BoringLayout
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import kotlin.math.ceil

@ColorInt
fun Context.getColorFromAttr(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true
): Int {
    theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}

inline fun <reified LP: ViewGroup.LayoutParams> View.modifyLayoutParams(function: LP.() -> Unit) {
    layoutParams = (layoutParams as LP).apply { function() }
}

fun TextView.isEllipsized(): Boolean {
    val textPixelLength = paint.measureText(text.toString())
    val numberOfLines = ceil((textPixelLength / measuredWidth).toDouble())
    return lineHeight * numberOfLines > measuredHeight
    //todo pro can this be calculated better? BoringLayout?
}
