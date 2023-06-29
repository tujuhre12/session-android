package org.thoughtcrime.securesms.conversation.v2

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.Cell
import org.thoughtcrime.securesms.ui.CellWithPadding
import org.thoughtcrime.securesms.ui.ItemButton
import org.thoughtcrime.securesms.ui.LocalExtraColors
import org.thoughtcrime.securesms.ui.destructiveButtonColors
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class MessageDetailActivity: PassphraseRequiredActionBarActivity() {
    var messageRecord: MessageRecord? = null

    @Inject
    lateinit var storage: Storage

    // region Settings
    companion object {
        // Extras
        const val MESSAGE_TIMESTAMP = "message_timestamp"
    }
    // endregion

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)

        val timestamp = intent.getLongExtra(MESSAGE_TIMESTAMP, -1L)

        title = resources.getString(R.string.conversation_context__menu_message_details)

        setContentView(createComposeView())
    }

    private fun createComposeView(): ComposeView = ComposeView(this).apply {
        id = View.generateViewId()
        setContent {
            MessageDetails()
        }
    }

    data class TitledText(val title: String, val value: String)

    @OptIn(ExperimentalLayoutApi::class)
    @Preview
    @Composable
    fun MessageDetails() {
        val fileDetails = listOf(
            TitledText("File Id:", "1237896548514214124235985214"),
            TitledText("File Type:", ".PNG"),
            TitledText("File Size:", "6mb"),
            TitledText("Resolution:", "550x550"),
            TitledText("Duration:", "N/A"),
        )

        val sent = TitledText("Sent:", "6:12 AM Tue, 09/08/2022")
        val received = TitledText("Received:", "6:12 AM Tue, 09/08/2022")
        val user = TitledText("Connor", "d4f1g54sdf5g1d5f4g65ds4564df65f4g65d54gdfsg")

        AppTheme {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CellWithPadding {
                    FlowRow(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        maxItemsInEachRow = 2
                    ) {
                        fileDetails.forEach {
                            titledText(it, Modifier.weight(1f))
                        }
                    }
                }
                CellWithPadding {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        titledText(sent)
                        titledText(received)
                        titledView("From:") {
                            Row {
                                Box(modifier = Modifier
                                    .width(60.dp)
                                    .height(60.dp))
                                Column {
                                    titledText(user, valueStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
                                }
                            }
                        }
                    }
                }
                Cell {
                    Column {
                        ItemButton("Reply", R.drawable.ic_reply)
                        Divider()
                        ItemButton("Resend", R.drawable.ic_reply)
                        Divider()
                        ItemButton("Delete", R.drawable.ic_delete_24 , colors = destructiveButtonColors())
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
