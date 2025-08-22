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

    private val _uiState: MutableStateFlow<UIState> = MutableStateFlow(
        UIState(
            //todo PRO need to properly calculate this
            proStatus = if(proStatusManager.isCurrentUserPro())
//                ProAccountStatus.Expired
                ProAccountStatus.Pro.AutoRenewing(
                showProBadge = true,
                infoLabel = Phrase.from(context, R.string.proAutoRenew)
                    .put(RELATIVE_TIME_KEY, "15 days")
                    .format()
            )
            else ProAccountStatus.None
        )
    )
    val uiState: StateFlow<UIState> = _uiState

    private val _dialogState: MutableStateFlow<DialogsState> = MutableStateFlow(DialogsState())
    val dialogState: StateFlow<DialogsState> = _dialogState

    init {

    }


    fun onCommand(command: Commands) {
        when (command) {
            is Commands.ShowOpenUrlDialog -> {
                _dialogState.update {
                    it.copy(openLinkDialogUrl = command.url)
                }
            }

            Commands.ShowPlanUpdate -> {
                //todo PRO implement
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

    data class UIState(
        val proStatus: ProAccountStatus,
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

    data class ProSettings(
        val showProBadge: Boolean = false
    )

    data class DialogsState(
        val openLinkDialogUrl: String? = null,
    )
}
