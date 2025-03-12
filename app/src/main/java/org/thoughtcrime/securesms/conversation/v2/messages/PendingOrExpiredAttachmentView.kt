package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewPendingAttachmentBinding
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.utilities.StringSubstitutionConstants.FILE_TYPE_KEY
import org.session.libsession.utilities.getColorFromAttr
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.dialogs.AutoDownloadDialog
import org.thoughtcrime.securesms.util.ActivityDispatcher
import org.thoughtcrime.securesms.util.displaySize
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class PendingOrExpiredAttachmentView: LinearLayout {
    private val binding by lazy { ViewPendingAttachmentBinding.bind(this) }
    enum class AttachmentType {
        AUDIO,
        DOCUMENT,
        IMAGE,
        VIDEO,
    }

    // region Lifecycle
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    val expiredColor by lazy { context.getColorFromAttr(android.R.attr.textColorTertiary) }

    // endregion
    @Inject lateinit var storage: StorageProtocol

    // region Updating
    fun bind(attachmentType: AttachmentType, @ColorInt textColor: Int, attachment: DatabaseAttachment?, expired: Boolean = false) {
        val stringRes = when (attachmentType) {
            AttachmentType.AUDIO -> R.string.audio
            AttachmentType.DOCUMENT -> R.string.document
            AttachmentType.IMAGE -> R.string.image
            AttachmentType.VIDEO -> R.string.video
        }

        if(expired){
            binding.pendingDownloadIcon.setColorFilter(expiredColor)
            binding.pendingDownloadSize.isVisible = false
            binding.pendingDownloadTitle.text = context.getString(R.string.attachmentsExpired)
            binding.pendingDownloadTitle.setTextColor(expiredColor)
            binding.separator.isVisible = false
        }
        else {
            binding.pendingDownloadIcon.setColorFilter(textColor)
            binding.pendingDownloadSize.isVisible = true
            binding.pendingDownloadSize.text = attachment?.displaySize()
            binding.pendingDownloadTitle.text = Phrase.from(context, R.string.attachmentsTapToDownload)
                .put(FILE_TYPE_KEY, context.getString(stringRes).lowercase(Locale.ROOT))
                .format()
            binding.pendingDownloadTitle.setTextColor(textColor)
            binding.separator.isVisible = true
        }
    }
    // endregion

    // region Interaction
    fun showDownloadDialog(threadRecipient: Recipient, attachment: DatabaseAttachment) {
        if (!storage.shouldAutoDownloadAttachments(threadRecipient)) {
            // just download
            ActivityDispatcher.get(context)?.showDialog(AutoDownloadDialog(threadRecipient, attachment))
        }
    }
}