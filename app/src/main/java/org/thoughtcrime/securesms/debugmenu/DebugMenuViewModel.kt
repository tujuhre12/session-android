package org.thoughtcrime.securesms.debugmenu

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.session.libsession.utilities.Environment
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import javax.inject.Inject

@HiltViewModel
class DebugMenuViewModel @Inject constructor(
    private val application: Application,
    private val textSecurePreferences: TextSecurePreferences
) : ViewModel() {
    private val TAG = "DebugMenu"

    private val _uiState = MutableStateFlow(
        UIState(
            currentEnvironment = textSecurePreferences.getEnvironment().label,
            environments = Environment.entries.map { it.label }
        )
    )
    val uiState: StateFlow<UIState>
        get() = _uiState

    fun onCommand(command: Commands) {
        when (command) {
            is Commands.ChangeEnvironment -> changeEnvironment(command.environment)
        }
    }

    private fun changeEnvironment(environment: String) {
        if(environment == _uiState.value.currentEnvironment) return
        val env = Environment.entries.firstOrNull { it.label == environment } ?: return

        // show a loading state

        // clear remote and local data, then restart the app
        viewModelScope.launch {
            val deletionResultMap: Map<String, Boolean>? = try {
                val openGroups =
                    DatabaseComponent.get(application).lokiThreadDatabase().getAllOpenGroups()
                openGroups.map { it.value.server }.toSet().forEach { server ->
                    OpenGroupApi.deleteAllInboxMessages(server).get()
                }
                SnodeAPI.deleteAllMessages().get()
            } catch (e: Exception) {
                Log.e(
                    TAG, "Failed to delete network message from debug menu", e
                )
                null
            }

            // If the network data deletion was successful proceed to delete the local data as well.
            if (deletionResultMap?.values?.all { it } == true) {
                // save the environment
                textSecurePreferences.setEnvironment(env)

                // clear local data and restart
                ApplicationContext.getInstance(application).clearAllData()
            } else { // the remote deletion failed, show an error

            }
        }
    }

    data class UIState(
        val currentEnvironment: String,
        val environments: List<String>
    )

    sealed class Commands {
        data class ChangeEnvironment(val environment: String) : Commands()
    }
}