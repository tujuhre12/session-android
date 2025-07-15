package org.thoughtcrime.securesms.util

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.widget.TextView
import androidx.core.content.ContextCompat
import network.loki.messenger.R

object UnreadStylingHelper {

    fun getUnreadBackground(context: Context, isUnread: Boolean): Drawable? {
        val drawableRes = if (isUnread) {
            ContextCompat.getDrawable(context, R.drawable.conversation_unread_background)
        } else {
            ContextCompat.getDrawable(context, R.drawable.conversation_view_background)
        }

        return drawableRes
    }

    fun formatUnreadCount(unreadCount: Int): String? {
        return when {
            unreadCount == 0 -> null
            unreadCount < 10000 -> unreadCount.toString()
            else -> "9999+"
        }
    }

    fun getUnreadTextSize(unreadCount: Int): Float {
        return if (unreadCount < 1000) 12.0f else 10.0f
    }

    fun applyUnreadTextStyle(
        textView: TextView,
        unreadCount: Int,
        isRead: Boolean
    ) {
        textView.typeface =
            if (unreadCount > 0 && !isRead) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
}