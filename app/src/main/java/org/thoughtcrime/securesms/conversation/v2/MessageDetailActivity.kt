package org.thoughtcrime.securesms.conversation.v2

import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.model.MessageRecord
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

        val sent = TitledText("Sent:", "6:12 AM Tue, 09/08/2022 ")
        val received = TitledText("Received:", "6:12 AM Tue, 09/08/2022 ")
        val user = TitledText("Connor", "d4f1g54sdf5g1d5f4g65ds4564df65f4g65d54gdfsg")

        AppCompatTheme {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CardWithPadding {
                    FlowRow(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        maxItemsInEachRow = 2
                    ) {
                        fileDetails.forEach {
                            titledText(it, Modifier.weight(1f))
                        }
                    }
                }
                CardWithPadding {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        titledText(sent)
                        titledText(received)
                        titledView("From:") {
                            Row {
                                Box(modifier = Modifier.width(60.dp).height(60.dp))
                                Column {
                                    titledText(user)
                                }
                            }
                        }
                    }
                }
                Card {
                    Column {
                        ItemButton("Reply", R.drawable.ic_reply)
                        Divider()
                        ItemButton("Resend", R.drawable.ic_reply)
                        Divider()
                        ItemButton("Delete", R.drawable.ic_delete_24, color = Color.Red)
                    }
                }
            }
        }
    }

    @Composable
    fun Divider() {
        Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = Color(0xff414141))
    }

    @Composable
    fun ItemButton(text: String, @DrawableRes icon: Int, color: Color = Color.White) {
        TextButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            onClick = {},
            shape = RectangleShape,
        ) {
            Box(modifier = Modifier.width(80.dp).fillMaxHeight()) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = "",
                    tint = color,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Text(text, color = color, modifier = Modifier.fillMaxWidth())
        }
    }

    @Composable
    fun Card(content: @Composable () -> Unit) {
        CardWithPadding(0.dp) { content() }
    }

    @Composable
    fun CardWithPadding(padding: Dp = 24.dp, content: @Composable () -> Unit) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = 0.dp,
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            backgroundColor = Color(0xff1b1b1b),
            contentColor = Color.White
        ) { Box(Modifier.padding(padding)) { content() } }
    }

    @Composable
    fun titledText(titledText: TitledText, modifier: Modifier = Modifier) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Title(titledText.title)
            Text(titledText.value)
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