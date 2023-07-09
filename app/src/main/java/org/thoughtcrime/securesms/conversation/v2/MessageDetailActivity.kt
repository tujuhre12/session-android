package org.thoughtcrime.securesms.conversation.v2

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent.ACTION_UP
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewVisibleMessageContentBinding
import org.session.libsession.messaging.jobs.AttachmentDownloadJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentTransferProgress
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.MediaPreviewActivity
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.Avatar
import org.thoughtcrime.securesms.ui.CarouselNextButton
import org.thoughtcrime.securesms.ui.CarouselPrevButton
import org.thoughtcrime.securesms.ui.Cell
import org.thoughtcrime.securesms.ui.CellNoMargin
import org.thoughtcrime.securesms.ui.CellWithPaddingAndMargin
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.HorizontalPagerIndicator
import org.thoughtcrime.securesms.ui.ItemButton
import org.thoughtcrime.securesms.ui.Theme
import org.thoughtcrime.securesms.ui.blackAlpha40
import org.thoughtcrime.securesms.ui.colorDestructive
import org.thoughtcrime.securesms.ui.destructiveButtonColors
import javax.inject.Inject

@AndroidEntryPoint
class MessageDetailActivity : PassphraseRequiredActionBarActivity() {

    private var timestamp: Long = 0L

    @Inject
    lateinit var storage: Storage

    private val viewModel: MessageDetailsViewModel by viewModels()

    companion object {
        // Extras
        const val MESSAGE_TIMESTAMP = "message_timestamp"

        const val ON_REPLY = 1
        const val ON_RESEND = 2
        const val ON_DELETE = 3
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)

        timestamp = intent.getLongExtra(MESSAGE_TIMESTAMP, -1L)

        val messageRecord =
            DatabaseComponent.get(this).mmsSmsDatabase().getMessageForTimestamp(timestamp) ?: run {
                finish()
                return
            }

        val error = DatabaseComponent.get(this).lokiMessageDatabase()
            .getErrorMessage(messageRecord.getId())

        viewModel.setMessageRecord(messageRecord, error)

        title = resources.getString(R.string.conversation_context__menu_message_details)

        ComposeView(this)
            .apply { setContent { MessageDetailsScreen() } }
            .let(::setContentView)
    }

    @Composable
    private fun MessageDetailsScreen() {
        val details by viewModel.details.observeAsState(MessageDetails())
        val threadDb = DatabaseComponent.get(this@MessageDetailActivity).threadDatabase()
        AppTheme {
            MessageDetails(
                threadDb = threadDb,
                messageDetails = details,
                onReply = { setResultAndFinish(ON_REPLY) },
                onResend = details.error?.let { { setResultAndFinish(ON_RESEND) } },
                onDelete = { setResultAndFinish(ON_DELETE) },
                onClickImage = { slide ->
                    // only open to downloaded images
                    if (slide.transferState == AttachmentTransferProgress.TRANSFER_PROGRESS_FAILED) {
                        // Restart download here (on IO thread)
                        (slide.asAttachment() as? DatabaseAttachment)?.let { attachment ->
                            onAttachmentNeedsDownload(attachment.attachmentId.rowId, details.mmsRecord!!.getId())
                        }
                    }
                    if (!slide.isInProgress) MediaPreviewActivity.getPreviewIntent(
                        this,
                        slide,
                        details.mmsRecord,
                        threadDb.getRecipientForThreadId(details.mmsRecord!!.threadId),
                    ).let(::startActivity)
                },
                onAttachmentNeedsDownload = ::onAttachmentNeedsDownload,
            )
        }
    }

    private fun setResultAndFinish(code: Int) {
        Bundle().apply { putLong(MESSAGE_TIMESTAMP, timestamp) }
            .let(Intent()::putExtras)
            .let { setResult(code, it) }

        finish()
    }

    private fun onAttachmentNeedsDownload(attachmentId: Long, mmsId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            JobQueue.shared.add(AttachmentDownloadJob(attachmentId, mmsId))
        }
    }

}

@Composable
fun PreviewMessageDetails() {
    AppTheme {
        MessageDetails(
            messageDetails = MessageDetails(
                attachments = listOf(),
                sent = TitledText("Sent:", "6:12 AM Tue, 09/08/2022"),
                received = TitledText("Received:", "6:12 AM Tue, 09/08/2022"),
                error = TitledText("Error:", "Message failed to send"),
                senderInfo = TitledText("Connor", "d4f1g54sdf5g1d5f4g65ds4564df65f4g65d54"),
            )
        )
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun MessageDetails(
    threadDb: ThreadDatabase? = null,
    messageDetails: MessageDetails,
    onReply: () -> Unit = {},
    onResend: (() -> Unit)? = null,
    onDelete: () -> Unit = {},
    onClickImage: (Slide) -> Unit = {},
    onAttachmentNeedsDownload: (Long, Long) -> Unit = { _, _ -> }
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        messageDetails.record?.let { message ->
            AndroidView(
                modifier = Modifier.padding(horizontal = 32.dp),
                factory = {
                    ViewVisibleMessageContentBinding.inflate(LayoutInflater.from(it)).mainContainerConstraint.apply {
                        bind(
                            message,
                            thread = threadDb?.getRecipientForThreadId(message.threadId)!!,
                            onAttachmentNeedsDownload = onAttachmentNeedsDownload,
                            suppressThumbnails = true
                        )

                        setOnTouchListener { _, event ->
                            if (event.actionMasked == ACTION_UP) onContentClick(event)
                            true
                        }
                    }
                }
            )
        }
        Carousel(messageDetails.attachments) { onClickImage(it) }
        MetadataCell(messageDetails)
        Buttons(
            onReply,
            onResend,
            onDelete,
        )
    }
}

@Composable
fun MetadataCell(
    messageDetails: MessageDetails,
) {
    messageDetails.apply {
        if (sent != null || received != null || senderInfo != null) CellWithPaddingAndMargin {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                sent?.let { TitledText(it) }
                received?.let { TitledText(it) }
                error?.let { TitledErrorText(it) }
                senderInfo?.let {
                    TitledView(stringResource(id = R.string.message_details_header__from)) {
                        Row {
                            sender?.let { Avatar(it) }
                            TitledMonospaceText(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Buttons(
    onReply: () -> Unit = {},
    onResend: (() -> Unit)? = null,
    onDelete: () -> Unit = {},
) {
    Cell {
        Column {
            ItemButton(
                stringResource(id = R.string.reply),
                R.drawable.ic_message_details__reply,
                onClick = onReply
            )
            Divider()
            onResend?.let {
                ItemButton(
                    stringResource(id = R.string.resend),
                    R.drawable.ic_message_details__refresh,
                    onClick = it
                )
                Divider()
            }
            ItemButton(
                stringResource(id = R.string.delete),
                R.drawable.ic_message_details__trash,
                colors = destructiveButtonColors(),
                onClick = onDelete
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Carousel(attachments: List<Attachment>, onClick: (Slide) -> Unit) {
    val imageAttachments = attachments.filter { it.hasImage() }.takeIf { it.isNotEmpty() } ?: return
    val pagerState = rememberPagerState { imageAttachments.size }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row {
            CarouselPrevButton(pagerState)
            Box(modifier = Modifier.weight(1f)) {
                CellCarousel(pagerState, imageAttachments, onClick)
                HorizontalPagerIndicator(pagerState)
                ExpandButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) { onClick(imageAttachments[pagerState.currentPage].slide) }
            }
            CarouselNextButton(pagerState)
        }
        FileDetails(attachments, pagerState)
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalGlideComposeApi::class
)
@Composable
private fun CellCarousel(
    pagerState: PagerState,
    imageAttachments: List<Attachment>,
    onClick: (Slide) -> Unit
) {
    CellNoMargin {
        HorizontalPager(state = pagerState) { i ->
            val slide = imageAttachments[i].slide
            GlideImage(
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clickable { onClick(slide) },
                model = slide.uri,
                contentDescription = slide.fileName.orNull() ?: stringResource(id = R.string.image)
            )
        }
    }
}

@Composable
fun ExpandButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = blackAlpha40,
        modifier = modifier,
        contentColor = Color.White,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_expand),
            contentDescription = "",
            modifier = Modifier.clickable { onClick() },
        )
    }
}

@Preview
@Composable
fun PreviewFileDetails() {
    Theme(R.style.Ocean_Dark) {
        FileDetails(
            fileDetails = listOf(
                TitledText("File Id:", "Screen Shot 2023-07-06 at 11.35.50 am.png"),
                TitledText("File Type:", "image/png"),
                TitledText("File Size:", "195.6kB"),
                TitledText("Resolution:", "342x312"),
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileDetails(attachments: List<Attachment>, pagerState: PagerState) {
    FileDetails(attachments[pagerState.currentPage].fileDetails)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileDetails(fileDetails: List<TitledText>) {
    if (fileDetails.isEmpty()) return

    CellWithPaddingAndMargin {
        FlowRow(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            fileDetails.forEach {
                TitledText(
                    it,
                    modifier = Modifier
                        .widthIn(min = 100.dp)    // set minimum width
                        .width(IntrinsicSize.Max) // make the text as wide as necessary
                        .weight(1f)               // space evenly
                )
            }
        }
    }
}

@Composable
fun TitledErrorText(titledText: TitledText, modifier: Modifier = Modifier) {
    TitledText(
        titledText,
        modifier = modifier,
        valueStyle = LocalTextStyle.current.copy(color = colorDestructive)
    )
}

@Composable
fun TitledMonospaceText(titledText: TitledText, modifier: Modifier = Modifier) {
    TitledText(
        titledText,
        modifier = modifier,
        valueStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
    )
}

@Composable
fun TitledText(
    titledText: TitledText,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = LocalTextStyle.current
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Title(titledText.title)
        Text(titledText.value, style = valueStyle)
    }
}

@Composable
fun TitledView(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Title(title)
        content()
    }
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontWeight = FontWeight.Bold)
}
