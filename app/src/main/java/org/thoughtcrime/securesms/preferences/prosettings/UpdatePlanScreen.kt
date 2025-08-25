package org.thoughtcrime.securesms.preferences.prosettings

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.NonTranslatableStringConstants
import org.session.libsession.utilities.StringSubstitutionConstants
import org.session.libsession.utilities.StringSubstitutionConstants.APP_PRO_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.MONTHLY_PRICE_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.PRICE_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.RELATIVE_TIME_KEY
import org.thoughtcrime.securesms.preferences.prosettings.ProSettingsViewModel.ProAccountStatus
import org.thoughtcrime.securesms.preferences.prosettings.ProSettingsViewModel.ProPlan
import org.thoughtcrime.securesms.preferences.prosettings.ProSettingsViewModel.ProPlanBadge
import org.thoughtcrime.securesms.ui.components.AccentFillButtonRect
import org.thoughtcrime.securesms.ui.components.RadioButtonIndicator
import org.thoughtcrime.securesms.ui.components.annotatedStringResource
import org.thoughtcrime.securesms.ui.components.inlineContentMap
import org.thoughtcrime.securesms.ui.components.radioButtonColors
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors
import org.thoughtcrime.securesms.ui.theme.bold


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun UpdatePlanScreen(
    viewModel: ProSettingsViewModel,
    onBack: () -> Unit,
) {
    val proData by viewModel.proSettingsUIState.collectAsState()
    val planData by viewModel.proPlanUIState.collectAsState()

    UpdatePlan(
        proData = proData,
        planData = planData,
        sendCommand = viewModel::onCommand,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun UpdatePlan(
    proData: ProSettingsViewModel.ProSettingsUIState,
    planData: ProSettingsViewModel.ProPlanUIState,
    sendCommand: (ProSettingsViewModel.Commands) -> Unit,
    onBack: () -> Unit,
) {
    BaseProSettingsScreen(
        data = proData,
        onBack = onBack,
    ) {
        // Keeps track of the badge height dynamically so we can adjust the padding accordingly
        // This is better than a static badge height since users can change their font settings
        // and display sizes for better visibility and the padding should work accordingly
        var badgeHeight by remember { mutableStateOf(0.dp) }

        Spacer(Modifier.height(LocalDimensions.current.spacing))

        Text(
            modifier = Modifier.fillMaxWidth(),
            text = annotatedStringResource(planData.title),
            textAlign = TextAlign.Center,
            style = LocalType.current.base,
            color = LocalColors.current.text,

        )

        Spacer(Modifier.height(LocalDimensions.current.smallSpacing))

        // SUBSCRIPTIONS
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.smallSpacing)
        ) {
            planData.plans.forEach {
                PlanItem(
                    proPlan = it,
                    badgePadding = badgeHeight / 2,
                    onBadgeLaidOut = { height -> badgeHeight = max(badgeHeight, height) }
                )
            }
        }

        Spacer(Modifier.height(LocalDimensions.current.smallSpacing))

        AccentFillButtonRect(
            modifier = Modifier.fillMaxWidth()
                .widthIn(max = LocalDimensions.current.maxContentWidth),
            text = stringResource(R.string.updatePlan),
            onClick = {}
        )

        Spacer(Modifier.height(LocalDimensions.current.smallSpacing))

        Text(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = LocalDimensions.current.spacing),
            text = annotatedStringResource(
                Phrase.from(LocalContext.current.getText(R.string.proTosPrivacy))
                    .put(APP_PRO_KEY, NonTranslatableStringConstants.APP_PRO)
                    .format()
            ),
            textAlign = TextAlign.Center,
            style = LocalType.current.small,
            color = LocalColors.current.text,
            inlineContent = inlineContentMap(
                textSize = LocalType.current.small.fontSize,
                imageColor = LocalColors.current.text
            ),
        )
    }
}

@Composable
private fun PlanItem(
    proPlan: ProSettingsViewModel.ProPlan,
    badgePadding: Dp,
    modifier: Modifier= Modifier,
    onBadgeLaidOut: (Dp) -> Unit
){
    val density = LocalDensity.current

    // outer box
    Box(modifier = modifier.fillMaxWidth()) {
        // card content
        Box(
            modifier = modifier.padding(top = maxOf(badgePadding, 9.dp)) // 9.dp is a simple fallback to match default styling
                .background(
                    color = LocalColors.current.backgroundSecondary,
                    shape = MaterialTheme.shapes.small
                )
                .border(
                    width = 1.dp,
                    color = if(proPlan.selected) LocalColors.current.accent else LocalColors.current.borders,
                    shape = MaterialTheme.shapes.small
                )
                .clip(MaterialTheme.shapes.small)

        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(LocalDimensions.current.smallSpacing)
                    .padding(top = if(proPlan.badges.isNotEmpty()) badgePadding/2 else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = proPlan.title,
                        style = LocalType.current.h7,
                    )

                    Text(
                        text = proPlan.subtitle,
                        style = LocalType.current.small.copy(color = LocalColors.current.textSecondary),
                    )
                }

                RadioButtonIndicator(
                    selected = proPlan.selected,
                    enabled = true,
                    colors = radioButtonColors(
                        unselectedBorder = LocalColors.current.borders,
                        selectedBorder = LocalColors.current.accent,
                    )
                )
            }
        }

        // badges
        if(proPlan.badges.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(start = LocalDimensions.current.smallSpacing)
                    .onGloballyPositioned { coordinates ->
                        onBadgeLaidOut(with(density) { coordinates.size.height.toDp() })
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                proPlan.badges.forEach {
                    PlanBadge(
                        modifier = Modifier.weight(1f, fill = false),
                        badge = it
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanBadge(
    badge: ProSettingsViewModel.ProPlanBadge,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = LocalColors.current.accent,
        shape = RoundedCornerShape(4.0.dp)
    ) {
        Text(
            modifier = Modifier.padding(
                horizontal = 6.dp,
                vertical = 2.dp
            ),
            text = badge.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LocalType.current.small.bold().copy(
                color = LocalColors.current.accentButtonFillText
            )
        )
    }
}

@Preview
@Composable
private fun PreviewUpdatePlanItems(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(LocalDimensions.current.spacing),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PlanItem(
                proPlan = ProSettingsViewModel.ProPlan(
                    title = "Plan 1",
                    subtitle = "Subtitle",
                    selected = true,
                    currentPlan = true,
                    badges = listOf(
                        ProSettingsViewModel.ProPlanBadge("Current Plan"),
                        ProSettingsViewModel.ProPlanBadge("20% Off", "This is a tooltip"),
                    ),
                ),
                badgePadding = 0.dp,
                onBadgeLaidOut = {}
            )

            PlanItem(
                proPlan = ProSettingsViewModel.ProPlan(
                    title = "Plan 2",
                    subtitle = "Subtitle",
                    selected = false,
                    currentPlan = false,
                    badges = listOf(
                        ProSettingsViewModel.ProPlanBadge("Current Plan"),
                        ProSettingsViewModel.ProPlanBadge("20% Off", "This is a tooltip"),
                    ),
                ),
                badgePadding = 0.dp,
                onBadgeLaidOut = {}
            )

            PlanItem(
                proPlan = ProSettingsViewModel.ProPlan(
                    title = "Plan 1 with a very long title boo foo bar hello there",
                    subtitle = "Subtitle that is also very long and is allowed to go onto another line",
                    selected = true,
                    currentPlan = true,
                    badges = listOf(
                        ProSettingsViewModel.ProPlanBadge("Current Plan"),
                        ProSettingsViewModel.ProPlanBadge(
                            "20% Off but that is very long so we can test how this renders to be safe",
                            "This is a tooltip"
                        ),
                    ),
                ),
                badgePadding = 0.dp,
                onBadgeLaidOut = {}
            )
        }
    }
}

@Preview
@Composable
private fun PreviewUpdatePlan(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        val context = LocalContext.current
        UpdatePlan(
            planData = ProSettingsViewModel.ProPlanUIState(
                title = "This is a title",
                enableButton = true,
                plans = listOf(
                    ProPlan(
                        title = Phrase.from(context.getText(R.string.proPriceTwelveMonths))
                            .put(MONTHLY_PRICE_KEY, "$3.99")
                            .format().toString(),
                        subtitle = Phrase.from(context.getText(R.string.proBilledAnnually))
                            .put(PRICE_KEY, "$47.99")
                            .format().toString(),
                        selected = false,
                        currentPlan = false,
                        badges = listOf(
                            ProPlanBadge("20% Off"),
                        ),
                    ),
                    ProPlan(
                        title = Phrase.from(context.getText(R.string.proPriceThreeMonths))
                            .put(MONTHLY_PRICE_KEY, "$4.99")
                            .format().toString(),
                        subtitle = Phrase.from(context.getText(R.string.proBilledQuarterly))
                            .put(PRICE_KEY, "$14.99")
                            .format().toString(),
                        selected = true,
                        currentPlan = true,
                        badges = listOf(
                            ProPlanBadge("Current Plan"),
                            ProPlanBadge("20% Off", "This is a tooltip"),
                        ),
                    ),
                    ProPlan(
                        title = Phrase.from(context.getText(R.string.proPriceOneMonth))
                            .put(MONTHLY_PRICE_KEY, "$5.99")
                            .format().toString(),
                        subtitle = Phrase.from(context.getText(R.string.proBilledMonthly))
                            .put(PRICE_KEY, "$5")
                            .format().toString(),
                        selected = false,
                        currentPlan = false,
                        badges = emptyList(),
                    ),
                )
            ),
            proData = ProSettingsViewModel.ProSettingsUIState(
                proStatus = ProAccountStatus.Pro.AutoRenewing(
                    showProBadge = true,
                    infoLabel = Phrase.from(LocalContext.current, R.string.proAutoRenew)
                        .put(RELATIVE_TIME_KEY, "15 days")
                        .format()
                ),
//                proStatus = ProAccountStatus.Expired,
            ),
            sendCommand = {},
            onBack = {},
        )
    }
}


