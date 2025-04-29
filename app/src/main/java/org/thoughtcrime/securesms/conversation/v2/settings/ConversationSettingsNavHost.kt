package org.thoughtcrime.securesms.conversation.v2.settings

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import network.loki.messenger.BuildConfig
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.utilities.Address
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.conversation.disappearingmessages.DisappearingMessagesViewModel
import org.thoughtcrime.securesms.conversation.disappearingmessages.ui.DisappearingMessagesScreen
import org.thoughtcrime.securesms.conversation.v2.settings.ConversationSettingsDestination.*
import org.thoughtcrime.securesms.groups.EditGroupViewModel
import org.thoughtcrime.securesms.groups.GroupMembersViewModel
import org.thoughtcrime.securesms.groups.InviteContactsViewModel
import org.thoughtcrime.securesms.groups.SelectContactsViewModel
import org.thoughtcrime.securesms.groups.compose.EditGroupScreen
import org.thoughtcrime.securesms.groups.compose.GroupMembersScreen
import org.thoughtcrime.securesms.groups.compose.InviteContactsScreen
import org.thoughtcrime.securesms.media.MediaOverviewScreen
import org.thoughtcrime.securesms.media.MediaOverviewViewModel
import org.thoughtcrime.securesms.ui.ObserveAsEvents
import org.thoughtcrime.securesms.ui.horizontalSlideComposable

// Destinations
sealed interface ConversationSettingsDestination {
    @Serializable
    data object RouteConversationSettings: ConversationSettingsDestination

    @Serializable
    data class RouteGroupMembers(
        val groupId: String
    ): ConversationSettingsDestination

    @Serializable
    data class RouteManageMembers(
        val groupId: String
    ): ConversationSettingsDestination

    @Serializable
    data class RouteInviteContacts(
        val groupId: String,
        val excludingAccountIDs: List<String>
    ): ConversationSettingsDestination

    @Serializable
    data object RouteDisappearingMessages: ConversationSettingsDestination

    @Serializable
    data object RouteAllMedia: ConversationSettingsDestination
}

@Composable
fun ConversationSettingsNavHost(
    threadId: Long,
    threadAddress: Address?,
    navigator: ConversationSettingsNavigator,
    onBack: () -> Unit
){
    val navController = rememberNavController()

    ObserveAsEvents(flow = navigator.navigationActions) { action ->
        when(action) {
            is NavigationAction.Navigate -> navController.navigate(
                action.destination
            ) {
                action.navOptions(this)
            }
            NavigationAction.NavigateUp -> navController.navigateUp()
        }
    }

    NavHost(navController = navController, startDestination = navigator.startDestination) {
        // Conversation Settings
        horizontalSlideComposable<RouteConversationSettings> {
            val viewModel = hiltViewModel<ConversationSettingsViewModel, ConversationSettingsViewModel.Factory> { factory ->
                factory.create(threadId)
            }

            ConversationSettingsScreen(
                viewModel = viewModel,
                onBack = onBack,
            )
        }

        // Group Members
        horizontalSlideComposable<RouteGroupMembers> { backStackEntry ->
            val data: RouteGroupMembers = backStackEntry.toRoute()

            val viewModel = hiltViewModel<GroupMembersViewModel, GroupMembersViewModel.Factory> { factory ->
                factory.create(AccountId(data.groupId))
            }

            GroupMembersScreen (
                viewModel = viewModel,
                onBack = navController::popBackStack,
            )
        }
        // Edit Group
        horizontalSlideComposable<RouteManageMembers> { backStackEntry ->
            val data: RouteManageMembers = backStackEntry.toRoute()

            val viewModel = hiltViewModel<EditGroupViewModel, EditGroupViewModel.Factory> { factory ->
                factory.create(AccountId(data.groupId))
            }
            EditGroupScreen(
                viewModel = viewModel,
                navigateToInviteContact = {
                    navController.navigate(RouteInviteContacts(
                        groupId = data.groupId,
                        excludingAccountIDs = viewModel.excludingAccountIDsFromContactSelection.toList()
                    ))
                },
                onBack = navController::popBackStack,
            )
        }

        // Invite Contacts
        horizontalSlideComposable<RouteInviteContacts> { backStackEntry ->
            val data: RouteInviteContacts = backStackEntry.toRoute()

            val viewModel = hiltViewModel<InviteContactsViewModel, InviteContactsViewModel.Factory> { factory ->
                factory.create(
                    groupId = AccountId(data.groupId),
                    excludingAccountIDs = data.excludingAccountIDs.map(::AccountId).toSet()
                )
            }

            InviteContactsScreen(
                viewModel = viewModel,
                onBack = navController::popBackStack,
            )
        }

        // Disappearing Messages
        horizontalSlideComposable<RouteDisappearingMessages> {
            val viewModel: DisappearingMessagesViewModel =
                hiltViewModel<DisappearingMessagesViewModel, DisappearingMessagesViewModel.Factory> { factory ->
                    factory.create(
                        threadId = threadId,
                        isNewConfigEnabled = ExpirationConfiguration.isNewConfigEnabled,
                        showDebugOptions = BuildConfig.DEBUG
                    )
                }

            DisappearingMessagesScreen(
                viewModel = viewModel,
                onBack = navController::popBackStack,
            )
        }

        // All Media
        horizontalSlideComposable<RouteAllMedia> {
            if(threadAddress == null) {
                navController.popBackStack()
                return@horizontalSlideComposable
            }

            val viewModel = hiltViewModel<MediaOverviewViewModel, MediaOverviewViewModel.Factory> { factory ->
                factory.create(threadAddress)
            }

            MediaOverviewScreen(
                viewModel = viewModel,
                onClose = navController::popBackStack,
            )
        }
    }
}