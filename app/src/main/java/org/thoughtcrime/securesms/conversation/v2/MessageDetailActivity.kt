package org.thoughtcrime.securesms.conversation.v2

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.MediaPreviewActivity
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.components.ProfilePictureView
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.CarouselNextButton
import org.thoughtcrime.securesms.ui.CarouselPrevButton
import org.thoughtcrime.securesms.ui.Cell
import org.thoughtcrime.securesms.ui.CellNoMargin
import org.thoughtcrime.securesms.ui.CellWithPaddingAndMargin
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.HorizontalPagerIndicator
import org.thoughtcrime.securesms.ui.ItemButton
import org.thoughtcrime.securesms.ui.blackAlpha40
import org.thoughtcrime.securesms.ui.colorDestructive
import org.thoughtcrime.securesms.ui.destructiveButtonColors
import javax.inject.Inject


@AndroidEntryPoint
class MessageDetailActivity : PassphraseRequiredActionBarActivity() {

    private var timestamp: Long = 0L

    @Inject
    lateinit var storage: Storage

    companion object {
        // Extras
        const val MESSAGE_TIMESTAMP = "message_timestamp"

        const val ON_REPLY = 1
        const val ON_RESEND = 2
        const val ON_DELETE = 3
    }

    val viewModel = MessageDetailsViewModel()

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
        MessageDetails(
            details,
            onReply = { setResultAndFinish(ON_REPLY) },
            onResend = { setResultAndFinish(ON_RESEND) },
            onDelete = { setResultAndFinish(ON_DELETE) },
            onClickImage = { slide ->
                MediaPreviewActivity.getPreviewIntent(this, slide, details.mmsRecord, details.sender)
                    .let(::startActivity)
            }
        )
    }

    private fun setResultAndFinish(code: Int) {
        Bundle().apply { putLong(MESSAGE_TIMESTAMP, timestamp) }
            .let(Intent()::putExtras)
            .let { setResult(code, it) }

        finish()
    }

    @Preview
    @Composable
    fun PreviewMessageDetails() {
        MessageDetails(
            MessageDetails(
                attachments = listOf(),
                sent = TitledText("Sent:", "6:12 AM Tue, 09/08/2022"),
                received = TitledText("Received:", "6:12 AM Tue, 09/08/2022"),
                error = TitledText("Error:", "Message failed to send"),
                senderInfo = TitledText("Connor", "d4f1g54sdf5g1d5f4g65ds4564df65f4g65d54gdfsg"),
            )
        )
    }

    @Composable
    fun MessageDetails(
        messageDetails: MessageDetails,
        onReply: () -> Unit = {},
        onResend: () -> Unit = {},
        onDelete: () -> Unit = {},
        onClickImage: (Slide) -> Unit = {},
    ) {
        messageDetails.apply {
            AppTheme {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Attachments(attachments) { onClickImage(it) }
                    MetaDataCell(messageDetails)
                    Buttons(
                        error != null,
                        onReply,
                        onResend,
                        onDelete,
                    )
                }
            }
        }
    }

    @Composable
    fun MetaDataCell(
        messageDetails: MessageDetails,
    ) {
        messageDetails.apply {
            if (sent != null || received != null || senderInfo != null) CellWithPaddingAndMargin {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    sent?.let { TitledText(it) }
                    received?.let { TitledText(it) }
                    error?.let { TitledErrorText(it) }
                    senderInfo?.let {
                        TitledView("From:") {
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
    fun RowScope.Avatar(sender: Recipient) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .align(Alignment.CenterVertically)
        ) {
            AndroidView(
                factory = {
                    ProfilePictureView(it).apply { update(sender) }
                },
                modifier = Modifier.width(46.dp).height(46.dp)
            )
        }
    }

    @Composable
    fun Buttons(
        hasError: Boolean,
        onReply: () -> Unit = {},
        onResend: () -> Unit = {},
        onDelete: () -> Unit = {},
    ) {
        Cell {
            Column {
                ItemButton(
                    "Reply",
                    R.drawable.ic_message_details__reply,
                    onClick = onReply
                )
                Divider()
                if (hasError) {
                    ItemButton(
                        "Resend",
                        R.drawable.ic_message_details__refresh,
                        onClick = onResend
                    )
                    Divider()
                }
                ItemButton(
                    "Delete",
                    R.drawable.ic_message_details__trash,
                    colors = destructiveButtonColors(),
                    onClick = onDelete
                )
            }
        }
    }

    @Composable
    fun Attachments(attachments: List<Attachment>, onClick: (Slide) -> Unit) {
        val slide = attachments.firstOrNull()?.slide ?: return
        when {
            slide.hasImage() -> Carousel(attachments, onClick)
        }
    }

    @OptIn(ExperimentalFoundationApi::class,)
    @Composable
    fun Carousel(attachments: List<Attachment>, onClick: (Slide) -> Unit) {
        val imageAttachments = attachments.filter { it.slide.hasImage() }
        val pagerState = rememberPagerState { imageAttachments.size }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row {
                CarouselPrevButton(pagerState)
                Box(modifier = Modifier.weight(1f)) {
                    CellPager(pagerState, imageAttachments, onClick)
                    HorizontalPagerIndicator(pagerState)
                    ExpandButton(modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
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
    private fun CellPager(pagerState: PagerState, imageAttachments: List<Attachment>, onClick: (Slide) -> Unit) {
        CellNoMargin {
            HorizontalPager(state = pagerState) { i ->
                val slide = imageAttachments[i].slide
                GlideImage(
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onClick(slide) },
                    model = slide.uri,
                    contentDescription = slide.fileName.orNull() ?: "image"
                )
            }
        }
    }

    @Composable
    fun ExpandButton(modifier: Modifier) {
        Surface(
            shape = CircleShape,
            color = blackAlpha40,
            modifier = modifier
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_expand),
                contentDescription = ""
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
    @Composable
    fun FileDetails(attachments: List<Attachment>, pagerState: PagerState) {
        attachments[pagerState.currentPage].fileDetails.takeIf { it.isNotEmpty() }?.let {
            CellWithPaddingAndMargin {
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    maxItemsInEachRow = 2
                ) {
                    it.forEach { TitledText(it, Modifier.weight(1f)) }
                }
            }
        }
    }

    @Composable
    fun TitledErrorText(titledText: TitledText, modifier: Modifier = Modifier) {
        TitledText(
            titledText,
            modifier = modifier,
            valueStyle = LocalTextStyle.current.copy(color = colorDestructive))
    }

    @Composable
    fun TitledMonospaceText(titledText: TitledText, modifier: Modifier = Modifier) {
        TitledText(
            titledText,
            modifier = modifier,
            valueStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
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
    fun Title(text: String) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}
