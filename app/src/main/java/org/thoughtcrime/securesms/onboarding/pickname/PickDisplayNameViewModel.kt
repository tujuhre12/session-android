package org.thoughtcrime.securesms.onboarding.pickname

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import network.loki.messenger.R
import org.session.libsession.utilities.SSKEnvironment.ProfileManagerProtocol.Companion.NAME_PADDED_LENGTH
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.dependencies.ConfigFactory

internal class PickDisplayNameViewModel(
    private val loadFailed: Boolean,
    private val prefs: TextSecurePreferences,
    private val configFactory: ConfigFactory
): ViewModel() {
    private val _states = MutableStateFlow(if (loadFailed) pickNewNameState() else State())
    val states = _states.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    fun onContinue() {
        _states.update { it.copy(displayName = it.displayName.trim()) }

        val displayName = _states.value.displayName

        when {
            displayName.isEmpty() -> { _states.update { it.copy(isTextErrorColor = true, error = R.string.displayNameErrorDescription) } }
            displayName.toByteArray().size > NAME_PADDED_LENGTH -> { _states.update { it.copy(isTextErrorColor = true, error = R.string.displayNameErrorDescriptionShorter) } }
            else -> {
                // success - clear the error as we can still see it during the transition to the
                // next screen.
                _states.update { it.copy(isTextErrorColor = false, error = null) }

                when {
                    loadFailed -> {
                        prefs.setProfileName(displayName)
                        configFactory.user?.setName(displayName)

                        _events.tryEmit(Event.LoadAccountComplete)
                    }
                    else -> _events.tryEmit(Event.CreateAccount(displayName))
                }
            }
        }
    }

    fun onChange(value: String) {
        _states.update { state ->
            state.copy(
                displayName = value,
                isTextErrorColor = false
            )
        }
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(loadFailed: Boolean): Factory
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @AssistedInject constructor(
        @Assisted private val loadFailed: Boolean,
        private val prefs: TextSecurePreferences,
        private val configFactory: ConfigFactory
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PickDisplayNameViewModel(loadFailed, prefs, configFactory) as T
        }
    }
}

data class State(
    @StringRes val title: Int = R.string.displayNamePick,
    @StringRes val description: Int = R.string.displayNameDescription,
    val isTextErrorColor: Boolean = false,
    @StringRes val error: Int? = null,
    val displayName: String = ""
)

fun pickNewNameState() = State(
    title = R.string.displayNameNew,
    description = R.string.displayNameErrorNew
)

sealed interface Event {
    class CreateAccount(val profileName: String): Event
    object LoadAccountComplete: Event
}
