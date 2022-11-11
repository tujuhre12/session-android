package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewPendingAttachmentBinding
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.dialogs.AutoDownloadDialog
import org.thoughtcrime.securesms.util.ActivityDispatcher
import org.thoughtcrime.securesms.util.createAndStartAttachmentDownload
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class PendingAttachmentView: LinearLayout {
    private val binding by lazy { ViewPendingAttachmentBinding.bind(this) }
    enum class AttachmentType {
        AUDIO,
        DOCUMENT,
        MEDIA
    }

    // region Lifecycle
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // endregion
    @Inject lateinit var storage: StorageProtocol

    // region Updating
    fun bind(attachmentType: AttachmentType, @ColorInt textColor: Int, attachment: DatabaseAttachment) {
        val (iconRes, stringRes) = when (attachmentType) {
            AttachmentType.AUDIO -> R.drawable.ic_microphone to R.string.Slide_audio
            AttachmentType.DOCUMENT -> R.drawable.ic_document_large_light to R.string.document
            AttachmentType.MEDIA -> R.drawable.ic_image_white_24dp to R.string.media
        }
        val iconDrawable = ContextCompat.getDrawable(context,iconRes)!!
        iconDrawable.mutate().setTint(textColor)
        val text = context.getString(R.string.UntrustedAttachmentView_download_attachment, context.getString(stringRes).toLowerCase(Locale.ROOT))

        binding.untrustedAttachmentIcon.setImageDrawable(iconDrawable)
        binding.untrustedAttachmentTitle.text = text
    }
    // endregion

    // region Interaction
    fun showDownloadDialog(threadRecipient: Recipient, attachment: DatabaseAttachment) {
        JobQueue.shared.createAndStartAttachmentDownload(attachment)
        if (!storage.hasAutoDownloadFlagBeenSet(threadRecipient)) {
            // just download
            ActivityDispatcher.get(context)?.showDialog(AutoDownloadDialog(threadRecipient, attachment))
        }
    }

}