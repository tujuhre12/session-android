package org.thoughtcrime.securesms.conversation.v2.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.thoughtcrime.securesms.conversation.v2.settings.ConversationSettingsViewModel.Commands.HideNicknameDialog
import org.thoughtcrime.securesms.conversation.v2.settings.ConversationSettingsViewModel.Commands.RemoveNickname
import org.thoughtcrime.securesms.conversation.v2.settings.ConversationSettingsViewModel.Commands.SetNickname
import org.thoughtcrime.securesms.conversation.v2.settings.ConversationSettingsViewModel.Commands.UpdateNickname
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.components.annotatedStringResource
import org.thoughtcrime.securesms.ui.qaTag
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.PreviewTheme

@Composable
fun ConversationSettingsDialogs(
    dialogsState: ConversationSettingsViewModel.DialogsState,
    sendCommand: (ConversationSettingsViewModel.Commands) -> Unit
){
    val context = LocalContext.current

    // Nickname
    if(dialogsState.nicknameDialog != null){

        AlertDialog(
            onDismissRequest = {
                // hide dialog
                sendCommand(HideNicknameDialog)
            },
            title = AnnotatedString(stringResource(R.string.nicknameSet)),
            text = annotatedStringResource(Phrase.from(context, R.string.nicknameDescription)
                .put(NAME_KEY, dialogsState.nicknameDialog.name)
                .format()),
            showCloseButton = true,
            content = {
                SessionOutlinedTextField(
                    text = dialogsState.nicknameDialog.inputNickname ?: "",
                    modifier = Modifier.qaTag(R.string.AccessibilityId_sessionIdInput)
                        .padding(top = LocalDimensions.current.smallSpacing),
                    placeholder = stringResource(R.string.accountIdOrOnsEnter),
                    onChange = { updatedText ->
                        sendCommand(UpdateNickname(updatedText))
                    },
                    onContinue = { sendCommand(SetNickname) },
                    error = dialogsState.nicknameDialog.error,
                )
            },
            buttons = listOf(
                DialogButtonModel(
                    text = GetString(stringResource(id = R.string.save)),
                    enabled = dialogsState.nicknameDialog.setEnabled,
                    onClick = { sendCommand(SetNickname) }
                ),
                DialogButtonModel(
                    text = GetString(stringResource(R.string.remove)),
                    color = LocalColors.current.danger,
                    enabled = dialogsState.nicknameDialog.removeEnabled,
                    onClick = {
                        sendCommand(RemoveNickname)
                    }
                )
            )
        )
    }
}

@Preview
@Composable
fun PreviewNicknameSetDialog() {
    PreviewTheme {
        ConversationSettingsDialogs(
            dialogsState = ConversationSettingsViewModel.DialogsState(
                nicknameDialog = ConversationSettingsViewModel.NicknameDialogData(
                    name = "Rick",
                    currentNickname = "Razza",
                    inputNickname = "Rickety",
                    setEnabled = true,
                    removeEnabled = true,
                    error = null,
                )
            ),
            sendCommand = {}
        )
    }
}


@Preview
@Composable
fun PreviewNicknameEmptyDialog() {
    PreviewTheme {
        ConversationSettingsDialogs(
            dialogsState = ConversationSettingsViewModel.DialogsState(
                nicknameDialog = ConversationSettingsViewModel.NicknameDialogData(
                    name = "Rick",
                    currentNickname = null,
                    inputNickname = null,
                    setEnabled = false,
                    removeEnabled = false,
                    error = null,
                )
            ),
            sendCommand = {}
        )
    }
}

@Preview
@Composable
fun PreviewNicknameEmptyWithInputDialog() {
    PreviewTheme {
        ConversationSettingsDialogs(
            dialogsState = ConversationSettingsViewModel.DialogsState(
                nicknameDialog = ConversationSettingsViewModel.NicknameDialogData(
                    name = "Rick",
                    currentNickname = null,
                    inputNickname = "Rickety",
                    setEnabled = true,
                    removeEnabled = false,
                    error = null,
                )
            ),
            sendCommand = {}
        )
    }
}