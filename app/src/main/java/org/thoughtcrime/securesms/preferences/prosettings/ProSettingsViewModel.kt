package org.thoughtcrime.securesms.preferences.prosettings

import android.content.Context
import androidx.compose.ui.platform.LocalContext
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
import org.session.libsession.utilities.StringSubstitutionConstants.RELATIVE_TIME_KEY
import org.thoughtcrime.securesms.pro.ProStatusManager
import org.thoughtcrime.securesms.ui.UINavigator
import javax.inject.Inject


@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val navigator: UINavigator<ProSettingsDestination>,
    private val proStatusManager: ProStatusManager,
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
        val planStatus = //                ProAccountStatus.Expired
            ProAccountStatus.Pro.AutoRenewing(
                showProBadge = true,
                infoLabel = Phrase.from(context, R.string.proAutoRenew)
                    .put(RELATIVE_TIME_KEY, "15 days")
                    .format()
            )

        _proSettingsUIState.update {
            ProSettingsUIState(
                proStatus = if(proStatusManager.isCurrentUserPro())
                    planStatus
                else ProAccountStatus.None
            )
        }

        _proPlanUIState.update {
            ProPlanUIState(
                title = if(planStatus is ProAccountStatus.Expired)
                    Phrase.from(context.getText(R.string.proPlanRenewStart))
                        .put(APP_PRO_KEY, NonTranslatableStringConstants.APP_PRO)
                        .put(APP_PRO_KEY, NonTranslatableStringConstants.APP_PRO)
                        .format()
                    else Phrase.from(context.getText(R.string.proPlanActivatedAuto))
                    .put(APP_PRO_KEY, NonTranslatableStringConstants.APP_PRO)
                    .put(CURRENT_PLAN_KEY, "3 months") //todo PRO implement properly
                    .put(DATE_KEY, "May 21st, 2025") //todo PRO implement properly
                    .put(PRO_KEY, NonTranslatableStringConstants.PRO)
                    .format(),
                enableButton = false,
                //todo PRO calculate all plans properly
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
                        enableButton = !command.plan.currentPlan
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
        }
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

        object ShowPlanUpdate: Commands
        data class SetShowProBadge(val show: Boolean): Commands

        data class SelectProPlan(val plan: ProPlan): Commands
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

    sealed interface ProAccountStatus{
        object None: ProAccountStatus

        sealed interface Pro: ProAccountStatus{
            val showProBadge: Boolean
            val infoLabel: CharSequence

            data class AutoRenewing(
                override val showProBadge: Boolean,
                override val infoLabel: CharSequence
            ): Pro

            data class Expiring(
                override val showProBadge: Boolean,
                override val infoLabel: CharSequence
            ): Pro
        }

        data object Expired: ProAccountStatus
    }

    data class ProPlanUIState(
        val plans: List<ProPlan> = emptyList(),
        val enableButton: Boolean = false,
        val title: CharSequence = "",
    )

    data class ProPlan(
        val title: String,
        val subtitle: String,
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
    )
}
