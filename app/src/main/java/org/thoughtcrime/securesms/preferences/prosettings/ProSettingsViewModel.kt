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
                title = if(planStatus is ProAccountStatus.Expired) "Upgrade" else "Update",
                enableButton = false,
                plans = listOf(
                    ProPlan(
                        title = "Plan 1",
                        subtitle = "Subtitle",
                        selected = true,
                        currentPlan = true,
                        badges = listOf(
                            ProPlanBadge("Current Plan"),
                            ProPlanBadge("20% Off", "This is a tooltip"),
                        ),
                    ),
                    ProPlan(
                        title = "Plan 2",
                        subtitle = "Subtitle",
                        selected = false,
                        currentPlan = false,
                        badges = listOf(
                            ProPlanBadge("Current Plan"),
                            ProPlanBadge("20% Off", "This is a tooltip"),
                        ),
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
        }
    }

    private fun navigateTo(destination: ProSettingsDestination){
        viewModelScope.launch {
            navigator.navigate(destination)
        }
    }

    sealed interface Commands {
        data class ShowOpenUrlDialog(val url: String?) : Commands

        object ShowPlanUpdate: Commands
        data class SetShowProBadge(val show: Boolean): Commands
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
        val title: String = "",
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
    )
}
