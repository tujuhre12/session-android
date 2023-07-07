package org.thoughtcrime.securesms.conversation.v2

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.utilities.Util
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.AttachmentDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.Slide
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class TitledText(val title: String, val value: String)

data class MessageDetails(
    val attachments: List<Attachment> = emptyList(),
    val record: MessageRecord? = null,
    val mmsRecord: MmsMessageRecord? = null,
    val sent: TitledText? = null,
    val received: TitledText? = null,
    val error: TitledText? = null,
    val senderInfo: TitledText? = null,
    val sender: Recipient? = null
)

data class Attachment(
    val slide: Slide,
    val fileDetails: List<TitledText>
)

@HiltViewModel
class MessageDetailsViewModel @Inject constructor(
    private val attachmentDb: AttachmentDatabase
): ViewModel() {

    fun setMessageRecord(record: MessageRecord?, error: String?) {
        val mmsRecord = record as? MmsMessageRecord

        val slides: List<Slide> = mmsRecord?.slideDeck?.thumbnailSlides?.toList() ?: emptyList()

        _details.value = record?.run {
            MessageDetails(
                record = record,
                mmsRecord = mmsRecord,
                attachments = slides.map { Attachment(it, it.details) },
                sent = dateSent.let(::Date).toString().let { TitledText("Sent:", it) },
                received = dateReceived.let(::Date).toString().let { TitledText("Received:", it) },
                error = error?.let { TitledText("Error:", it) },
                senderInfo = individualRecipient.run {
                    name?.let { TitledText(it, address.serialize()) }
                },
                sender = individualRecipient
            )
        }
    }

    private var _details = MutableLiveData(MessageDetails())
    val details: LiveData<MessageDetails> = _details

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
            ?.run {
                getAttachmentAudioExtras(attachmentId)
                    ?.let { audioExtras ->
                        audioExtras.durationMs.takeIf { it > 0 }?.let {
                            String.format(
                                "%01d:%02d",
                                TimeUnit.MILLISECONDS.toMinutes(it),
                                TimeUnit.MILLISECONDS.toSeconds(it) % 60
                            )
                        }
                    }
            }
}