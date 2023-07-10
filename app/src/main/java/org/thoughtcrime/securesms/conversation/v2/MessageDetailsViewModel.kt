package org.thoughtcrime.securesms.conversation.v2

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.session.libsession.messaging.jobs.AttachmentDownloadJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.utilities.Util
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.AttachmentDatabase
import org.thoughtcrime.securesms.database.LokiMessageDatabase
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.ImageSlide
import org.thoughtcrime.securesms.mms.Slide
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class TitledText(val title: String, val value: String)

data class MessageDetailsState(
    val attachments: List<Attachment> = emptyList(),
    val record: MessageRecord? = null,
    val mmsRecord: MmsMessageRecord? = null,
    val sent: TitledText? = null,
    val received: TitledText? = null,
    val error: TitledText? = null,
    val senderInfo: TitledText? = null,
    val sender: Recipient? = null,
    val thread: Recipient? = null,
) {
    val imageAttachments = attachments.filter { it.hasImage() }
    val nonImageAttachment: Attachment? = attachments.firstOrNull { !it.hasImage() }
}

data class Attachment(
    val slide: Slide,
    val fileDetails: List<TitledText>
) {
    val fileName: String? get() = slide.fileName.orNull()
    val uri get() = slide.uri

    fun hasImage() = slide is ImageSlide
}

@HiltViewModel
class MessageDetailsViewModel @Inject constructor(
    private val attachmentDb: AttachmentDatabase,
    private val lokiMessageDatabase: LokiMessageDatabase,
    private val mmsSmsDatabase: MmsSmsDatabase,
    private val threadDb: ThreadDatabase,
) : ViewModel() {

    fun setMessageTimestamp(timestamp: Long) {
        mmsSmsDatabase.getMessageForTimestamp(timestamp).let(::setMessageRecord)
    }

    fun setMessageRecord(record: MessageRecord?) {
        val mmsRecord = record as? MmsMessageRecord

        val slides: List<Slide> = mmsRecord?.slideDeck?.thumbnailSlides?.toList() ?: emptyList()

        _details.value = record?.run {
            MessageDetailsState(
                attachments = slides.map { Attachment(it, it.details) },
                record = record,
                mmsRecord = mmsRecord,
                sent = dateSent.let(::Date).toString().let { TitledText("Sent:", it) },
                received = dateReceived.let(::Date).toString().let { TitledText("Received:", it) },
                error = lokiMessageDatabase.getErrorMessage(id)?.let { TitledText("Error:", it) },
                senderInfo = individualRecipient.run { name?.let { TitledText(it, address.serialize()) } },
                sender = individualRecipient,
                thread = threadDb.getRecipientForThreadId(threadId)!!,
            )
        }
    }

    private var _details = MutableLiveData(MessageDetailsState())
    val details: LiveData<MessageDetailsState> = _details

    private val Slide.details: List<TitledText>
        get() = listOfNotNull(
            fileName.orNull()?.let { TitledText("File Id:", it) },
            TitledText("File Type:", asAttachment().contentType),
            TitledText("File Size:", Util.getPrettyFileSize(fileSize)),
            takeIf { it.hasImage() }
                .run { asAttachment().run { "${width}x$height" } }
                .let { TitledText("Resolution:", it) },
            attachmentDb.duration(this)?.let { TitledText("Duration:", it) },
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

}
