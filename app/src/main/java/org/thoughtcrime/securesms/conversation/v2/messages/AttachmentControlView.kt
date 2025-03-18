package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewAttachmentControlBinding
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
class AttachmentControlView: LinearLayout {
    private val binding by lazy { ViewAttachmentControlBinding.bind(this) }
    enum class AttachmentType {
        VOICE,
        AUDIO,
        DOCUMENT,
        IMAGE,
        VIDEO,
    }
    //todo: ATTACHMENT Handle multiple images case (icon plus total size)

    sealed class AttachmentState {
        object Loading: AttachmentState()
        object Pending: AttachmentState()
        object Failed: AttachmentState()
        object Expired: AttachmentState()
    }

    // region Lifecycle
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // endregion
    @Inject lateinit var storage: StorageProtocol

    val errorColor by lazy {
        context.getColorFromAttr(R.attr.danger)
    }

    // region Updating
    private fun getAttachmentData(attachmentType: AttachmentType): Pair<Int, Int> {
        return when (attachmentType) {
            AttachmentType.VOICE -> Pair(R.string.messageVoice, R.drawable.ic_mic)
            AttachmentType.AUDIO -> Pair(R.string.audio, R.drawable.ic_volume_2)
            AttachmentType.DOCUMENT -> Pair(R.string.document, R.drawable.ic_file)
            AttachmentType.IMAGE -> Pair(R.string.image, R.drawable.ic_image)
            AttachmentType.VIDEO -> Pair(R.string.video, R.drawable.ic_square_play)
        }
    }

    fun bind(attachmentType: AttachmentType, @ColorInt textColor: Int, attachment: DatabaseAttachment?, state: AttachmentState) {
        val (stringRes, iconRes) = getAttachmentData(attachmentType)

        binding.pendingDownloadIcon.setImageResource(iconRes)

        when(state){
            AttachmentState.Expired -> {
                val expiredColor = textColor.also { alpha = 0.7f }

                binding.pendingDownloadIcon.setColorFilter(expiredColor)
                binding.pendingDownloadSize.isVisible = false

                binding.pendingDownloadTitle.apply {
                    text = context.getString(R.string.attachmentsExpired)
                    setTextColor(expiredColor)
                    setTypeface(typeface, android.graphics.Typeface.ITALIC)
                }

                binding.separator.isVisible = false
                binding.pendingDownloadSubtitle.isVisible = false
            }

            AttachmentState.Loading -> {
                binding.pendingDownloadIcon.setColorFilter(textColor)

                //todo: ATTACHMENT This will need to be tweaked to dynamically show the the downloaded amount
                binding.pendingDownloadSize.apply {
                    text = attachment?.displaySize()
                    setTextColor(textColor)
                    isVisible = true
                }

                binding.pendingDownloadTitle.apply{
                    text = context.getString(R.string.downloading)
                    setTextColor(textColor)
                    setTypeface(typeface, android.graphics.Typeface.NORMAL)
                }

                binding.separator.apply {
                    imageTintList = ColorStateList.valueOf(textColor)
                    isVisible = true
                }

                binding.pendingDownloadSubtitle.isVisible = false
            }

            AttachmentState.Failed -> {
                binding.pendingDownloadIcon.setColorFilter(textColor)

                binding.pendingDownloadSize.apply {
                    text = attachment?.displaySize()
                    setTextColor(errorColor)
                    isVisible = true
                }

                //todo: ATTACHMENT we need the 'tap to retry' string in crowdin
                binding.pendingDownloadTitle.apply{
                    text = "Failed to download"//context.getString(R.string.errorUnknown) //todo: ATTACHMENT we  need the right text in crowdin
                    setTextColor(errorColor)
                    setTypeface(typeface, android.graphics.Typeface.NORMAL)
                }

                binding.separator.apply {
                    imageTintList = ColorStateList.valueOf(errorColor)
                    isVisible = true
                }

                binding.pendingDownloadSubtitle.isVisible = true
            }

            else -> {
                binding.pendingDownloadIcon.setColorFilter(textColor)

                binding.pendingDownloadSize.apply {
                    text = attachment?.displaySize()
                    setTextColor(textColor)
                    isVisible = true
                }

                binding.pendingDownloadTitle.apply{
                    text = Phrase.from(context, R.string.attachmentsTapToDownload)
                        .put(FILE_TYPE_KEY, context.getString(stringRes).lowercase(Locale.ROOT))
                        .format()
                    setTextColor(textColor)
                    setTypeface(typeface, android.graphics.Typeface.NORMAL)
                }

                binding.separator.apply {
                    imageTintList = ColorStateList.valueOf(textColor)
                    isVisible = true
                }

                binding.pendingDownloadSubtitle.isVisible = false
            }
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