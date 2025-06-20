package org.thoughtcrime.securesms.conversation.v2

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.EMOJI_KEY
import org.thoughtcrime.securesms.conversation.v2.ConversationViewModel.Commands.ClearEmoji
import org.thoughtcrime.securesms.conversation.v2.ConversationViewModel.Commands.ConfirmRecreateGroup
import org.thoughtcrime.securesms.conversation.v2.ConversationViewModel.Commands.HideClearEmoji
import org.thoughtcrime.securesms.conversation.v2.ConversationViewModel.Commands.HideDeleteEveryoneDialog
import org.thoughtcrime.securesms.conversation.v2.ConversationViewModel.Commands.HideRecreateGroupConfirm
import org.thoughtcrime.securesms.conversation.v2.ConversationViewModel.Commands.MarkAsDeletedForEveryone
import org.thoughtcrime.securesms.conversation.v2.ConversationViewModel.Commands.MarkAsDeletedLocally
import org.thoughtcrime.securesms.conversation.v2.ConversationViewModel.Commands.ShowOpenUrlDialog
import org.thoughtcrime.securesms.groups.compose.CreateGroupScreen
import org.thoughtcrime.securesms.openUrl
import org.thoughtcrime.securesms.pro.ProStatusManager
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogBg
import org.thoughtcrime.securesms.ui.DialogButtonData
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.OpenURLAlertDialog
import org.thoughtcrime.securesms.ui.RadioOption
import org.thoughtcrime.securesms.ui.components.DialogTitledRadioButton
import org.thoughtcrime.securesms.ui.components.PrimaryFillButtonRect
import org.thoughtcrime.securesms.ui.components.TertiaryFillButtonRect
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionMaterialTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationV2Dialogs(
    dialogsState: ConversationViewModel.DialogsState,
    sendCommand: (ConversationViewModel.Commands) -> Unit
){
    SessionMaterialTheme {
        // open link confirmation
        if(!dialogsState.openLinkDialogUrl.isNullOrEmpty()){
            OpenURLAlertDialog(
                url = dialogsState.openLinkDialogUrl,
                onDismissRequest = {
                    // hide dialog
                    sendCommand(ShowOpenUrlDialog(null))
                }
            )
        }

        // delete message(s)
        if(dialogsState.deleteEveryone != null){
            val data = dialogsState.deleteEveryone
            var deleteForEveryone by remember { mutableStateOf(data.defaultToEveryone)}

            AlertDialog(
                onDismissRequest = {
                    // hide dialog
                    sendCommand(HideDeleteEveryoneDialog)
                },
                title = pluralStringResource(
                    R.plurals.deleteMessage,
                    data.messages.size,
                    data.messages.size
                ),
                text = pluralStringResource(
                    R.plurals.deleteMessageConfirm,
                    data.messages.size,
                    data.messages.size
                ),
                content = {
                    // add warning text, if any
                    data.warning?.let {
                        Text(
                            text = it,
                            textAlign = TextAlign.Center,
                            style = LocalType.current.small,
                            color = LocalColors.current.warning,
                            modifier = Modifier.padding(
                                top = LocalDimensions.current.xxxsSpacing,
                                bottom = LocalDimensions.current.xxsSpacing
                            )
                        )
                    }

                    DialogTitledRadioButton(
                        option = RadioOption(
                            value = Unit,
                            title = GetString(stringResource(R.string.deleteMessageDeviceOnly)),
                            selected = !deleteForEveryone
                        )
                    ) {
                        deleteForEveryone = false
                    }

                    DialogTitledRadioButton(
                        option = RadioOption(
                            value = Unit,
                            title = GetString(data.deleteForEveryoneLabel),
                            selected = deleteForEveryone,
                            enabled = data.everyoneEnabled
                        )
                    ) {
                        deleteForEveryone = true
                    }
                },
                buttons = listOf(
                    DialogButtonData(
                        text = GetString(stringResource(id = R.string.delete)),
                        color = LocalColors.current.danger,
                        onClick = {
                            // delete messages based on chosen option
                            sendCommand(
                                if(deleteForEveryone) MarkAsDeletedForEveryone(
                                    data.copy(defaultToEveryone = deleteForEveryone)
                                )
                                else MarkAsDeletedLocally(data.messages)
                            )
                        }
                    ),
                    DialogButtonData(
                        GetString(stringResource(R.string.cancel))
                    )
                )
            )
        }

        // Clear emoji
        if(dialogsState.clearAllEmoji != null){
            AlertDialog(
                onDismissRequest = {
                    // hide dialog
                    sendCommand(HideClearEmoji)
                },
                text = stringResource(R.string.emojiReactsClearAll).let { txt ->
                    Phrase.from(txt).put(EMOJI_KEY, dialogsState.clearAllEmoji.emoji).format().toString()
                },
                buttons = listOf(
                    DialogButtonData(
                        text = GetString(stringResource(id = R.string.clear)),
                        color = LocalColors.current.danger,
                        onClick = {
                            // delete emoji
                            sendCommand(
                                ClearEmoji(dialogsState.clearAllEmoji.emoji, dialogsState.clearAllEmoji.messageId)
                            )
                        }
                    ),
                    DialogButtonData(
                        GetString(stringResource(R.string.cancel))
                    )
                )
            )
        }

        if (dialogsState.recreateGroupConfirm) {
            AlertDialog(
                onDismissRequest = {
                    // hide dialog
                    sendCommand(HideRecreateGroupConfirm)
                },
                title = stringResource(R.string.recreateGroup),
                text = stringResource(R.string.legacyGroupChatHistory),
                buttons = listOf(
                    DialogButtonData(
                        text = GetString(stringResource(id = R.string.theContinue)),
                        color = LocalColors.current.danger,
                        onClick = {
                            sendCommand(ConfirmRecreateGroup)
                        }
                    ),
                    DialogButtonData(
                        GetString(stringResource(R.string.cancel))
                    )
                )
            )
        }

        if (dialogsState.recreateGroupData != null) {
            val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            ModalBottomSheet(
                onDismissRequest = {
                    sendCommand(ConversationViewModel.Commands.HideRecreateGroup)
                },
                sheetState = state,
                dragHandle = null
            ) {
                CreateGroupScreen(
                    fromLegacyGroupId = dialogsState.recreateGroupData.legacyGroupId,
                    onNavigateToConversationScreen = { threadId ->
                        sendCommand(ConversationViewModel.Commands.NavigateToConversation(threadId))
                    },
                    onBack = {
                        sendCommand(ConversationViewModel.Commands.HideRecreateGroup)
                    },
                    onClose = {
                        sendCommand(ConversationViewModel.Commands.HideRecreateGroup)
                    },
                )
            }
        }

        // Pro CTA
        if (dialogsState.sessionProCTA) {
            SessionProCTA(
                sendCommand = sendCommand
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionProCTA(
    modifier: Modifier = Modifier,
    sendCommand: (ConversationViewModel.Commands) -> Unit
){
    val context = LocalContext.current

    BasicAlertDialog(
        modifier = modifier,
        onDismissRequest = {
            sendCommand(ConversationViewModel.Commands.HideSessionProCTA)
        },
        content = {
            DialogBg {

                Column(modifier = Modifier.fillMaxWidth()) {
                    // hero image
                    Image(
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth,
                        painter = painterResource(id = R.drawable.pro_cta),
                        contentDescription = null,
                    )

                    // content
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(LocalDimensions.current.smallSpacing)
                    ) {
                        // title
                        Row(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.xxxsSpacing)

                        ) {
                            Text(
                                text = ProStatusManager.UPDATETP,
                                style = LocalType.current.h5
                            )

                            Image(
                                painter = painterResource(id = R.drawable.ic_pro_badge),
                                contentScale = ContentScale.FillHeight,
                                contentDescription = ProStatusManager.PRO,
                            )
                        }

                        Spacer(Modifier.height(LocalDimensions.current.smallSpacing))

                        // main message
                        Text(
                            text = ProStatusManager.CTA_TXT,
                            textAlign = TextAlign.Center,
                            style = LocalType.current.base.copy(
                                color = LocalColors.current.textSecondary
                            )
                        )

                        Spacer(Modifier.height(LocalDimensions.current.smallSpacing))

                        // features
                        ProCTAFeature(text = ProStatusManager.CTA_FEAT1)
                        Spacer(Modifier.height(LocalDimensions.current.xsSpacing))
                        ProCTAFeature(text = ProStatusManager.CTA_FEAT2)
                        Spacer(Modifier.height(LocalDimensions.current.xsSpacing))
                        ProCTAFeature(text = ProStatusManager.CTA_FEAT3)

                        Spacer(Modifier.height(LocalDimensions.current.smallSpacing))

                        // buttons
                        Row(
                            Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.xsSpacing),
                        ) {
                            PrimaryFillButtonRect(
                                modifier = Modifier.weight(1f),
                                text = ProStatusManager.UPGRADE,
                                onClick = {
                                    sendCommand(ConversationViewModel.Commands.HideSessionProCTA)
                                    context.openUrl(ProStatusManager.PRO_URL)
                                }

                            )

                            TertiaryFillButtonRect(
                                modifier = Modifier.weight(1f),
                                text = stringResource(R.string.cancel),
                                onClick = {
                                    sendCommand(ConversationViewModel.Commands.HideSessionProCTA)
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun ProCTAFeature(
    text: String,
    modifier: Modifier = Modifier
){
    Row(
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = LocalDimensions.current.xxxsSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.xxsSpacing)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_circle_check),
            colorFilter = ColorFilter.tint(LocalColors.current.primary),
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterVertically)
        )

        Text(
            text = text,
            style = LocalType.current.base
        )
    }
}

@Preview
@Composable
fun PreviewSessionProCTA(){
    PreviewTheme {
        SessionProCTA(
            sendCommand = {}
        )
    }
}

@Preview
@Composable
fun PreviewURLDialog(){
    PreviewTheme {
        ConversationV2Dialogs(
            dialogsState = ConversationViewModel.DialogsState(
                openLinkDialogUrl = "https://google.com"
            ),
            sendCommand = {}
        )
    }
}
