package org.thoughtcrime.securesms.preferences.prosettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import org.thoughtcrime.securesms.openUrl
import org.thoughtcrime.securesms.ui.OpenURLAlertDialog
import org.thoughtcrime.securesms.preferences.prosettings.ProSettingsViewModel.Commands.*
import org.thoughtcrime.securesms.ui.AlertDialog
import org.thoughtcrime.securesms.ui.Cell
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.IconActionRowItem
import org.thoughtcrime.securesms.ui.components.annotatedStringResource
import org.thoughtcrime.securesms.ui.theme.LocalColors
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

    if(dialogsState.showTCPolicyDialog){
        TCPolicyDialog(
            sendCommand = sendCommand
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
            Spacer(Modifier.height(LocalDimensions.current.xsSpacing))
            Cell(
              bgColor = LocalColors.current.backgroundTertiary
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val context = LocalContext.current
                    val spacing = LocalDimensions.current.xsSpacing

                    val tcsUrl = "https://getsession.org/pro/terms"
                    IconActionRowItem(
                        title = annotatedStringResource(tcsUrl),
                        textStyle = LocalType.current.large.bold(),
                        icon = R.drawable.ic_square_arrow_up_right,
                        iconSize = LocalDimensions.current.iconSmall,
                        paddingValues = PaddingValues(start = spacing),
                        qaTag = R.string.AccessibilityId_onboardingTos,
                        onClick = {
                            context.openUrl(tcsUrl)
                        }
                    )
                    Divider(paddingValues = PaddingValues(horizontal = spacing))
                    val privacyUrl = "https://getsession.org/pro/privacy"
                    IconActionRowItem(
                        title = annotatedStringResource(privacyUrl),
                        textStyle = LocalType.current.large.bold(),
                        icon = R.drawable.ic_square_arrow_up_right,
                        iconSize = LocalDimensions.current.iconSmall,
                        paddingValues = PaddingValues(start = spacing),
                        qaTag = R.string.AccessibilityId_onboardingPrivacy,
                        onClick = {
                            context.openUrl(privacyUrl)
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