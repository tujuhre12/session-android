package org.thoughtcrime.securesms.conversation.v2

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.components.ProfilePictureView
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.Cell
import org.thoughtcrime.securesms.ui.CellWithPadding
import org.thoughtcrime.securesms.ui.ItemButton
import org.thoughtcrime.securesms.ui.LocalExtraColors
import org.thoughtcrime.securesms.ui.colorDestructive
import org.thoughtcrime.securesms.ui.destructiveButtonColors
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class MessageDetailActivity: PassphraseRequiredActionBarActivity() {

    private var timestamp: Long = 0L

    var messageRecord: MessageRecord? = null

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

    class MessageDetailsViewModel: ViewModel() {

        fun setMessageRecord(value: MessageRecord?, error: String?) {
            val mmsRecord = value as? MmsMessageRecord

            val slides = mmsRecord?.slideDeck?.thumbnailSlides

            _details.value = value?.run {
                MessageDetails(
                    sent = dateSent.let(::Date).toString().let { TitledText("Sent:", it) },
                    received = dateReceived.let(::Date).toString().let { TitledText("Received:", it) },
                    error = error?.let { TitledText("Error:", it) },
                    senderInfo = individualRecipient.run { name?.let { TitledText(it, address.serialize()) } },
                    sender = individualRecipient
                )
            } ?: MessageDetails()
        }

        private var _details = MutableLiveData(MessageDetails())
        val details: LiveData<MessageDetails> = _details
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)

        timestamp = intent.getLongExtra(MESSAGE_TIMESTAMP, -1L)

        messageRecord = DatabaseComponent.get(this).mmsSmsDatabase().getMessageForTimestamp(timestamp) ?: run {
            finish()
            return
        }

        val error = DatabaseComponent.get(this).lokiMessageDatabase().getErrorMessage(messageRecord!!.getId())

        viewModel.setMessageRecord(messageRecord, error)

        title = resources.getString(R.string.conversation_context__menu_message_details)

        setContentView(ComposeView(this).apply {
            setContent {
                MessageDetailsScreen()
            }
        })
    }

    @Composable
    private fun MessageDetailsScreen() {
        val details by viewModel.details.observeAsState(MessageDetails())
        MessageDetails(
            details,
            onReply = { setResultAndFinish(ON_REPLY) },
            onResend = { setResultAndFinish(ON_RESEND) },
            onDelete = { setResultAndFinish(ON_DELETE) }
        )
    }

    private fun setResultAndFinish(code: Int) {
        Bundle().apply { putLong(MESSAGE_TIMESTAMP, timestamp) }
            .let(Intent()::putExtras)
            .let { setResult(code, it) }

        finish()
    }

    data class TitledText(val title: String, val value: String)

    data class MessageDetails(
        val fileDetails: List<TitledText>? = null,
        val sent: TitledText? = null,
        val received: TitledText? = null,
        val error: TitledText? = null,
        val senderInfo: TitledText? = null,
        val sender: Recipient? = null
    )

    @Preview
    @Composable
    fun PreviewMessageDetails() {
        MessageDetails(
            MessageDetails(
                fileDetails = listOf(
                    TitledText("File Id:", "1237896548514214124235985214"),
                    TitledText("File Type:", ".PNG"),
                    TitledText("File Size:", "6mb"),
                    TitledText("Resolution:", "550x550"),
                    TitledText("Duration:", "N/A"),
                ),
                sent = TitledText("Sent:", "6:12 AM Tue, 09/08/2022"),
                received = TitledText("Received:", "6:12 AM Tue, 09/08/2022"),
                error = TitledText("Error:", "Message failed to send"),
                senderInfo = TitledText("Connor", "d4f1g54sdf5g1d5f4g65ds4564df65f4g65d54gdfsg")
            )
        )
    }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
    @Composable
    fun MessageDetails(
        messageDetails: MessageDetails,
        onReply: () -> Unit = {},
        onResend: () -> Unit = {},
        onDelete: () -> Unit = {},
    ) {
        messageDetails.apply {
            AppTheme {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HorizontalPager(pageCount = 1) {

                    }

                    fileDetails?.takeIf { it.isNotEmpty() }?.let {
                        CellWithPadding {
                            FlowRow(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                maxItemsInEachRow = 2
                            ) {
                                it.forEach {
                                    titledText(it, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    if (sent != null || received != null || senderInfo != null) CellWithPadding {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            sent?.let { titledText(it) }
                            received?.let { titledText(it) }
                            error?.let { titledText(it, valueStyle = LocalTextStyle.current.copy(color = colorDestructive)) }
                            senderInfo?.let {
                                titledView("From:") {
                                    Row {
                                        sender?.let {
                                            Box(modifier = Modifier
                                                .width(60.dp)
                                                .align(Alignment.CenterVertically)) {
                                                AndroidView(
                                                    factory = { ProfilePictureView(it).apply { update(sender) } },
                                                    modifier = Modifier
                                                        .width(46.dp)
                                                        .height(46.dp)
                                                )
                                            }
                                        }
                                        Column {
                                            titledText(it, valueStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
                                        }
                                    }
                               }
                            }
                        }
                    }
                    Cell {
                        Column {
                            ItemButton("Reply", R.drawable.ic_message_details__reply, onClick = onReply)
                            Divider()
                            if (error != null) {
                                ItemButton("Resend", R.drawable.ic_message_details__refresh, onClick = onResend)
                                Divider()
                            }
                            ItemButton("Delete", R.drawable.ic_message_details__trash, colors = destructiveButtonColors(), onClick = onDelete)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Divider() {
        Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = LocalExtraColors.current.divider)
    }

    @Composable
    fun titledText(titledText: TitledText, modifier: Modifier = Modifier, valueStyle: TextStyle = LocalTextStyle.current) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Title(titledText.title)
            Text(titledText.value, style = valueStyle)
        }
    }

    @Composable
    fun titledView(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
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
