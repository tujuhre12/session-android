package org.thoughtcrime.securesms.onboarding.name

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.SSKEnvironment.ProfileManagerProtocol.Companion.NAME_PADDED_LENGTH
import org.session.libsession.utilities.TextSecurePreferences
import javax.inject.Inject

@HiltViewModel
class DisplayNameViewModel @Inject constructor(
    private val prefs: TextSecurePreferences
): ViewModel() {

    private val state = MutableStateFlow(State())
    val stateFlow = state.asStateFlow()

    private val event = Channel<Event>()
    val eventFlow = event.receiveAsFlow()

    fun onContinue() {
        state.update { it.copy(displayName = it.displayName.trim()) }

        val displayName = state.value.displayName

        when {
            displayName.isEmpty() -> { state.update { it.copy(error = R.string.activity_display_name_display_name_missing_error) } }
            displayName.length > NAME_PADDED_LENGTH -> { state.update { it.copy(error = R.string.activity_display_name_display_name_too_long_error) } }
            else -> {
                prefs.setProfileName(displayName)
                viewModelScope.launch { event.send(Event.DONE) }
            }
        }
    }

    fun onChange(value: String) {
        state.update {
            it.copy(
                displayName = value,
                error = value.takeIf { it.length > NAME_PADDED_LENGTH }?.let { R.string.activity_display_name_display_name_too_long_error }
            )
        }
    }
}

data class State(
    @StringRes val title: Int = R.string.activity_display_name_title_2,
    @StringRes val description: Int = R.string.activity_display_name_explanation,
    @StringRes val error: Int? = null,
    val displayName: String = ""
)

sealed interface Event {
    object DONE: Event
}