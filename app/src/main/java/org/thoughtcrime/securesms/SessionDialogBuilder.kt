package org.thoughtcrime.securesms

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.LinearLayout.VERTICAL
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.setMargins
import androidx.core.view.setPadding
import androidx.core.view.updateMargins
import network.loki.messenger.R
import org.thoughtcrime.securesms.util.toPx
import java.lang.ref.Reference


@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class DialogDsl

@DialogDsl
class SessionDialogBuilder(val context: Context) {

    private val dialogBuilder: AlertDialog.Builder = AlertDialog.Builder(context)

    private var dialog: AlertDialog? = null
    private fun dismiss() = dialog?.dismiss()

    private val topView = LinearLayout(context).apply { orientation = VERTICAL }
        .also(dialogBuilder::setCustomTitle)

    private val root = LinearLayout(context).apply { orientation = VERTICAL }
        .also(dialogBuilder::setView)

    fun title(@StringRes id: Int) = title(context.getString(id))

    fun title(text: CharSequence?) = title(text?.toString())
    fun title(text: String?) {
        text(text, R.style.TextAppearance_AppCompat_Title) { setPadding(toPx(20, resources)) }
    }

    fun text(@StringRes id: Int, style: Int = 0) = text(context.getString(id), style)
    fun text(text: CharSequence?) = text(text?.toString())
    fun text(text: String?, @StyleRes style: Int = 0) {
        text(text, style) {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { toPx(40, resources).let { updateMargins(it, 0, it, 0) } }
        }
    }

    private fun text(text: String?, @StyleRes style: Int, modify: TextView.() -> Unit) {
        text ?: return
        TextView(context, null, 0, style)
            .apply {
                setText(text)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                modify()
            }.let(topView::addView)
    }

    fun view(view: View) {
        dialogBuilder.setView(view)
    }

    fun view(@LayoutRes layout: Int) {
        dialogBuilder.setView(layout)
    }

    fun setIconAttribute(@AttrRes icon: Int) {
        dialogBuilder.setIconAttribute(icon)
    }

    fun singleChoiceItems(
        options: Array<String>,
        currentSelected: Int,
        onSelect: (Int) -> Unit
    ) {
        dialogBuilder.setSingleChoiceItems(
            options,
            currentSelected
        ) { dialog, it -> onSelect(it); dialog.dismiss() }
    }

    fun buttons(build: (@DialogDsl ButtonsBuilder).() -> Unit) {
        ButtonsBuilder(context, ::dismiss).build(build).let(root::addView)
    }

    fun show(): AlertDialog = dialogBuilder.show()
}

@DialogDsl
class ButtonsBuilder(val context: Context, val dismiss: () -> Unit) {
    val root = LinearLayout(context)

    fun destructiveButton(
        @StringRes text: Int,
        @StringRes contentDescription: Int,
        listener: () -> Unit = {}
    ) {
        button(
            text,
            contentDescription,
            R.style.Widget_Session_Button_Dialog_DestructiveText,
            listener
        )
    }

    fun cancelButton(listener: (() -> Unit) = {}) = button(android.R.string.cancel, R.string.AccessibilityId_cancel_button, listener = listener)

    fun button(
        @StringRes text: Int,
        @StringRes contentDescriptionRes: Int = 0,
        @StyleRes style: Int = R.style.Widget_Session_Button_Dialog_UnimportantText,
        listener: (() -> Unit) = {}
    ) {
        Button(context, null, 0, style)
            .apply {
                setText(text)
                contentDescription = resources.getString(contentDescriptionRes)
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1f)
                    .apply { setMargins(toPx(20, resources)) }
                setOnClickListener {
                    listener.invoke()
                    dismiss()
                }
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
