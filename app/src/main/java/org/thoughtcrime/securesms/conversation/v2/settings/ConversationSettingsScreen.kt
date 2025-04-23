package org.thoughtcrime.securesms.conversation.v2.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.GroupMember
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.groups.GroupMemberState
import org.thoughtcrime.securesms.groups.GroupMembersViewModel
import org.thoughtcrime.securesms.ui.components.BackAppBar
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.primaryBlue
import org.thoughtcrime.securesms.util.AvatarUIData
import org.thoughtcrime.securesms.util.AvatarUIElement

@Composable
fun ConversationSettingsScreen(
    threadId: Long,
    onBack: () -> Unit,
) {
    val viewModel = hiltViewModel<ConversationSettingsViewModel, ConversationSettingsViewModel.Factory> { factory ->
        factory.create(threadId)
    }

    ConversationSettings(
        onBack = onBack,
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationSettings(
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            BackAppBar(
                title = stringResource(id = R.string.sessionSettings),
                onBack = onBack,
            )
        }
    ) { paddings ->
        Column(
            modifier = Modifier.padding(paddings).consumeWindowInsets(paddings),
        ) {

        }
    }

}

@Preview
@Composable
private fun ConversationSettingsPreview() {
    PreviewTheme {

        ConversationSettings(
            onBack = {},
        )
    }
}
