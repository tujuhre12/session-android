package org.thoughtcrime.securesms.onboarding.messagenotifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.ApplicationContext

internal class MessageNotificationsViewModel(
    private val state: State,
    private val application: Application
): AndroidViewModel(application) {
    private val _uiStates = MutableStateFlow(UiState())
    val uiStates = _uiStates.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _uiStates.update { UiState(pushEnabled = enabled) }
    }

    /**
     * @return [true] if the back press was handled.
     */
    fun onBackPressed(): Boolean = when (state) {
        is State.CreateAccount -> false
        is State.LoadAccount -> {
            _uiStates.update { it.copy(showDialog = true) }

            true
        }
    }

    fun dismissDialog() {
        _uiStates.update { it.copy(showDialog = false) }
    }

    fun quit() {
        _uiStates.update { it.copy(clearData = true) }

        viewModelScope.launch(Dispatchers.IO) {
            ApplicationContext.getInstance(application).clearAllData()
        }
    }

    data class UiState(
        val pushEnabled: Boolean = true,
        val showDialog: Boolean = false,
        val clearData: Boolean = false
    ) {
        val pushDisabled get() = !pushEnabled
    }

    sealed interface State {
        class CreateAccount(val displayName: String): State
        object LoadAccount: State
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(profileName: String?): Factory
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @AssistedInject constructor(
        @Assisted private val profileName: String?,
        private val application: Application
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MessageNotificationsViewModel(
                state = profileName?.let(State::CreateAccount) ?: State.LoadAccount,
                application = application
            ) as T
        }
    }
}
