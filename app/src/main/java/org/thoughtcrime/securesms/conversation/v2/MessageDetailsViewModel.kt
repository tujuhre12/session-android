package org.thoughtcrime.securesms.conversation.v2

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.messaging.jobs.AttachmentDownloadJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentTransferProgress
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.utilities.Util
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.MediaPreviewArgs
import org.thoughtcrime.securesms.database.AttachmentDatabase
import org.thoughtcrime.securesms.database.LokiMessageDatabase
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.ImageSlide
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.TitledText
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class MessageDetailsViewModel @Inject constructor(
    private val attachmentDb: AttachmentDatabase,
    private val lokiMessageDatabase: LokiMessageDatabase,
    private val mmsSmsDatabase: MmsSmsDatabase,
    private val threadDb: ThreadDatabase,
) : ViewModel() {

    private var _state = MutableLiveData(MessageDetailsState())
    val state: LiveData<MessageDetailsState> = _state

    private var _event = MutableLiveData<MediaPreviewArgs>()
    val event: LiveData<MediaPreviewArgs> = _event

    fun setMessageTimestamp(timestamp: Long) {
        val record = mmsSmsDatabase.getMessageForTimestamp(timestamp) ?: return
        val mmsRecord = record as? MmsMessageRecord

        _state.value = record.run {
            val slides = mmsRecord?.slideDeck?.slides ?: emptyList()

            MessageDetailsState(
                attachments = slides.map(::Attachment),
                record = record,
                sent = dateSent.let(::Date).toString().let { TitledText(R.string.message_details_header__sent, it) },
                received = dateReceived.let(::Date).toString().let { TitledText(R.string.message_details_header__received, it) },
                error = lokiMessageDatabase.getErrorMessage(id)?.let { TitledText(R.string.message_details_header__error, it) },
                senderInfo = individualRecipient.run { name?.let { TitledText(it, address.serialize()) } },
                sender = individualRecipient,
                thread = threadDb.getRecipientForThreadId(threadId)!!,
            )
        }
    }

    private val Slide.details: List<TitledText>
        get() = listOfNotNull(
            fileName.orNull()?.let { TitledText(R.string.message_details_header__file_id, it) },
            TitledText(R.string.message_details_header__file_type, asAttachment().contentType),
            TitledText(R.string.message_details_header__file_size, Util.getPrettyFileSize(fileSize)),
            takeIf { it is ImageSlide }
                ?.let(Slide::asAttachment)
                ?.run { "${width}x$height" }
                ?.let { TitledText(R.string.message_details_header__resolution, it) },
            attachmentDb.duration(this)?.let { TitledText(R.string.message_details_header__duration, it) },
        )

    private fun AttachmentDatabase.duration(slide: Slide): String? =
        slide.takeIf { it.hasAudio() }
            ?.run { asAttachment() as? DatabaseAttachment }
            ?.run { getAttachmentAudioExtras(attachmentId)?.durationMs }
            ?.takeIf { it > 0 }
            ?.let {
                String.format(
                    "%01d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(it),
                    TimeUnit.MILLISECONDS.toSeconds(it) % 60
                )
            }

    fun Attachment(slide: Slide): Attachment =
        Attachment(slide.details, slide.fileName.orNull(), slide.uri, slide is ImageSlide)

    fun onClickImage(index: Int) {
        val state = state.value ?: return
        val mmsRecord = state.mmsRecord ?: return
        val slide = mmsRecord.slideDeck.slides[index] ?: return
        // only open to downloaded images
        if (slide.transferState == AttachmentTransferProgress.TRANSFER_PROGRESS_FAILED) {
            // Restart download here (on IO thread)
            (slide.asAttachment() as? DatabaseAttachment)?.let { attachment ->
                onAttachmentNeedsDownload(attachment.attachmentId.rowId, state.mmsRecord.getId())
            }
        }

        if (slide.isInProgress) return

        _event.value = MediaPreviewArgs(slide, state.mmsRecord, state.thread)
    }


    fun onAttachmentNeedsDownload(attachmentId: Long, mmsId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            JobQueue.shared.add(AttachmentDownloadJob(attachmentId, mmsId))
        }
    }
}

data class MessageDetailsState(
    val attachments: List<Attachment> = emptyList(),
    val imageAttachments: List<Attachment> = attachments.filter { it.hasImage },
    val nonImageAttachmentFileDetails: List<TitledText>? = attachments.firstOrNull { !it.hasImage }?.fileDetails,
    val record: MessageRecord? = null,
    val mmsRecord: MmsMessageRecord? = record as? MmsMessageRecord,
    val sent: TitledText? = null,
    val received: TitledText? = null,
    val error: TitledText? = null,
    val senderInfo: TitledText? = null,
    val sender: Recipient? = null,
    val thread: Recipient? = null,
) {
    val fromTitle = GetString(R.string.message_details_header__from)
}

data class Attachment(
    val fileDetails: List<TitledText>,
    val fileName: String?,
    val uri: Uri?,
    val hasImage: Boolean
)
