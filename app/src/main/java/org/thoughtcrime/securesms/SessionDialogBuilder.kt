package org.thoughtcrime.securesms

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.LinearLayout.VERTICAL
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.setMargins
import androidx.core.view.updateMargins
import network.loki.messenger.R
import org.thoughtcrime.securesms.util.toPx

class SessionDialogBuilder(val context: Context) {

    private val dialog: AlertDialog = AlertDialog.Builder(context).create()

    private val root = LinearLayout(context).apply { orientation = VERTICAL }
        .also(dialog::setView)

    fun title(@StringRes id: Int) {
        TextView(context, null, 0, R.style.TextAppearance_AppCompat_Title)
            .apply { textAlignment = View.TEXT_ALIGNMENT_CENTER }
            .apply { setText(id) }
            .apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { setMargins(toPx(20, resources)) }
            }.let(root::addView)
    }

    fun text(@StringRes id: Int, style: Int = 0) {
        TextView(context, null, 0, style)
            .apply { textAlignment = View.TEXT_ALIGNMENT_CENTER }
            .apply { setText(id) }
            .apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { toPx(40, resources).let { updateMargins(it, 0, it, 0) } }
            }.let(root::addView)
    }

    fun buttons(build: ButtonsBuilder.() -> Unit) {
        ButtonsBuilder(context, dialog).build(build).let(root::addView)
    }

    fun show(): AlertDialog = dialog.apply { show() }
}

class ButtonsBuilder(val context: Context, val dialog: AlertDialog) {
    val root = LinearLayout(context)

    fun destructiveButton(@StringRes text: Int, @StringRes contentDescription: Int, listener: () -> Unit = {}) {
        button(text, contentDescription, R.style.Widget_Session_Button_Dialog_DestructiveText, listener)
    }

    fun cancelButton() = button(android.R.string.cancel)

    fun button(
        @StringRes text: Int,
        @StringRes contentDescriptionRes: Int = 0,
        @StyleRes style: Int = R.style.Widget_Session_Button_Dialog_UnimportantText,
        listener: (() -> Unit) = {}) {
        Button(context, null, 0, style)
            .apply { setText(text) }
            .apply { setOnClickListener {
                listener.invoke()
                dialog.dismiss()
                contentDescription = resources.getString(contentDescriptionRes)
            } }
            .apply {
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1f)
                    .apply { setMargins(toPx(20, resources)) }
            }
            .let(root::addView)
    }

    internal fun build(build: ButtonsBuilder.() -> Unit): LinearLayout {
        build()
        return root
    }
}

fun Context.sessionDialog(build: SessionDialogBuilder.() -> Unit): AlertDialog =
    SessionDialogBuilder(this).apply { build() }.show()
