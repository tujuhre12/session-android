package org.thoughtcrime.securesms.conversation.v2.settings

import android.content.Intent
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

    suspend fun navigateToIntent(intent: Intent) {
        _navigationActions.send(NavigationAction.NavigateToIntent(intent))
    }
}

sealed interface NavigationAction {
    data class Navigate(
        val destination: ConversationSettingsDestination,
        val navOptions: NavOptionsBuilder.() -> Unit = {}
    ): NavigationAction

    data object NavigateUp: NavigationAction

    data class NavigateToIntent(
        val intent: Intent
    ): NavigationAction
}