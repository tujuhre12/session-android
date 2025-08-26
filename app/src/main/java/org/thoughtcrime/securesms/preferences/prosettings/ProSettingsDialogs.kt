package org.thoughtcrime.securesms.preferences.prosettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.NonTranslatableStringConstants
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.PRO_KEY
import org.thoughtcrime.securesms.ui.OpenURLAlertDialog
import org.thoughtcrime.securesms.preferences.prosettings.ProSettingsViewModel.Commands.*
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.Cell
import org.thoughtcrime.securesms.ui.IconActionRowItem
import org.thoughtcrime.securesms.ui.components.annotatedStringResource
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors
import org.thoughtcrime.securesms.ui.theme.bold

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

@Composable
fun TCPolicyDialog(
    sendCommand: (ProSettingsViewModel.Commands) -> Unit
){
    AlertDialog(
        onDismissRequest = { sendCommand(HideTCPolicyDialog) },
        title = stringResource(R.string.urlOpen),
        text = stringResource(R.string.urlOpenBrowser),
        content = {
            Cell {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(10.dp),
                ) {
                    val tcsUrl = "https://getsession.org/pro/terms"
                    IconActionRowItem(
                        title = annotatedStringResource(tcsUrl),
                        textStyle = LocalType.current.large.bold(),
                        icon = R.drawable.ic_square_arrow_up_right,
                        iconSize = LocalDimensions.current.iconSmall,
                        qaTag = R.string.AccessibilityId_onboardingTos,
                        onClick = {
                            sendCommand(ShowOpenUrlDialog(tcsUrl))
                        }
                    )
                }
            }
        }
    )
}

@Preview
@Composable
private fun PreviewCPolicyDialog(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        TCPolicyDialog(
            sendCommand = {}
        )
    }
}