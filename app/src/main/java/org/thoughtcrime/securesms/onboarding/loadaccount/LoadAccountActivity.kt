package org.thoughtcrime.securesms.onboarding.loadaccount

import android.os.Bundle
import androidx.activity.viewModels
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.onboarding.loading.LoadingManager
import org.thoughtcrime.securesms.onboarding.messagenotifications.startMessageNotificationsActivity
import org.thoughtcrime.securesms.ui.setComposeContent
import javax.inject.Inject

@AndroidEntryPoint
@androidx.annotation.OptIn(ExperimentalGetImage::class)
class LoadAccountActivity : BaseActionBarActivity() {

    @Inject lateinit var prefs: TextSecurePreferences
    @Inject lateinit var loadingManager: LoadingManager

    private val viewModel: LinkDeviceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setTitle(R.string.activity_link_load_account)
        prefs.setHasViewedSeed(true)
        prefs.setConfigurationMessageSynced(false)
        prefs.setRestorationTime(System.currentTimeMillis())
        prefs.setLastProfileUpdateTime(0)

        lifecycleScope.launch {
            viewModel.eventFlow.collect {
                loadingManager.load(it.mnemonic)
                startMessageNotificationsActivity()
                finish()
            }
        }

        setComposeContent {
            val state by viewModel.stateFlow.collectAsState()
            LoadAccountScreen(state, viewModel.qrErrorsFlow, viewModel::onChange, viewModel::onContinue, viewModel::onScanQrCode)
        }
    }
}
