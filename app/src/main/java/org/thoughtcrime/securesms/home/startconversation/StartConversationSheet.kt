package org.thoughtcrime.securesms.home.startconversation

import android.annotation.SuppressLint
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.home.startconversation.group.CreateGroupScreen
import org.thoughtcrime.securesms.home.startconversation.home.StartConversationScreen
import org.thoughtcrime.securesms.home.startconversation.invitefriend.InviteFriend
import org.thoughtcrime.securesms.ui.NavigationAction
import org.thoughtcrime.securesms.ui.ObserveAsEvents
import org.thoughtcrime.securesms.ui.UINavigator
import org.thoughtcrime.securesms.ui.components.BaseBottomSheet
import org.thoughtcrime.securesms.ui.horizontalSlideComposable
import org.thoughtcrime.securesms.ui.theme.PreviewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartConversationSheet(
    modifier: Modifier = Modifier,
    accountId: String,
    navigator: UINavigator<StartConversationDestination>,
    onDismissRequest: () -> Unit,
){
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()

    BaseBottomSheet(
        modifier = modifier,
        sheetState = sheetState,
        dragHandle = null,
        onDismissRequest = onDismissRequest
    ){
        BoxWithConstraints(modifier = modifier) {
            val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
            val targetHeight = (this.maxHeight - topInset) * 0.94f // sheet should take up 94% of the height, without the staatus bar
            Box(
                modifier = Modifier.height(targetHeight),
                contentAlignment = Alignment.TopCenter
            ) {
                StartConversationNavHost(
                    accountId = accountId,
                    navigator = navigator,
                    onClose = {
                        scope.launch {
                            sheetState.hide()
                            onDismissRequest()
                        }
                    }
                )
            }
        }
    }
}

// Destinations
@Serializable
sealed interface StartConversationDestination {
    @Serializable
    data object Home: StartConversationDestination

    @Serializable
    data object NewMessage: StartConversationDestination

    @Serializable
    data object CreateGroup: StartConversationDestination

    @Serializable
    data object JoinCommunity: StartConversationDestination

    @Serializable
    data object InviteFriend: StartConversationDestination
}

@SuppressLint("RestrictedApi")
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StartConversationNavHost(
    accountId: String,
    navigator: UINavigator<StartConversationDestination>,
    onClose: () -> Unit
){
    SharedTransitionLayout {
        val navController = rememberNavController()

        ObserveAsEvents(flow = navigator.navigationActions) { action ->
            when (action) {
                is NavigationAction.Navigate -> navController.navigate(
                    action.destination
                ) {
                    action.navOptions(this)
                }

                NavigationAction.NavigateUp -> navController.navigateUp()

                is NavigationAction.NavigateToIntent -> {
                    navController.context.startActivity(action.intent)
                }

                is NavigationAction.ReturnResult -> {}
            }
        }

        val scope = rememberCoroutineScope()

        NavHost(navController = navController, startDestination = StartConversationDestination.Home) {
            // Home
            horizontalSlideComposable<StartConversationDestination.Home> {
                StartConversationScreen (
                    accountId = accountId,
                    onClose = onClose,
                    navigateTo = {
                        scope.launch { navigator.navigate(it) }
                    }
                )
            }

            // New Message

            // Create Group
            horizontalSlideComposable<StartConversationDestination.CreateGroup> {
                val activity = LocalActivity.current

                CreateGroupScreen(
                    onNavigateToConversationScreen = { threadID ->
                        activity?.startActivity(
                            Intent(activity, ConversationActivityV2::class.java)
                                .putExtra(ConversationActivityV2.THREAD_ID, threadID)
                        )
                    },
                    onBack = { scope.launch { navigator.navigateUp() }},
                    onClose = onClose,
                    fromLegacyGroupId = null,
                )
            }

            // Join Community

            // Invite Friend
            horizontalSlideComposable<StartConversationDestination.InviteFriend> {
                InviteFriend(
                    accountId = accountId,
                    onBack = { scope.launch { navigator.navigateUp() }},
                    onClose = onClose
                )
            }

        }
    }
}

@Preview
@Composable
fun PreviewStartConversationSheet(){
    PreviewTheme {
        StartConversationSheet(
            accountId = "",
            onDismissRequest = {},
            navigator = UINavigator()
        )
    }
}