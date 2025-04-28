package org.thoughtcrime.securesms.conversation.v2.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.Cell
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.ExpandableText
import org.thoughtcrime.securesms.ui.LargeItemButton
import org.thoughtcrime.securesms.ui.components.Avatar
import org.thoughtcrime.securesms.ui.components.BackAppBar
import org.thoughtcrime.securesms.ui.getCellBottomShape
import org.thoughtcrime.securesms.ui.getCellTopShape
import org.thoughtcrime.securesms.ui.qaTag
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.bold
import org.thoughtcrime.securesms.ui.theme.dangerButtonColors
import org.thoughtcrime.securesms.ui.theme.monospace
import org.thoughtcrime.securesms.ui.theme.primaryBlue
import org.thoughtcrime.securesms.ui.theme.transparentButtonColors
import org.thoughtcrime.securesms.util.AvatarUIData
import org.thoughtcrime.securesms.util.AvatarUIElement

@Composable
fun ConversationSettingsScreen(
    viewModel: ConversationSettingsViewModel,
    onBack: () -> Unit,
) {
    val data by viewModel.uiState.collectAsState()

    ConversationSettings(
        data = data,
        sendCommand = viewModel::onCommand,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationSettings(
    data: ConversationSettingsViewModel.UIState,
    sendCommand: (ConversationSettingsViewModel.Commands) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            BackAppBar(
                title = stringResource(id = R.string.sessionSettings),
                onBack = onBack,
            )
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
    ) { paddings ->

        Column(
            modifier = Modifier.fillMaxSize()
                .padding(paddings).consumeWindowInsets(paddings)
                .padding(
                    horizontal = LocalDimensions.current.spacing,
                )
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(LocalDimensions.current.spacing))

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
                    textAlign = TextAlign.Center,
                )
            }

            // account ID
            if(!data.accountId.isNullOrEmpty()){
                Spacer(modifier = Modifier.height(LocalDimensions.current.xsSpacing))
                val haptics = LocalHapticFeedback.current
                val longPressLabel = stringResource(R.string.accountIDCopy)
                val onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    sendCommand(ConversationSettingsViewModel.Commands.CopyAccountId)
                }
                Text(
                    modifier = Modifier.qaTag(R.string.qa_conversation_settings_account_id)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { onLongPress() }
                            )
                        }
                        .semantics {
                            onLongClick(label = longPressLabel) {
                                onLongPress()
                                true
                            }
                        },
                    text = data.accountId,
                    textAlign = TextAlign.Center,
                    style = LocalType.current.base.monospace(),
                    color = LocalColors.current.text
                )
            }

            // settings options
            Spacer(modifier = Modifier.height(LocalDimensions.current.spacing))
            data.categories.forEachIndexed { index, optionsCategory ->
                ConversationSettingsCategory(
                    data = optionsCategory
                )

                // add spacing
                when (index) {
                    data.categories.lastIndex -> Spacer(modifier = Modifier.height(LocalDimensions.current.spacing))
                    else -> Spacer(modifier = Modifier.height(LocalDimensions.current.smallSpacing))
                }
            }

            Spacer(modifier = Modifier.height(LocalDimensions.current.spacing))
        }
    }
}
@Composable
fun ConversationSettingsCategory(
    modifier: Modifier = Modifier,
    data: ConversationSettingsViewModel.OptionsCategory,
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (!data.name.isNullOrEmpty()) {
            Text(
                modifier = Modifier.padding(
                    start = LocalDimensions.current.smallSpacing,
                    bottom = LocalDimensions.current.smallSpacing
                ),
                text = data.name,
                style = LocalType.current.base,
                color = LocalColors.current.textSecondary
            )
        }

        data.items.forEachIndexed { index, items ->
            ConversationSettingsSubCategory(
                data = items
            )

            // add spacing, except on the last one
            if (index < data.items.lastIndex) {
                Spacer(modifier = Modifier.height(LocalDimensions.current.xxsSpacing))
            }
        }
    }
}

@Composable
fun ConversationSettingsSubCategory(
    modifier: Modifier = Modifier,
    data: ConversationSettingsViewModel.OptionsSubCategory,
) {
    Cell(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            data.items.forEachIndexed { index, option ->
                LargeItemButton(
                    text = option.name,
                    subtitle = option.subtitle,
                    icon = option.icon,
                    shape = when (index) {
                        0 -> getCellTopShape()
                        data.items.lastIndex -> getCellBottomShape()
                        else -> RectangleShape
                    },
                    colors = if(data.danger) dangerButtonColors()
                    else transparentButtonColors(),
                    onClick = option.onClick,
                )

                if(index != data.items.lastIndex) Divider()
            }
        }
    }
}


@Preview
@Composable
private fun ConversationSettings1on1Preview() {
    PreviewTheme {

        ConversationSettings(
            sendCommand = {},
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
                categories = listOf(
                    ConversationSettingsViewModel.OptionsCategory(
                        items = listOf(
                            ConversationSettingsViewModel.OptionsSubCategory(
                                items = listOf(
                                    ConversationSettingsViewModel.OptionsItem(
                                        name = "Search",
                                        icon = R.drawable.ic_search,
                                        onClick = {}
                                    ),
                                    ConversationSettingsViewModel.OptionsItem(
                                        name = "Notifications",
                                        subtitle = "All Messages",
                                        icon = R.drawable.ic_volume_2,
                                        onClick = {}
                                    )
                                )
                            )
                        )
                    ),
                    ConversationSettingsViewModel.OptionsCategory(
                        name = "Admin",
                        items = listOf(
                            ConversationSettingsViewModel.OptionsSubCategory(
                                items = listOf(
                                    ConversationSettingsViewModel.OptionsItem(
                                        name = "Invite Contacts",
                                        icon = R.drawable.ic_user_round_plus,
                                        onClick = {}
                                    ),
                                    ConversationSettingsViewModel.OptionsItem(
                                        name = "Manage members",
                                        icon = R.drawable.ic_users_group_custom,
                                        onClick = {}
                                    )
                                )
                            ),
                            ConversationSettingsViewModel.OptionsSubCategory(
                                danger = true,
                                items = listOf(
                                    ConversationSettingsViewModel.OptionsItem(
                                        name = "Clear Messages",
                                        icon = R.drawable.ic_message_square,
                                        onClick = {}
                                    ),
                                    ConversationSettingsViewModel.OptionsItem(
                                        name = "Delete Group",
                                        icon = R.drawable.ic_trash_2,
                                        onClick = {}
                                    )
                                )
                            )
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
            sendCommand = {},
            onBack = {},
            data = ConversationSettingsViewModel.UIState(
                name = "Nickname that is very long but the text shouldn't be cut off because there is no limit to the display here so it should show the whole thing",
                canEditName = true,
                description = "This is a long description with a lot of text that should be more than 2 lines and should be truncated but you never know, it depends on size and such things dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk dfkjdfklj asjdlkj lkjdf lkjsa dlkfjlk asdflkjlksdfjklasdfjasdlkfjasdflk lkasdjfalsdkfjasdklfj lsadkfjalsdkfjsadklf lksdjfalsdkfjasdlkfjasdlkf asldkfjasdlkfja and this is the end",
                accountId = "05000000000000000000000000000000000000000000000000000000000000000",
                avatarUIData = AvatarUIData(
                    listOf(
                        AvatarUIElement(
                            name = "TO",
                            color = primaryBlue
                        )
                    )
                ),
                categories = listOf(
                    ConversationSettingsViewModel.OptionsCategory(
                        items = listOf(
                            ConversationSettingsViewModel.OptionsSubCategory(
                                items = listOf(
                                    ConversationSettingsViewModel.OptionsItem(
                                        name = "Search",
                                        icon = R.drawable.ic_search,
                                        onClick = {}
                                    ),
                                    ConversationSettingsViewModel.OptionsItem(
                                        name = "Notifications",
                                        subtitle = "All Messages",
                                        icon = R.drawable.ic_volume_2,
                                        onClick = {}
                                    )
                                )
                            )
                        )
                    ),
                    ConversationSettingsViewModel.OptionsCategory(
                        name = "Admin",
                        items = listOf(
                            ConversationSettingsViewModel.OptionsSubCategory(
                                items = listOf(
                                    ConversationSettingsViewModel.OptionsItem(
                                        name = "Invite Contacts",
                                        icon = R.drawable.ic_user_round_plus,
                                        onClick = {}
                                    ),
                                    ConversationSettingsViewModel.OptionsItem(
                                        name = "Manage members",
                                        icon = R.drawable.ic_users_group_custom,
                                        onClick = {}
                                    )
                                )
                            ),
                            ConversationSettingsViewModel.OptionsSubCategory(
                                items = listOf(
                                    ConversationSettingsViewModel.OptionsItem(
                                        name = "Clear Messages",
                                        icon = R.drawable.ic_message_square,
                                        onClick = {}
                                    ),
                                    ConversationSettingsViewModel.OptionsItem(
                                        name = "Delete Group",
                                        icon = R.drawable.ic_trash_2,
                                        onClick = {}
                                    )
                                )
                            )
                        )
                    )
                ),
            )
        )
    }
}
