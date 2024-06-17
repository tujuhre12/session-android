package org.thoughtcrime.securesms.onboarding.messagenotifications

import android.content.Context
import android.content.Intent
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
import org.thoughtcrime.securesms.onboarding.loading.LoadingManager
import org.thoughtcrime.securesms.ui.setComposeContent
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.util.start
import javax.inject.Inject

@AndroidEntryPoint
class MessageNotificationsActivity : BaseActionBarActivity() {

    @Inject lateinit var pushRegistry: PushRegistry
    @Inject lateinit var prefs: TextSecurePreferences
    @Inject lateinit var loadingManager: LoadingManager

    private val viewModel: MessageNotificationsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo(true)
        prefs.setHasSeenWelcomeScreen(true)

        setComposeContent { MessageNotificationsScreen() }
    }

    @Composable
    private fun MessageNotificationsScreen() {
        val state by viewModel.stateFlow.collectAsState()
        MessageNotificationsScreen(state, viewModel::setEnabled, ::register)
    }

    private fun register() {
        prefs.setPushEnabled(viewModel.stateFlow.value.pushEnabled)
        ApplicationContext.getInstance(this).startPollingIfNeeded()
        pushRegistry.refresh(true)

        when {
            prefs.getHasViewedSeed() && !prefs.getConfigurationMessageSynced() -> start<LoadingActivity>()
            prefs.getProfileName() != null -> startHomeActivity()
            else -> startHomeActivity()
        }
    }
}

fun Context.startMessageNotificationsActivity(flags: Int = 0) {
    Intent(this, MessageNotificationsActivity::class.java)
        .also { it.flags = flags }
        .also(::startActivity)
}
