package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.fragment.app.DialogFragment
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.createSessionDialog
import org.thoughtcrime.securesms.dependencies.DatabaseComponent

/** Shown upon sending a message to a user that's blocked. */
class BlockedDialog(private val recipient: Recipient) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        val contactDB = DatabaseComponent.get(requireContext()).sessionContactDatabase()
        val sessionID = recipient.address.toString()
        val contact = contactDB.getContactWithSessionID(sessionID)
        val name = contact?.displayName(Contact.ContactContext.REGULAR) ?: sessionID

        val explanation = resources.getString(R.string.dialog_blocked_explanation, name)
        val spannable = SpannableStringBuilder(explanation)
        val startIndex = explanation.indexOf(name)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startIndex, startIndex + name.count(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        title(resources.getString(R.string.dialog_blocked_title, name))
        text(spannable)
        button(R.string.ConversationActivity_unblock) { unblock() }
        cancelButton { dismiss() }
    }

    private fun unblock() {
        DatabaseComponent.get(requireContext()).recipientDatabase().setBlocked(recipient, false)
        dismiss()
    }
}
