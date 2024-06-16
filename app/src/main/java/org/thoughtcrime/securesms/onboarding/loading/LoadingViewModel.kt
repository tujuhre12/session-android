package org.thoughtcrime.securesms.onboarding.loading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import org.session.libsession.utilities.TextSecurePreferences
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class State(val duration: Duration)

private val ANIMATE_TO_DONE_TIME = 500.milliseconds
private val IDLE_DONE_TIME = 1.seconds
private val TIMEOUT_TIME = 15.seconds

@OptIn(FlowPreview::class)
@HiltViewModel
class LoadingViewModel @Inject constructor(
    val prefs: TextSecurePreferences
): ViewModel() {

    private val state = MutableStateFlow(State(TIMEOUT_TIME))
    val stateFlow = state.asStateFlow()

    private val event = Channel<Event>()
    val eventFlow = event.receiveAsFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                TextSecurePreferences.events
                    .filter { it == TextSecurePreferences.CONFIGURATION_SYNCED }
                    .timeout(TIMEOUT_TIME)
                    .onStart { emit(TextSecurePreferences.CONFIGURATION_SYNCED) }
                    .collectLatest {
                        if (prefs.getConfigurationMessageSynced()) onSuccess()
                    }
            } catch (e: Exception) {
                onFail()
            }
        }
    }

    private suspend fun onSuccess() {
        state.value = State(ANIMATE_TO_DONE_TIME)
        delay(IDLE_DONE_TIME)
        event.send(Event.SUCCESS)
    }

    private fun onFail() {
        event.trySend(Event.TIMEOUT)
    }
}

sealed interface Event {
    object SUCCESS: Event
    object TIMEOUT: Event
}
