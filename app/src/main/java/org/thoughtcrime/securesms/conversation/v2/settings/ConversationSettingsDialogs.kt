package org.thoughtcrime.securesms.conversation.v2.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.GROUP_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.thoughtcrime.securesms.conversation.v2.settings.ConversationSettingsViewModel.Commands.*
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.DialogButtonModel
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.RadioOption
import org.thoughtcrime.securesms.ui.components.DialogTitledRadioButton
import org.thoughtcrime.securesms.ui.components.annotatedStringResource
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
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
            content = {
                //todo UCS add input
            },
            buttons = listOf(
                DialogButtonModel(
                    text = GetString(stringResource(id = R.string.save)),
                    onClick = {
                        // delete messages based on chosen option
                        sendCommand(SetNickname("")) //todo UCS set real data (or will it be in the VM already?)
                    } //todo UCS handle disabled
                ),
                DialogButtonModel(
                    text = GetString(stringResource(R.string.remove)),
                    color = LocalColors.current.danger,
                    onClick = {
                        sendCommand(RemoveNickname)
                    } //todo UCS handle disabled
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
                    name = "Thomas",
                    nickname = "Toto"
                )
            ),
            sendCommand = {}
        )
    }
}

@Preview
@Composable
fun PreviewNicknameEmptytDialog() {
    PreviewTheme {
        ConversationSettingsDialogs(
            dialogsState = ConversationSettingsViewModel.DialogsState(
                nicknameDialog = ConversationSettingsViewModel.NicknameDialogData(
                    name = "Thomas",
                    nickname = null
                )
            ),
            sendCommand = {}
        )
    }
}