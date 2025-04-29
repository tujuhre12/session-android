package org.thoughtcrime.securesms.conversation.v2.settings

import androidx.navigation.NavOptionsBuilder
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Singleton
import org.thoughtcrime.securesms.conversation.v2.settings.ConversationSettingsDestination.*
import javax.inject.Inject

@ActivityRetainedScoped
class ConversationSettingsNavigator @Inject constructor(){
    val startDestination: ConversationSettingsDestination = RouteConversationSettings

    private val _navigationActions = Channel<NavigationAction>()
    val navigationActions = _navigationActions.receiveAsFlow()

    suspend fun navigate(
        destination: ConversationSettingsDestination,
        navOptions: NavOptionsBuilder.() -> Unit = {}
    ) {
        _navigationActions.send(NavigationAction.Navigate(
            destination = destination,
            navOptions = navOptions
        ))
    }

    suspend fun navigateUp() {
        _navigationActions.send(NavigationAction.NavigateUp)
    }
}

sealed interface NavigationAction {
    data class Navigate(
        val destination: ConversationSettingsDestination,
        val navOptions: NavOptionsBuilder.() -> Unit = {}
    ): NavigationAction

    data object NavigateUp: NavigationAction
}