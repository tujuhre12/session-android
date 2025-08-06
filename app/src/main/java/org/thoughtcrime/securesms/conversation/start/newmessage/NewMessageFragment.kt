package org.thoughtcrime.securesms.conversation.start.newmessage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.session.libsession.utilities.Address
import org.thoughtcrime.securesms.conversation.start.StartConversationDelegate
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.openUrl
import org.thoughtcrime.securesms.ui.createThemedComposeView

@AndroidEntryPoint
class NewMessageFragment : Fragment() {
    private val viewModel: NewMessageViewModel by viewModels()

    lateinit var delegate: StartConversationDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            viewModel.success.collect {
                createPrivateChat(it.address)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = createThemedComposeView {
        val uiState by viewModel.state.collectAsState(State())
        NewMessage(
            uiState,
            viewModel.qrErrors,
            viewModel,
            onClose = { delegate.onDialogClosePressed() },
            onBack = { delegate.onDialogBackPressed() },
            onHelp = { requireContext().openUrl("https://sessionapp.zendesk.com/hc/en-us/articles/4439132747033-How-do-Account-ID-usernames-work") }
        )
    }

    private fun createPrivateChat(address: Address.Standard) {
        ConversationActivityV2.createIntent(requireContext(), address).apply {
            setDataAndType(requireActivity().intent.data, requireActivity().intent.type)
        }.let(requireContext()::startActivity)
        delegate.onDialogClosePressed()
    }
}
