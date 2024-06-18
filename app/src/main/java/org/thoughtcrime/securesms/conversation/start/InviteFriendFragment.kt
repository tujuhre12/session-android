package org.thoughtcrime.securesms.conversation.start

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.preferences.copyPublicKey
import org.thoughtcrime.securesms.preferences.sendInvitationToUseSession
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.components.AppBar
import org.thoughtcrime.securesms.ui.components.SlimOutlineButton
import org.thoughtcrime.securesms.ui.components.SlimOutlineCopyButton
import org.thoughtcrime.securesms.ui.components.border
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.createThemedComposeView
import org.thoughtcrime.securesms.ui.small

@AndroidEntryPoint
class InviteFriendFragment : Fragment() {
    lateinit var delegate: NewConversationDelegate

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = createThemedComposeView {
        InviteFriend(
            TextSecurePreferences.getLocalNumber(LocalContext.current)!!,
            onBack = { delegate.onDialogBackPressed() },
            onClose = { delegate.onDialogClosePressed() },
            copyPublicKey = requireContext()::copyPublicKey,
            sendInvitation = requireContext()::sendInvitationToUseSession,
        )
    }
}

@Preview
@Composable
private fun PreviewInviteFriend() {
    PreviewTheme {
        InviteFriend("050000000")
    }
}

@Composable
private fun InviteFriend(
    accountId: String,
    onBack: () -> Unit = {},
    onClose: () -> Unit = {},
    copyPublicKey: () -> Unit = {},
    sendInvitation: () -> Unit = {},
) {
    Column(modifier = Modifier.background(LocalColors.current.backgroundSecondary)) {
        AppBar(stringResource(R.string.invite_a_friend), onBack = onBack, onClose = onClose)
        Column(
            modifier = Modifier.padding(horizontal = LocalDimensions.current.itemSpacingMedium),
        ) {
            Text(
                accountId,
                modifier = Modifier
                    .contentDescription(R.string.AccessibilityId_recovery_password_container)
                    .fillMaxWidth()
                    .border()
                    .padding(LocalDimensions.current.marginSmall),
                textAlign = TextAlign.Center,
                style = base
            )

            Spacer(modifier = Modifier.height(LocalDimensions.current.itemSpacingXSmall))

            Text(
                stringResource(R.string.invite_your_friend_to_chat_with_you_on_session_by_sharing_your_account_id_with_them),
                textAlign = TextAlign.Center,
                style = small,
                color = LocalColors.current.textSecondary,
                modifier = Modifier.padding(horizontal = LocalDimensions.current.itemSpacingSmall)
            )

            Spacer(modifier = Modifier.height(LocalDimensions.current.itemSpacingSmall))

            Row(horizontalArrangement = spacedBy(LocalDimensions.current.itemSpacingSmall)) {
                SlimOutlineButton(
                    stringResource(R.string.share),
                    modifier = Modifier
                        .weight(1f)
                        .contentDescription("Share button"),
                    onClick = sendInvitation
                )

                SlimOutlineCopyButton(
                    modifier = Modifier.weight(1f),
                    onClick = copyPublicKey
                )
            }
        }
    }
}
