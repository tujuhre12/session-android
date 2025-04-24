package org.thoughtcrime.securesms.conversation.v2.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import network.loki.messenger.R
import org.thoughtcrime.securesms.groups.compose.ExpandableText
import org.thoughtcrime.securesms.ui.components.Avatar
import org.thoughtcrime.securesms.ui.components.BackAppBar
import org.thoughtcrime.securesms.ui.qaTag
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.bold
import org.thoughtcrime.securesms.ui.theme.monospace
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

    val data by viewModel.uiState.collectAsState()

    ConversationSettings(
        data = data,
        onBack = onBack,
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationSettings(
    data: ConversationSettingsViewModel.UIState,
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
            modifier = Modifier.padding(paddings).consumeWindowInsets(paddings)
                .fillMaxWidth()
                .padding(
                    horizontal = LocalDimensions.current.spacing,
                    vertical = LocalDimensions.current.smallSpacing
                ),
            horizontalAlignment = CenterHorizontally
        ) {
            // Profile picture
            Avatar(
                modifier = Modifier.qaTag(R.string.qa_conversation_settings_avatar),
                size = LocalDimensions.current.xlargeSpacing,
                data = data.avatarUIData
            )

            Spacer(modifier = Modifier.height(LocalDimensions.current.spacing))

            // name and edit icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    modifier = Modifier.qaTag(R.string.qa_conversation_settings_name)
                        .weight(
                            weight = 1.0f,
                            fill = false,
                        ),
                    text = data.name,
                    textAlign = TextAlign.Center,
                    style = LocalType.current.h4,
                    color = LocalColors.current.text
                )

                if(data.canEditName) {
                    //todo UCS check rtl ltr behaviour
                    Image(
                        modifier = Modifier.padding(start = LocalDimensions.current.xxsSpacing)
                            .size(LocalDimensions.current.iconSmall),
                        painter = painterResource(R.drawable.ic_pencil),
                        colorFilter = ColorFilter.tint(LocalColors.current.textSecondary),
                        contentDescription = null,
                    )
                }
            }

            // description or display name
            if(!data.description.isNullOrEmpty()){
                Spacer(modifier = Modifier.height(LocalDimensions.current.xxsSpacing))
                ExpandableText(
                    text = data.description,
                    textStyle = LocalType.current.small,
                    textColor = LocalColors.current.textSecondary,
                    buttonTextStyle = LocalType.current.base.bold(),
                    buttonTextColor = LocalColors.current.textSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // account ID
            if(!data.accountId.isNullOrEmpty()){
                Spacer(modifier = Modifier.height(LocalDimensions.current.xxsSpacing))
                Text(
                    modifier = Modifier.qaTag(R.string.qa_conversation_settings_account_id),
                    text = data.accountId,
                    textAlign = TextAlign.Center,
                    style = LocalType.current.base.monospace(),
                    color = LocalColors.current.text
                )
            }
        }
    }

}

@Preview
@Composable
private fun ConversationSettings1on1Preview() {
    PreviewTheme {

        ConversationSettings(
            onBack = {},
            data = ConversationSettingsViewModel.UIState(
                name = "Nickname",
                canEditName = true,
                description = "(Real name)",
                accountId = "05000000000000000000000000000000000000000000000000000000000000000",
                avatarUIData = AvatarUIData(
                    listOf(
                        AvatarUIElement(
                            name = "TO",
                            color = primaryBlue
                        )
                    )
                ),
            )
        )
    }
}

@Preview
@Composable
private fun ConversationSettings1on1LongNamePreview() {
    PreviewTheme {

        ConversationSettings(
            onBack = {},
            data = ConversationSettingsViewModel.UIState(
                name = "Nickname that is very long but the text shouldn't be cut off because there is no limit to the display here so it should show the whole thing",
                canEditName = true,
                description = "This is a long description asdklajsdklj aslkd lkasjdasldkj jlasdjasldkj alskdjaslkdj lkasdjlkasdjalskdj lakjsdlkasd lkasjdlaksdj lkasjdlaksjdalsk ljkasjdaskl",
                accountId = "05000000000000000000000000000000000000000000000000000000000000000",
                avatarUIData = AvatarUIData(
                    listOf(
                        AvatarUIElement(
                            name = "TO",
                            color = primaryBlue
                        )
                    )
                ),
            )
        )
    }
}
