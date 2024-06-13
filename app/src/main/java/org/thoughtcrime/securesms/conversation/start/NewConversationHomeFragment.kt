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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.ItemButton
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.classicDarkColors
import org.thoughtcrime.securesms.ui.components.AppBar
import org.thoughtcrime.securesms.ui.components.QrImage
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.onCreateView
import org.thoughtcrime.securesms.ui.small
import org.thoughtcrime.securesms.ui.xl
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
    ): View = onCreateView { NewConversationScreen() }

    @Composable
    fun NewConversationScreen() {
        Column(modifier = Modifier.background(MaterialTheme.colors.primarySurface)) {
            AppBar(stringResource(R.string.dialog_new_conversation_title), onClose = { delegate.onDialogClosePressed() })
            ItemButton(textId = R.string.messageNew, icon = R.drawable.ic_message) { delegate.onNewMessageSelected() }
            Divider(startIndent = LocalDimensions.current.dividerIndent)
            ItemButton(textId = R.string.activity_create_group_title, icon = R.drawable.ic_group) { delegate.onCreateGroupSelected() }
            Divider(startIndent = LocalDimensions.current.dividerIndent)
            ItemButton(textId = R.string.dialog_join_community_title, icon = R.drawable.ic_globe) { delegate.onJoinCommunitySelected() }
            Divider(startIndent = LocalDimensions.current.dividerIndent)
            ItemButton(textId = R.string.activity_settings_invite_button_title, icon = R.drawable.ic_invite_friend, contentDescription = R.string.AccessibilityId_invite_friend_button) { delegate.onInviteFriend() }
            Column(
                modifier = Modifier
                    .padding(horizontal = LocalDimensions.current.marginMedium)
                    .padding(top = LocalDimensions.current.itemSpacingMedium)
            ) {
                Text(
                    text = stringResource(R.string.accountIdYours),
                    style = MaterialTheme.typography.xl
                )
                Spacer(modifier = Modifier.height(LocalDimensions.current.itemSpacingTiny))
                Text(
                    text = stringResource(R.string.qrYoursDescription),
                    color = classicDarkColors[5],
                    style = MaterialTheme.typography.small
                )
                Spacer(modifier = Modifier.height(LocalDimensions.current.itemSpacingSmall))
                QrImage(string = TextSecurePreferences.getLocalNumber(requireContext())!!, Modifier.contentDescription(R.string.AccessibilityId_qr_code))
            }
        }
    }
}
