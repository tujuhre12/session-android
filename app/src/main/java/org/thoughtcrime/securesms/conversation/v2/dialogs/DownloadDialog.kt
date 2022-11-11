package org.thoughtcrime.securesms.conversation.v2.dialogs

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.DialogDownloadBinding
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog
import org.thoughtcrime.securesms.database.SessionContactDatabase
import javax.inject.Inject

/** Shown when receiving media from a contact for the first time, to confirm that
 * they are to be trusted and files sent by them are to be downloaded. */
@AndroidEntryPoint
class AutoDownloadDialog(private val threadRecipient: Recipient,
                     private val databaseAttachment: DatabaseAttachment
) : BaseDialog() {

    @Inject lateinit var storage: StorageProtocol
    @Inject lateinit var contactDB: SessionContactDatabase

    override fun setContentView(builder: AlertDialog.Builder) {
        val binding = DialogDownloadBinding.inflate(LayoutInflater.from(requireContext()))
        val threadId = storage.getThreadId(threadRecipient) ?: run {
            dismiss()
            return
        }

        val displayName = when {
            threadRecipient.isOpenGroupRecipient -> storage.getOpenGroup(threadId)?.name ?: "UNKNOWN"
            threadRecipient.isClosedGroupRecipient -> storage.getGroup(threadRecipient.address.toGroupString())?.title ?: "UNKNOWN"
            else -> storage.getContactWithSessionID(threadRecipient.address.serialize())?.displayName(Contact.ContactContext.REGULAR) ?: "UNKNOWN"
        }
        val title = resources.getString(R.string.dialog_auto_download_title)
        binding.downloadTitleTextView.text = title
        val explanation = resources.getString(R.string.dialog_auto_download_explanation, displayName)
        val spannable = SpannableStringBuilder(explanation)
        val startIndex = explanation.indexOf(displayName)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startIndex, startIndex + displayName.count(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.downloadExplanationTextView.text = spannable
        binding.no.setOnClickListener {
            setAutoDownload(false)
            dismiss()
        }
        binding.yes.setOnClickListener {
            setAutoDownload(true)
            dismiss()
        }
        builder.setView(binding.root)
    }

    private fun setAutoDownload(shouldDownload: Boolean) {
        storage.setAutoDownloadAttachments(threadRecipient, shouldDownload)
    }
}