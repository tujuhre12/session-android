package org.thoughtcrime.securesms.preferences.prosettings

import androidx.compose.runtime.Composable
import org.thoughtcrime.securesms.ui.OpenURLAlertDialog
import org.thoughtcrime.securesms.preferences.prosettings.ProSettingsViewModel.Commands.*

@Composable
fun ProSettingsDialogs(
    dialogsState: ProSettingsViewModel.DialogsState,
    sendCommand: (ProSettingsViewModel.Commands) -> Unit
){
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
}