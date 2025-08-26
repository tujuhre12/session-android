package org.thoughtcrime.securesms.preferences.prosettings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.phrase.Phrase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.NonTranslatableStringConstants
import org.session.libsession.utilities.StringSubstitutionConstants.APP_PRO_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.CURRENT_PLAN_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.DATE_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.MONTHLY_PRICE_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.PRICE_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.PRO_KEY
import org.thoughtcrime.securesms.pro.ProAccountStatus
import org.thoughtcrime.securesms.pro.ProStatusManager
import org.thoughtcrime.securesms.pro.subscription.SubscriptionCoordinator
import org.thoughtcrime.securesms.pro.subscription.SubscriptionManager
import org.thoughtcrime.securesms.ui.SimpleDialogData
import org.thoughtcrime.securesms.ui.UINavigator
import javax.inject.Inject


@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val navigator: UINavigator<ProSettingsDestination>,
    private val proStatusManager: ProStatusManager,
    private val subscriptionCoordinator: SubscriptionCoordinator
) : ViewModel() {

    private val _proSettingsUIState: MutableStateFlow<ProSettingsUIState> = MutableStateFlow(ProSettingsUIState())
    val proSettingsUIState: StateFlow<ProSettingsUIState> = _proSettingsUIState

    private val _dialogState: MutableStateFlow<DialogsState> = MutableStateFlow(DialogsState())
    val dialogState: StateFlow<DialogsState> = _dialogState

    private val _proPlanUIState: MutableStateFlow<ProPlanUIState> = MutableStateFlow(ProPlanUIState())
    val proPlanUIState: StateFlow<ProPlanUIState> = _proPlanUIState

    init {
        generateState()
    }

    private fun generateState(){
        //todo PRO need to properly calculate this
        val planStatus = proStatusManager.getCurrentSubscriptionStatus()

        _proSettingsUIState.update {
            ProSettingsUIState(
                proStatus = if(proStatusManager.isCurrentUserPro())
                    planStatus
                else ProAccountStatus.None
            )
        }

        _proPlanUIState.update {
            // sort out the title and button label for the plan screen based on subscription status
            val (title, buttonLabel) = when(planStatus) {
                is ProAccountStatus.Expired ->
                    Phrase.from(context.getText(R.string.proPlanRenewStart))
                        .put(APP_PRO_KEY, NonTranslatableStringConstants.APP_PRO)
                        .put(APP_PRO_KEY, NonTranslatableStringConstants.APP_PRO)
                        .format() to
                            context.getString(R.string.renew)

                is ProAccountStatus.Pro.Expiring -> Phrase.from(context.getText(R.string.proPlanActivatedNotAuto))
                    .put(APP_PRO_KEY, NonTranslatableStringConstants.APP_PRO)
                    .put(DATE_KEY, "May 21st, 2025") //todo PRO implement properly
                    .format() to
                        context.getString(R.string.updatePlan)

                else -> Phrase.from(context.getText(R.string.proPlanActivatedAuto))
                    .put(APP_PRO_KEY, NonTranslatableStringConstants.APP_PRO)
                    .put(CURRENT_PLAN_KEY, "3 months") //todo PRO implement properly
                    .put(DATE_KEY, "May 21st, 2025") //todo PRO implement properly
                    .put(PRO_KEY, NonTranslatableStringConstants.PRO)
                    .format() to
                        context.getString(R.string.updatePlan)
            }

            ProPlanUIState(
                title = title,
                buttonLabel = buttonLabel,
                enableButton = planStatus is ProAccountStatus.Expired, // expired plan always have an enabled button
                //todo PRO calculate all plans properly
                //todo PRO add tooltip if the user's current plan is 3 or 12 months
                //todo PRO do not show a current plan badge when status is expired
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
                        type = SubscriptionManager.SubscriptionType.TWELVE_MONTHS,
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
                        type = SubscriptionManager.SubscriptionType.THREE_MONTHS,
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
                        type = SubscriptionManager.SubscriptionType.ONE_MONTH,
                        badges = emptyList(),
                    ),
                )
            )
        }
    }


    fun onCommand(command: Commands) {
        when (command) {
            is Commands.ShowOpenUrlDialog -> {
                _dialogState.update {
                    it.copy(openLinkDialogUrl = command.url)
                }
            }

            Commands.ShowPlanUpdate -> {
                navigateTo(ProSettingsDestination.UpdatePlan)
            }

            is Commands.SetShowProBadge -> {
                //todo PRO implement
            }

            is Commands.SelectProPlan -> {
                _proPlanUIState.update { data ->
                    data.copy(
                        plans = data.plans.map {
                            it.copy(selected = it == command.plan)
                        },
                        enableButton = _proSettingsUIState.value.proStatus is ProAccountStatus.Expired
                                || !command.plan.currentPlan
                        //todo PRO should the button always be enabled for EXPIRING state?
                    )
                }
            }

            Commands.ShowTCPolicyDialog -> {
                _dialogState.update {
                    it.copy(showTCPolicyDialog = true)
                }
            }

            Commands.HideTCPolicyDialog -> {
                _dialogState.update {
                    it.copy(showTCPolicyDialog = false)
                }
            }

            Commands.GetProPlan -> {
                // if we already have a current plan, ask for confirmation first
                if(_proSettingsUIState.value.proStatus is ProAccountStatus.Pro){
                    _dialogState.update {
                        it.copy(
                            showSimpleDialog = SimpleDialogData(
                                title = context.getString(R.string.updatePlan),
                                message = if(_proSettingsUIState.value.proStatus is ProAccountStatus.Pro.AutoRenewing)
                                    "TEMP AUTO RENEW"
                                else "TEMP EXPIRING", //todo PRO get real string
                                positiveText = context.getString(R.string.updatePlan),
                                negativeText = context.getString(R.string.cancel),
                                positiveStyleDanger = false,
                                onPositive = { getPlanFromProvider() },
                                onNegative = { onCommand(Commands.HideTCPolicyDialog) }
                            )
                        )
                    }
                }
                // otherwise go straight to the store
                else {
                    getPlanFromProvider()
                }
            }

            Commands.ConfirmProPlan -> {
                getPlanFromProvider()
            }

            Commands.HideSimpleDialog -> {
                _dialogState.update {
                    it.copy(showSimpleDialog = null)
                }
            }
        }
    }

    private fun getSelectedPlan(): ProPlan {
        return _proPlanUIState.value.plans.first { it.selected }
    }

    private fun getPlanFromProvider(){
        subscriptionCoordinator.getCurrentManager().purchasePlan(
            getSelectedPlan().type
        )
    }

    private fun navigateTo(destination: ProSettingsDestination){
        viewModelScope.launch {
            navigator.navigate(destination)
        }
    }

    sealed interface Commands {
        data class ShowOpenUrlDialog(val url: String?) : Commands
        data object ShowTCPolicyDialog: Commands
        data object HideTCPolicyDialog: Commands
        data object HideSimpleDialog : Commands

        object ShowPlanUpdate: Commands
        data class SetShowProBadge(val show: Boolean): Commands

        data class SelectProPlan(val plan: ProPlan): Commands
        data object GetProPlan: Commands
        data object ConfirmProPlan: Commands
    }

    data class ProSettingsUIState(
        val proStatus: ProAccountStatus = ProAccountStatus.None,
        val proStats: ProStats = ProStats()
    )

    data class ProStats(
        val groupsUpdated: Int = 0,
        val pinnedConversations: Int = 0,
        val proBadges: Int = 0,
        val longMessages: Int = 0
    )

    data class ProPlanUIState(
        val plans: List<ProPlan> = emptyList(),
        val enableButton: Boolean = false,
        val title: CharSequence = "",
        val buttonLabel: String = "",
    )

    data class ProPlan(
        val title: String,
        val subtitle: String,
        val type: SubscriptionManager.SubscriptionType,
        val currentPlan: Boolean,
        val selected: Boolean,
        val badges: List<ProPlanBadge>
    )

    data class ProPlanBadge(
        val title: String,
        val tooltip: String? = null
    )

    data class DialogsState(
        val openLinkDialogUrl: String? = null,
        val showTCPolicyDialog: Boolean = false,
        val showSimpleDialog: SimpleDialogData? = null,
    )
}
