package org.thoughtcrime.securesms.dms

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.start.NewConversationDelegate
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.BorderlessButtonSecondary
import org.thoughtcrime.securesms.ui.OutlineButton
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.ThemeResPreviewParameterProvider
import org.thoughtcrime.securesms.ui.components.AppBar
import org.thoughtcrime.securesms.ui.components.MaybeScanQrCode
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.components.SessionTabRow

@AndroidEntryPoint
class NewMessageFragment : Fragment() {

    val viewModel: NewMessageViewModel by viewModels()

    lateinit var delegate: NewConversationDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            viewModel.event.collect {
                when (it) {
                    is Event.Success -> createPrivateChat(it.key)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            AppTheme {
                val uiState by viewModel.state.collectAsState(State())
                NewMessage(
                    uiState,
                    viewModel,
                    onClose = { delegate.onDialogClosePressed() },
                    onBack = { delegate.onDialogBackPressed() },
                    onHelp = { Toast.makeText(requireContext(), "todo, not implemented.", Toast.LENGTH_LONG).show() }
                )
            }
        }
    }

    private fun createPrivateChat(hexEncodedPublicKey: String) {
        val recipient = Recipient.from(requireContext(), Address.fromSerialized(hexEncodedPublicKey), false)
        Intent(requireContext(), ConversationActivityV2::class.java).apply {
            putExtra(ConversationActivityV2.ADDRESS, recipient.address)
            setDataAndType(requireActivity().intent.data, requireActivity().intent.type)
            putExtra(ConversationActivityV2.THREAD_ID, DatabaseComponent.get(requireContext()).threadDatabase().getThreadIdIfExistsFor(recipient))
        }.let(requireContext()::startActivity)
        delegate.onDialogClosePressed()
    }
}

@Preview
@Composable
private fun PreviewNewMessage(
    @PreviewParameter(ThemeResPreviewParameterProvider::class) themeResId: Int
) {
    PreviewTheme(themeResId) {
        NewMessage(State(), object: Callbacks {})
    }
}

private val TITLES = listOf(R.string.enter_account_id, R.string.qrScan)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewMessage(
    state: State,
    callbacks: Callbacks,
    onClose: () -> Unit = {},
    onBack: () -> Unit = {},
    onHelp: () -> Unit = {},
) {
    val pagerState = rememberPagerState { TITLES.size }

    Column(modifier = Modifier.background(MaterialTheme.colors.background)) {
        AppBar("New Message", onClose = { onClose() }, onBack = { onBack() })
        SessionTabRow(pagerState, TITLES)
        HorizontalPager(pagerState) {
            when (TITLES[it]) {
                R.string.enter_account_id -> EnterAccountId(state, callbacks, onHelp)
                R.string.qrScan -> MaybeScanQrCode()
            }
        }
    }
}

@Composable
fun EnterAccountId(
    state: State,
    callbacks: Callbacks,
    onHelp: () -> Unit = {}
) {
    Column(
        modifier = Modifier.padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SessionOutlinedTextField(
            text = state.newMessageIdOrOns,
            modifier = Modifier.padding(horizontal = 64.dp),
            placeholder = "Enter account ID or ONS",
            onChange = callbacks::onChange,
            onContinue = callbacks::onContinue,
            error = state.error?.string()
        )
        BorderlessButtonSecondary(text = "Start a new conversation by entering your friend's Account ID, ONS or scanning their QR code.") { onHelp() }
        Spacer(modifier = Modifier.weight(1f))

        OutlineButton(
            text = stringResource(id = R.string.continue_2),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 64.dp, vertical = 20.dp)
                .width(200.dp),
            loading = state.loading
        ) { callbacks.onContinue() }
    }
}
