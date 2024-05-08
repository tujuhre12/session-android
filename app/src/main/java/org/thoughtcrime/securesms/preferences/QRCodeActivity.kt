package org.thoughtcrime.securesms.preferences

import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.PublicKeyValidation
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.database.threadDatabase
import org.thoughtcrime.securesms.ui.components.MaybeScanQrCode
import org.thoughtcrime.securesms.ui.components.QrImage
import org.thoughtcrime.securesms.ui.components.SessionTabRow
import org.thoughtcrime.securesms.ui.setComposeContent
import org.thoughtcrime.securesms.ui.small
import org.thoughtcrime.securesms.util.start

private val TITLES = listOf(R.string.view, R.string.scan)

class QRCodeActivity : PassphraseRequiredActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        supportActionBar!!.title = resources.getString(R.string.activity_qr_code_title)

        setComposeContent {
            Tabs(TextSecurePreferences.getLocalNumber(this)!!, onScan = ::handleQRCodeScanned)
        }
    }

    fun handleQRCodeScanned(string: String) {
        if (!PublicKeyValidation.isValid(string)) {
            return Toast.makeText(this, R.string.invalid_session_id, Toast.LENGTH_SHORT).show()
        }
        val recipient = Recipient.from(this, Address.fromSerialized(string), false)
        start<ConversationActivityV2> {
            putExtra(ConversationActivityV2.ADDRESS, recipient.address)
            setDataAndType(intent.data, intent.type)
            val existingThread = threadDatabase().getThreadIdIfExistsFor(recipient)
            putExtra(ConversationActivityV2.THREAD_ID, existingThread)
        }
        finish()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tabs(sessionId: String, onScan: (String) -> Unit) {
    val pagerState = rememberPagerState { TITLES.size }

    Column {
        SessionTabRow(pagerState, TITLES)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (TITLES[page]) {
                R.string.view -> QrPage(sessionId)
                R.string.scan -> MaybeScanQrCode(onScan = onScan)
            }
        }
    }
}

@Composable
fun QrPage(string: String) {
    Column(modifier = Modifier
        .padding(horizontal = 32.dp)
        .fillMaxSize()) {
        QrImage(
            string = string,
            contentDescription = "Your session id",
            modifier = Modifier.padding(top = 32.dp, bottom = 12.dp),
            icon = R.drawable.session
        )

        Text(
            text = stringResource(R.string.this_is_your_account_id_other_users_can_scan_it_to_start_a_conversation_with_you),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.small
        )
    }
}
