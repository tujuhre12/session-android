package org.thoughtcrime.securesms.conversation.start

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.ItemButton
import org.thoughtcrime.securesms.ui.classicDarkColors
import org.thoughtcrime.securesms.ui.components.AppBar
import org.thoughtcrime.securesms.ui.components.QrImage
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.medium
import org.thoughtcrime.securesms.ui.small
import javax.inject.Inject

@AndroidEntryPoint
class NewConversationHomeFragment : Fragment() {

    @Inject
    lateinit var textSecurePreferences: TextSecurePreferences

    lateinit var delegate: NewConversationDelegate

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            AppTheme {
                NewConversationScreen()
            }
        }
    }

    @Composable
    fun NewConversationScreen() {
        Column(modifier = Modifier.background(MaterialTheme.colors.primarySurface)) {
            AppBar(stringResource(R.string.dialog_new_conversation_title), onClose = { delegate.onDialogClosePressed() })
            ItemButton(textId = R.string.messageNew, icon = R.drawable.ic_message) { delegate.onNewMessageSelected() }
            Divider(modifier = Modifier.padding(start = 80.dp))
            ItemButton(textId = R.string.activity_create_group_title, icon = R.drawable.ic_group) { delegate.onCreateGroupSelected() }
            Divider(modifier = Modifier.padding(start = 80.dp))
            ItemButton(textId = R.string.dialog_join_community_title, icon = R.drawable.ic_globe) { delegate.onJoinCommunitySelected() }
            Divider(modifier = Modifier.padding(start = 80.dp))
            ItemButton(textId = R.string.activity_settings_invite_button_title, icon = R.drawable.ic_invite_friend, contentDescription = R.string.AccessibilityId_invite_friend_button) { delegate.onInviteFriend() }
            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .padding(top = 20.dp)) {
                Text(text = stringResource(R.string.accountIdYours), style = MaterialTheme.typography.medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = stringResource(R.string.qrYoursDescription), color = classicDarkColors[5], style = MaterialTheme.typography.small)
                Spacer(modifier = Modifier.height(20.dp))
                QrImage(string = TextSecurePreferences.getLocalNumber(requireContext())!!, Modifier.contentDescription(R.string.AccessibilityId_qr_code))
            }
        }
    }
}
