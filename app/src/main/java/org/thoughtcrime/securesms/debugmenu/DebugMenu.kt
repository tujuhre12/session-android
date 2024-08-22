package org.thoughtcrime.securesms.debugmenu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.thoughtcrime.securesms.ui.components.AppBarCloseIcon
import org.thoughtcrime.securesms.ui.components.AppBarText
import org.thoughtcrime.securesms.ui.components.appBarColors
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.PreviewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugMenu(
    modifier: Modifier = Modifier,
    onClose: () -> Unit
){
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color = LocalColors.current.background)
    ) {
        // App bar
        CenterAlignedTopAppBar(
            modifier = modifier,
            title = {
                AppBarText(title = "Debug Menu")
            },
            colors = appBarColors(LocalColors.current.background),
            navigationIcon = {
                AppBarCloseIcon(onClose = onClose)
            }
        )

        // Info pane
        Box(
            modifier = Modifier.fillMaxWidth()
               // .background()
        )
    }
}

@Preview
@Composable
fun PreviewDebugMenu(){
    PreviewTheme {
        DebugMenu(
            onClose = {}
        )
    }
}

/*
package net.artprocessors.eileen_capstone_ui.debug.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.jakewharton.processphoenix.ProcessPhoenix
import net.artprocessors.debug_ui.common.BaseButton
import net.artprocessors.debug_ui.common.BaseDebugTab
import net.artprocessors.debug_ui.common.DebugColorBg
import net.artprocessors.debug_ui.common.DebugColorFill
import net.artprocessors.debug_ui.common.DebugColorPrimary
import net.artprocessors.debug_ui.common.DebugColorTextSecondary
import net.artprocessors.debug_ui.common.DebugColorTextTertiary
import net.artprocessors.debug_ui.common.DebugTextCaption
import net.artprocessors.debug_ui.common.DebugTextNormal
import net.artprocessors.debug_ui.common.DebugTextTitle
import net.artprocessors.debug_ui.common.LabeledDropDown
import net.artprocessors.debug_ui.common.ShadowHorizontalSeparator
import net.artprocessors.debug_ui.common.l
import net.artprocessors.debug_ui.common.m
import net.artprocessors.debug_ui.common.s
import net.artprocessors.debug_ui.common.xl
import net.artprocessors.debug_ui.common.xs
import net.artprocessors.debug_ui.common.xxs
import net.artprocessors.eileen_capstone_ui.debug.model.AppInfo
import net.artprocessors.eileen_capstone_ui.debug.model.DeviceInfo
import net.artprocessors.eileen_capstone_ui.debug.model.SettingsCommand
import net.artprocessors.eileen_capstone_ui.debug.model.SettingsData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebugSettingsTabContent(
    modifier: Modifier = Modifier,
    title: String,
    data: SettingsData,
    sendCommand: (SettingsCommand) -> Unit,
    onReset: () -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    val modalBottomSheetState = rememberModalBottomSheetState()

    val context = LocalContext.current

    BaseDebugTab(
        title = title,
        appBarEndContent = {
            IconButton(
                onClick = { showSheet = true }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    tint = DebugColorPrimary,
                    contentDescription = "Additional info"
                )
            }
        }
    ) {
        Spacer(modifier = Modifier.height(m()))

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = s())
                .verticalScroll(scrollState)
        ) {
            // Environments
            LabeledDropDown(
                selectedText = data.selectedEnv,
                label = "Environment",
                values = data.envNames,
                onValueSelected = {
                    sendCommand(SettingsCommand.SetEnvironment(it))
                }
            )

            Spacer(modifier = Modifier.height(s()))

            // Modes
            LabeledDropDown(
                selectedText = data.selectedMode,
                label = "Mode",
                values = data.modeNames,
                onValueSelected = {
                    sendCommand(SettingsCommand.SetMode(it))
                }
            )

            Spacer(modifier = Modifier.height(s()))

            // Site Status
            LabeledDropDown(
                selectedText = data.selectedSiteStatus,
                label = "Site Status (Doesn't require a 'Reset' to take effect)",
                values = data.siteNames,
                onValueSelected = {
                    sendCommand(SettingsCommand.SetSiteStatus(it))
                }
            )

            Spacer(modifier = Modifier.height(s()))

            // Positioning
            LabeledDropDown(
                selectedText = data.selectedPositioning,
                label = "Positioning System",
                values = data.positioning,
                onValueSelected = {
                    sendCommand(SettingsCommand.SetPositioning(it))
                }
            )
        }

        ShadowHorizontalSeparator()

        Spacer(modifier = Modifier.height(s()))


        BaseButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s()),
            text = "Reset",
            onClick = {
                // call the custom reset that can be provided by client of this composable
                onReset()

                // kill and restart the application
                ProcessPhoenix.triggerRebirth(context)
            },
        )

        Spacer(modifier = Modifier.height(xs()))

        Text(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = s()),
            text = "Hit 'Reset' for these changes to take effect",
            style = DebugTextCaption,
            color = DebugColorTextSecondary
        )

        Spacer(modifier = Modifier.height(s()))
    }

    if (showSheet) {
        ModalBottomSheet(
            containerColor = DebugColorBg,
            onDismissRequest = { showSheet = false },
            sheetState = modalBottomSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle(color = DebugColorTextTertiary) },
        ) {
            AboutAppScreen(appInfo = data.aboutData.appInfo, deviceInfo = data.aboutData.deviceInfo)
        }
    }
}

@Composable
private fun AboutAppScreen(appInfo: AppInfo, deviceInfo: DeviceInfo) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.verticalScroll(scrollState)
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = s()),
            text = "About the App",
            textAlign = TextAlign.Center,
            style = DebugTextTitle
        )

        HorizontalDivider(color = DebugColorTextTertiary)

        Card(
            modifier = Modifier.padding(horizontal = m(), vertical = s()),
            colors = CardDefaults.cardColors(containerColor = DebugColorFill),
        ) {
            Column(
                modifier = Modifier.padding(xs())
            ) {
                val context = LocalContext.current.applicationContext
                val appContextInfo = context.applicationInfo
                val appLabelRes = appContextInfo.labelRes
                with(appInfo) {
                    InfoRow(
                        data = "Name",
                        value = if (appLabelRes == 0) "${appContextInfo.nonLocalizedLabel}" else stringResource(appLabelRes)
                    )
                    InfoRow(data = "ID", value = context.packageName)
                    InfoRow(data = "Variant", value = variant)
                    InfoRow(data = "Version", value = version, isLast = true)
                }
            }
        }

        Card(
            modifier = Modifier.padding(horizontal = m(), vertical = s()),
            colors = CardDefaults.cardColors(containerColor = DebugColorFill),
        ) {
            Column(
                modifier = Modifier.padding(xs())
            ) {
                with(deviceInfo) {
                    InfoRow(data = "Language", value = language)
                    InfoRow(data = "Time Zone", value = timezone)
                    InfoRow(data = "OS", value = os)
                    InfoRow(data = "Hardware", value = hardware, isLast = true)
                }
            }
        }

        Text(
            modifier = Modifier
                .padding(horizontal = l())
                .padding(bottom = xl()),
            text = "Powered by Pladia™ · Built by Art Processors\n\n" +
                    "Art Processors acknowledges the Palawa, Wurundjeri and all traditional custodians of the lands on which we work. We acknowledge their long history of story telling and pay our respects to their elders past, present and emerging.",
            style = DebugTextCaption.copy(color = DebugColorTextSecondary)
        )
    }
}

@Composable
private fun InfoRow(data: String, value: String, isLast: Boolean = false) {
    Row(
        modifier = Modifier
            .padding(xs())
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(xxs()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = data, style = DebugTextNormal)
        Text(
            modifier = Modifier.weight(1f),
            text = value,
            style = DebugTextCaption.copy(color = DebugColorTextSecondary),
            textAlign = TextAlign.End
        )
    }
    if (!isLast) HorizontalDivider(color = DebugColorTextTertiary)
}

@Preview(showBackground = true)
@Composable
private fun PreviewAboutAppScreen() {
    Surface(modifier = Modifier.background(color = DebugColorBg)) {
        AboutAppScreen(
            appInfo = AppInfo(
                variant = "BYOD Test",
                version = "24.2.0 (23111000)"
            ),
            deviceInfo = DeviceInfo(
                language = "English [en]",
                timezone = "Australia/Melbourne",
                os = "Android 14",
                hardware = "Pixel 7"
            )
        )
    }
}
 */