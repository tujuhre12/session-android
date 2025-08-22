package org.thoughtcrime.securesms.preferences.prosettings

import androidx.annotation.DrawableRes
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.squareup.phrase.Phrase
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.RELATIVE_TIME_KEY
import org.thoughtcrime.securesms.preferences.prosettings.ProSettingsViewModel.ProAccountStatus
import org.thoughtcrime.securesms.pro.ProStatusManager
import org.thoughtcrime.securesms.ui.CategoryCell
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.IconActionRowItem
import org.thoughtcrime.securesms.ui.SessionProSettingsHeader
import org.thoughtcrime.securesms.ui.SpeechBubbleTooltip
import org.thoughtcrime.securesms.ui.SwitchActionRowItem
import org.thoughtcrime.securesms.ui.components.BackAppBar
import org.thoughtcrime.securesms.ui.components.annotatedStringResource
import org.thoughtcrime.securesms.ui.qaTag
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors
import org.thoughtcrime.securesms.util.NumberUtil


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ProSettingsHomeScreen(
    viewModel: ProSettingsViewModel,
    onBack: () -> Unit,
) {
    val data by viewModel.uiState.collectAsState()
    val dialogsState by viewModel.dialogState.collectAsState()

    ProSettingsHome(
        data = data,
        dialogsState = dialogsState,
        sendCommand = viewModel::onCommand,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ProSettingsHome(
    data: ProSettingsViewModel.UIState,
    dialogsState: ProSettingsViewModel.DialogsState,
    sendCommand: (ProSettingsViewModel.Commands) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            BackAppBar(
                title = "",
                backgroundColor = Color.Transparent,
                onBack = onBack,
            )
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
    ) { paddings ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddings.calculateTopPadding() - LocalDimensions.current.appBarHeight)
                .consumeWindowInsets(paddings)
                .padding(
                    horizontal = LocalDimensions.current.spacing,
                )
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = CenterHorizontally
        ) {
            Spacer(Modifier.height(46.dp))

            SessionProSettingsHeader(
                disabled = data.proStatus is ProAccountStatus.Expired,
            )

            // Pro Stats
            if(data.proStatus is ProAccountStatus.Pro){
                Spacer(Modifier.height(LocalDimensions.current.spacing))
                ProStats(
                    data = data.proStats,
                    sendCommand = sendCommand,
                )
            }

            // Pro account settings
            if(data.proStatus is ProAccountStatus.Pro){
                Spacer(Modifier.height(LocalDimensions.current.smallSpacing))
                ProSettings(
                    data = data.proStatus,
                    sendCommand = sendCommand,
                )
            }

            // Manage Pro - Expired
            if(data.proStatus is ProAccountStatus.Expired){
                Spacer(Modifier.height(LocalDimensions.current.smallSpacing))
                ProManage(
                    data = data.proStatus,
                    sendCommand = sendCommand,
                )
            }

            // Features
            Spacer(Modifier.height(LocalDimensions.current.spacing))
            ProFeatures(
                data = data.proStatus,
                sendCommand = sendCommand,
            )

            // Manage Pro - Pro
            if(data.proStatus is ProAccountStatus.Pro){
                Spacer(Modifier.height(LocalDimensions.current.smallSpacing))
                ProManage(
                    data = data.proStatus,
                    sendCommand = sendCommand,
                )
            }

            // Help
            Spacer(Modifier.height(LocalDimensions.current.spacing))
            CategoryCell(
                title = stringResource(R.string.sessionHelp),
            ) {
                val iconColor = if(data.proStatus is ProAccountStatus.Expired) LocalColors.current.text
                else LocalColors.current.accentText

                // Cell content
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconActionRowItem(
                        title = annotatedStringResource(ProStatusManager.TEMP_LABEL_FAQ),
                        subtitle = annotatedStringResource(ProStatusManager.TEMP_LABEL_FAQ_DESCR),
                        icon = R.drawable.ic_square_arrow_up_right,
                        iconSize = LocalDimensions.current.iconMedium,
                        iconColor = iconColor,
                        qaTag = R.string.qa_pro_settings_action_faq,
                        onClick = {
                            //todo PRO implement
                        }
                    )
                    Divider()
                    IconActionRowItem(
                        title = annotatedStringResource(ProStatusManager.TEMP_LABEL_SUPPORT),
                        subtitle = annotatedStringResource(ProStatusManager.TEMP_LABEL_SUPPORT_DESCR),
                        icon = R.drawable.ic_square_arrow_up_right,
                        iconSize = LocalDimensions.current.iconMedium,
                        iconColor = iconColor,
                        qaTag = R.string.qa_pro_settings_action_support,
                        onClick = {
                            //todo PRO implement
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(LocalDimensions.current.spacing))
            Spacer(modifier = Modifier.height(paddings.calculateBottomPadding()))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProStats(
    modifier: Modifier = Modifier,
    data: ProSettingsViewModel.ProStats,
    sendCommand: (ProSettingsViewModel.Commands) -> Unit,
){
    CategoryCell(
        modifier = modifier,
        dropShadow = LocalColors.current.isLight,
        title = ProStatusManager.TEMP_LABEL_PRO_STATS,
        titleIcon = {
            val tooltipState = rememberTooltipState(isPersistent = true)
            val scope = rememberCoroutineScope()

            SpeechBubbleTooltip(
                text = ProStatusManager.TEMP_LABEL_PRO_STATS_TT,
                tooltipState = tooltipState
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_circle_help),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(LocalColors.current.textSecondary),
                    modifier = Modifier
                        .size(LocalDimensions.current.iconXSmall)
                        .clickable {
                            scope.launch {
                                if (tooltipState.isVisible) tooltipState.dismiss() else tooltipState.show()
                            }
                        }
                        .qaTag("Tooltip")
                )
            }
        }
    ){
        // Cell content
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(LocalDimensions.current.smallSpacing),
            verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallSpacing)
        ){
            Row(
                horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.xsSpacing)
            ) {
                // groups updated
                ProStatItem(
                    modifier = Modifier.weight(1f),
                    title = pluralStringResource(
                        R.plurals.temp_pro_stats_groups,
                        data.groupsUpdated,
                        NumberUtil.getFormattedNumber(data.groupsUpdated.toLong())
                    ),
                    icon = R.drawable.ic_users_group_custom

                )

                // Pinned Convos
                ProStatItem(
                    modifier = Modifier.weight(1f),
                    title = pluralStringResource(
                        R.plurals.temp_pro_stats_pins,
                        data.pinnedConversations,
                        NumberUtil.getFormattedNumber(data.pinnedConversations.toLong())
                    ),
                    icon = R.drawable.ic_pin
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.xsSpacing)
            ) {
                // Pro Badges
                ProStatItem(
                    modifier = Modifier.weight(1f),
                    title = pluralStringResource(
                        R.plurals.temp_pro_stats_badges,
                        data.proBadges,
                        NumberUtil.getFormattedNumber(data.proBadges.toLong())
                    ),
                    icon = R.drawable.ic_rectangle_ellipsis

                )

                // Long Messages
                ProStatItem(
                    modifier = Modifier.weight(1f),
                    title = pluralStringResource(
                        R.plurals.temp_pro_stats_long,
                        data.longMessages,
                        NumberUtil.getFormattedNumber(data.longMessages.toLong())
                    ),
                    icon = R.drawable.ic_message_square
                )
            }
        }
    }
}

@Composable
fun ProStatItem(
    modifier: Modifier = Modifier,
    title: String,
    @DrawableRes icon: Int
){
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallSpacing)
    ){
        Image(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(LocalDimensions.current.iconRowItem),
            colorFilter = ColorFilter.tint(LocalColors.current.accent)
        )

        Text(
            text = title,
            style = LocalType.current.h9,
            color = LocalColors.current.text
        )
    }
}

@Composable
fun ProSettings(
    modifier: Modifier = Modifier,
    data: ProAccountStatus.Pro,
    sendCommand: (ProSettingsViewModel.Commands) -> Unit,
){
    CategoryCell(
        modifier = modifier,
        title = ProStatusManager.TEMP_LABEL_SETTINGS,
    ) {
        // Cell content
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconActionRowItem(
                title = annotatedStringResource(ProStatusManager.TEMP_LABEL_UPDATE_PLAN),
                subtitle = annotatedStringResource(data.infoLabel),
                icon = R.drawable.ic_chevron_right,
                qaTag = R.string.qa_pro_settings_action_update_plan,
                onClick = { sendCommand(ProSettingsViewModel.Commands.ShowPlanUpdate) }
            )
            Divider()
            SwitchActionRowItem(
                title = annotatedStringResource(ProStatusManager.TEMP_LABEL_PRO_BADGE),
                subtitle = annotatedStringResource(ProStatusManager.TEMP_LABEL_SHOW_BADGE),
                checked = data.showProBadge,
                qaTag = R.string.qa_pro_settings_action_show_badge,
                onCheckedChange = { sendCommand(ProSettingsViewModel.Commands.SetShowProBadge(it)) }
            )
        }
    }
}

@Composable
fun ProFeatures(
    modifier: Modifier = Modifier,
    data: ProAccountStatus,
    sendCommand: (ProSettingsViewModel.Commands) -> Unit,
) {
    //todo PRO add real features once we have the strings
    //todo PRO handle color based on status

    CategoryCell(
        modifier = modifier,
        title = ProStatusManager.TEMP_LABEL_FEATURES,
    ) {
        // Cell content
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(LocalDimensions.current.smallSpacing),
        ) {
            ProFeatureItem(
                title = "Testing",
                subtitle = annotatedStringResource("Subtitle"),
                icon = R.drawable.ic_chevron_right,
                iconGradientStart = LocalColors.current.accent,
                iconGradientEnd = LocalColors.current.danger,
                expired = data is ProAccountStatus.Expired
            )
        }
    }
}

@Composable
private fun ProFeatureItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: AnnotatedString,
    @DrawableRes icon: Int,
    iconGradientStart: Color,
    iconGradientEnd: Color,
    expired: Boolean
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.background(
                brush = Brush.linearGradient(
                    colors = if(expired) listOf(LocalColors.current.disabled, LocalColors.current.disabled)
                            else listOf(iconGradientStart, iconGradientEnd),
                    start = Offset(0f, 0f),        // Top-left corner
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)  // Bottom-right corner
                ),
                shape = MaterialTheme.shapes.extraSmall
            ),
            contentAlignment = Alignment.Center
        ){
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier
                    .padding(LocalDimensions.current.xsSpacing)
                    .size(LocalDimensions.current.iconMedium),
                colorFilter = ColorFilter.tint(Color.Black)
            )
        }

        Column {
            Text(
                text = title,
                style = LocalType.current.h9,
                color = LocalColors.current.text
            )
            Text(
                text = subtitle,
                style = LocalType.current.small,
                color = LocalColors.current.textSecondary
            )
        }
    }
}

@Composable
fun ProManage(
    modifier: Modifier = Modifier,
    data: ProAccountStatus,
    sendCommand: (ProSettingsViewModel.Commands) -> Unit,
){
    CategoryCell(
        modifier = modifier,
        title = ProStatusManager.TEMP_LABEL_MANAGE,
    ) {
        // Cell content
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            when(data){
                is ProAccountStatus.Pro.AutoRenewing -> {
                    IconActionRowItem(
                        title = annotatedStringResource(ProStatusManager.TEMP_LABEL_CANCEL),
                        titleColor = LocalColors.current.danger,
                        icon = R.drawable.ic_circle_x_custom,
                        iconColor = LocalColors.current.danger,
                        qaTag = R.string.qa_pro_settings_action_cancel_plan,
                        onClick = {
                            //todo PRO implement
                        }
                    )
                    Divider()
                    IconActionRowItem(
                        title = annotatedStringResource(ProStatusManager.TEMP_LABEL_REFUND),
                        titleColor = LocalColors.current.danger,
                        icon = R.drawable.ic_circle_warning_custom,
                        iconColor = LocalColors.current.danger,
                        qaTag = R.string.qa_pro_settings_action_request_refund,
                        onClick = {
                            //todo PRO implement
                        }
                    )
                }

                is ProAccountStatus.Pro.Expiring -> {
                    IconActionRowItem(
                        title = annotatedStringResource(ProStatusManager.TEMP_LABEL_CANCEL),
                        titleColor = LocalColors.current.danger,
                        icon = R.drawable.ic_circle_x_custom,
                        iconColor = LocalColors.current.danger,
                        qaTag = R.string.qa_pro_settings_action_cancel_plan,
                        onClick = {
                            //todo PRO implement
                        }
                    )
                }

                is ProAccountStatus.Expired -> {
                    IconActionRowItem(
                        title = annotatedStringResource(ProStatusManager.TEMP_LABEL_RENEW),
                        titleColor = LocalColors.current.accentText,
                        icon = R.drawable.ic_circle_plus,
                        iconColor = LocalColors.current.accentText,
                        qaTag = R.string.qa_pro_settings_action_cancel_plan,
                        onClick = {
                            //todo PRO implement
                        }
                    )
                    Divider()
                    IconActionRowItem(
                        title = annotatedStringResource(ProStatusManager.TEMP_LABEL_RECOVER),
                        icon = R.drawable.ic_refresh_cw,
                        qaTag = R.string.qa_pro_settings_action_request_refund,
                        onClick = {
                            //todo PRO implement
                        }
                    )
                }

                is ProAccountStatus.None -> {}
            }
        }
    }
}

@Preview
@Composable
fun PreviewProSettingsPro(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        ProSettingsHome(
            data = ProSettingsViewModel.UIState(
//                proStatus = ProAccountStatus.Pro.AutoRenewing(
//                    showProBadge = true,
//                    infoLabel = Phrase.from(LocalContext.current, R.string.proAutoRenew)
//                        .put(RELATIVE_TIME_KEY, "15 days")
//                        .format()
//                ),
                proStatus = ProAccountStatus.Expired,
            ),
            dialogsState = ProSettingsViewModel.DialogsState(),
            sendCommand = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
fun PreviewProSettingsNonPro(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        ProSettingsHome(
            data = ProSettingsViewModel.UIState(
                proStatus = ProAccountStatus.None,
            ),
            dialogsState = ProSettingsViewModel.DialogsState(),
            sendCommand = {},
            onBack = {},
        )
    }
}