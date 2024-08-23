package org.thoughtcrime.securesms.debugmenu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import network.loki.messenger.BuildConfig
import org.thoughtcrime.securesms.ui.Cell
import org.thoughtcrime.securesms.ui.components.AppBarCloseIcon
import org.thoughtcrime.securesms.ui.components.AppBarText
import org.thoughtcrime.securesms.ui.components.BackAppBar
import org.thoughtcrime.securesms.ui.components.DropDown
import org.thoughtcrime.securesms.ui.components.appBarColors
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.bold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugMenu(
    uiState: DebugMenuViewModel.UIState,
    sendCommand: (DebugMenuViewModel.Commands) -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit
){
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color = LocalColors.current.background)
    ) {
        // App bar
        BackAppBar(title = "Debug Menu", onBack = onClose)

        Column(
            modifier = Modifier
                .padding(horizontal = LocalDimensions.current.spacing)
                .verticalScroll(rememberScrollState())
        ) {
            // Info pane
            DebugCell("App Info"){
                Text(
                    text = "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE} - ${BuildConfig.GIT_HASH.take(6)})",
                    style = LocalType.current.base
                )
            }

            // Environment
            DebugCell("Environment"){
               DropDown(
                   modifier = Modifier.fillMaxWidth(0.6f),
                   selectedText = uiState.currentEnvironment,
                   values = uiState.environments,
                   onValueSelected = {
                       sendCommand(DebugMenuViewModel.Commands.ChangeEnvironment(it))
                   }
               )
            }
        }
    }
}

@Composable
fun ColumnScope.DebugCell(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Spacer(modifier = Modifier.height(LocalDimensions.current.smallSpacing))

    Cell {
        Column(
            modifier = modifier.padding(LocalDimensions.current.spacing)
        ) {
            Text(
                text = title,
                style = LocalType.current.large.bold()
            )

            Spacer(modifier = Modifier.height(LocalDimensions.current.xsSpacing))

            content()
        }
    }
}

@Preview
@Composable
fun PreviewDebugMenu(){
    PreviewTheme {
        DebugMenu(
            uiState = DebugMenuViewModel.UIState(
                currentEnvironment = "Development",
                environments = listOf("Development", "Production")
            ),
            sendCommand = {},
            onClose = {}
        )
    }
}