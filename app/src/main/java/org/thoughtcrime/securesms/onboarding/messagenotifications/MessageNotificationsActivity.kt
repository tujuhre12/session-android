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
import org.thoughtcrime.securesms.home.HomeActivity
import org.thoughtcrime.securesms.notifications.PushRegistry
import org.thoughtcrime.securesms.ui.setComposeContent
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo
import javax.inject.Inject

@AndroidEntryPoint
class MessageNotificationsActivity : BaseActionBarActivity() {

    @Inject lateinit var pushRegistry: PushRegistry

    private val viewModel: MessageNotificationsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo(true)
        TextSecurePreferences.setHasSeenWelcomeScreen(this, true)

        setComposeContent { MessageNotificationsScreen() }
    }

    @Composable
    private fun MessageNotificationsScreen() {
        val state by viewModel.stateFlow.collectAsState()
        MessageNotificationsScreen(state, viewModel::setEnabled, ::register)
    }

    private fun register() {
        TextSecurePreferences.setPushEnabled(this, viewModel.stateFlow.value.pushEnabled)
        ApplicationContext.getInstance(this).startPollingIfNeeded()
        pushRegistry.refresh(true)
        Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(HomeActivity.FROM_ONBOARDING, true)
        }.also(::startActivity)
    }
}

fun Context.startMessageNotificationsActivity(flags: Int = 0) {
    Intent(this, MessageNotificationsActivity::class.java)
        .also { it.flags = flags }
        .also(::startActivity)
}
