package org.thoughtcrime.securesms.onboarding.messagenotifications

import android.app.Activity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.home.startHomeActivity
import org.thoughtcrime.securesms.notifications.PushRegistry
import org.thoughtcrime.securesms.onboarding.loading.LoadingActivity
import org.thoughtcrime.securesms.onboarding.manager.LoadingManager
import org.thoughtcrime.securesms.onboarding.messagenotifications.MessageNotificationsActivity.Companion.EXTRA_PROFILE_NAME
import org.thoughtcrime.securesms.ui.setComposeContent
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.util.start
import javax.inject.Inject

@AndroidEntryPoint
class MessageNotificationsActivity : BaseActionBarActivity() {

    companion object {
        const val EXTRA_PROFILE_NAME = "EXTRA_PROFILE_NAME"
    }

    @Inject
    internal lateinit var viewModelFactory: MessageNotificationsViewModel.AssistedFactory

    @Inject lateinit var pushRegistry: PushRegistry
    @Inject lateinit var prefs: TextSecurePreferences
    @Inject lateinit var loadingManager: LoadingManager

    val profileName by lazy { intent.getStringExtra(EXTRA_PROFILE_NAME) }

    private val viewModel: MessageNotificationsViewModel by viewModels {
        viewModelFactory.create(profileName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo()
        prefs.setHasSeenWelcomeScreen(true)

        setComposeContent { MessageNotificationsScreen() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (viewModel.onBackPressed()) return

        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    @Composable
    private fun MessageNotificationsScreen() {
        val uiState by viewModel.uiStates.collectAsState()
        MessageNotificationsScreen(
            uiState,
            setEnabled = viewModel::setEnabled,
            onContinue = ::register,
            quit = viewModel::quit,
            dismissDialog = viewModel::dismissDialog
        )
    }

    private fun register() {
        prefs.setPushEnabled(viewModel.uiStates.value.pushEnabled)
        ApplicationContext.getInstance(this).startPollingIfNeeded()
        pushRegistry.refresh(true)

        when {
            prefs.getHasViewedSeed() && !prefs.getConfigurationMessageSynced() -> start<LoadingActivity>()
            else -> startHomeActivity()
        }
    }
}

fun Activity.startMessageNotificationsActivity(profileName: String) {
    start<MessageNotificationsActivity> { putExtra(EXTRA_PROFILE_NAME, profileName) }
}
