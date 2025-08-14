package org.thoughtcrime.securesms.home.startconversation.community

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.emptyFlow
import network.loki.messenger.R
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.thoughtcrime.securesms.home.startconversation.community.JoinCommunityViewModel.Commands.OnQRScanned
import org.thoughtcrime.securesms.ui.ByteArraySliceImage
import org.thoughtcrime.securesms.ui.LoadingArcOr
import org.thoughtcrime.securesms.ui.components.AccentOutlineButton
import org.thoughtcrime.securesms.ui.components.AppBarCloseIcon
import org.thoughtcrime.securesms.ui.components.BackAppBar
import org.thoughtcrime.securesms.ui.components.QRScannerScreen
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.components.SessionTabRow
import org.thoughtcrime.securesms.ui.qaTag
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors
import org.thoughtcrime.securesms.util.State

private val TITLES = listOf(R.string.communityUrl, R.string.qrScan)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JoinCommunityScreen(
    state: JoinCommunityViewModel.JoinCommunityState,
    sendCommand: (JoinCommunityViewModel.Commands) -> Unit,
    onClose: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val pagerState = rememberPagerState { TITLES.size }

    //todo NEWCONVO display an overall loader for the state.loading

    Column(modifier = Modifier.background(
        LocalColors.current.backgroundSecondary,
        shape = MaterialTheme.shapes.small
    )) {
        BackAppBar(
            title = stringResource(R.string.communityJoin),
            backgroundColor = Color.Transparent, // transparent to show the rounded shape of the container
            onBack = onBack,
            actions = { AppBarCloseIcon(onClose = onClose) },
            windowInsets = WindowInsets(0, 0, 0, 0), // Insets handled by the dialog
        )
        SessionTabRow(pagerState, TITLES)
        HorizontalPager(pagerState) {
            when (TITLES[it]) {
                R.string.communityUrl -> CommunityScreen(
                    state = state,
                    sendCommand = sendCommand
                )
                R.string.qrScan -> QRScannerScreen(errors = emptyFlow(), onScan = { sendCommand(OnQRScanned(it)) })
            }
        }
    }
}


@Composable
private fun CommunityScreen(
    state: JoinCommunityViewModel.JoinCommunityState,
    sendCommand: (JoinCommunityViewModel.Commands) -> Unit
) {
    Surface(color = LocalColors.current.backgroundSecondary) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(rememberNestedScrollInteropConnection())
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier.padding(vertical = LocalDimensions.current.spacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SessionOutlinedTextField(
                    text = state.communityUrl,
                    modifier = Modifier
                        .padding(horizontal = LocalDimensions.current.spacing)
                        .qaTag(R.string.AccessibilityId_communityEnterUrl),
                    placeholder = stringResource(R.string.communityEnterUrl),
                    onChange = {
                        sendCommand(JoinCommunityViewModel.Commands.OnUrlChanged(it))
                    },
                    onContinue = {
                        sendCommand(JoinCommunityViewModel.Commands.JoinCommunity)
                    },
                    error = null, //todo NEWCONVO add
                    isTextErrorColor = false //todo NEWCONVO add
                )

            }

            Spacer(Modifier
                .weight(1f)
                .heightIn(min = LocalDimensions.current.smallSpacing))

            AccentOutlineButton(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = LocalDimensions.current.xlargeSpacing)
                    .padding(bottom = LocalDimensions.current.smallSpacing)
                    .fillMaxWidth()
                    .qaTag(R.string.AccessibilityId_communityJoin),
                enabled = state.isJoinButtonEnabled,
                onClick = {
                    sendCommand(JoinCommunityViewModel.Commands.JoinCommunity)
                }
            ) {
                LoadingArcOr(state.loading) {
                    Text(stringResource(R.string.join))
                }
            }
        }
    }

}

@Composable
private fun CommunityChip(
    modifier: Modifier = Modifier,
    group: OpenGroupApi.DefaultGroup,
    onClick: () -> Unit
){
    AssistChip(
        modifier = modifier,
        onClick = onClick,
        label = {
            Text(
                text = group.name,
                style = LocalType.current.base,
            )
        },
        leadingIcon = {
            if (group.image != null) {
                ByteArraySliceImage(
                    slice = group.image
                )
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        colors = AssistChipDefaults.assistChipColors().copy(
            containerColor = LocalColors.current.backgroundSecondary,
            labelColor = LocalColors.current.text
        ),
        border = BorderStroke(
            width = 1.dp,
            color = LocalColors.current.borders
        )
    )
}

@Preview
@Composable
private fun PreviewNewMessage(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        JoinCommunityScreen(
            state = JoinCommunityViewModel.JoinCommunityState(defaultCommunities = State.Loading),
            sendCommand = {}
        )
    }
}

@Preview
@Composable
private fun PreviewCommunityChip(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        Box(
            modifier = Modifier
                .background(LocalColors.current.background)
                .padding(12.dp)
        ) {
            CommunityChip(
                group = OpenGroupApi.DefaultGroup(
                    id = "id",
                    name = "Session community",
                    image = null
                ),
                onClick = {}
            )
        }
    }
}
