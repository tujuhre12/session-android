package org.thoughtcrime.securesms.onboarding.pickname

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.home.startHomeActivity
import org.thoughtcrime.securesms.onboarding.messagenotifications.startMessageNotificationsActivity
import org.thoughtcrime.securesms.ui.setComposeContent
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo
import javax.inject.Inject

private const val EXTRA_FAILED_TO_LOAD = "extra_failed_to_load"

@AndroidEntryPoint
class PickDisplayNameActivity : BaseActionBarActivity() {

    @Inject lateinit var viewModelFactory: PickDisplayNameViewModel.AssistedFactory
    @Inject lateinit var prefs: TextSecurePreferences

    val failedToLoad get() = intent.getBooleanExtra(EXTRA_FAILED_TO_LOAD, false)

    private val viewModel: PickDisplayNameViewModel by viewModels {
        viewModelFactory.create(failedToLoad)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo()

        setComposeContent { DisplayNameScreen(viewModel) }

        if (!failedToLoad) prefs.setHasViewedSeed(false)

        lifecycleScope.launch {
            viewModel.eventFlow.collect {
                if (failedToLoad) startHomeActivity() else startMessageNotificationsActivity()
            }
        }
    }

    @Composable
    private fun DisplayNameScreen(viewModel: PickDisplayNameViewModel) {
        val state = viewModel.stateFlow.collectAsState()
        DisplayName(state.value, viewModel::onChange) { viewModel.onContinue(this) }
    }
}

fun Context.startPickDisplayNameActivity(failedToLoad: Boolean = false, flags: Int = 0) {
    ApplicationContext.getInstance(this).newAccount = !failedToLoad

    Intent(this, PickDisplayNameActivity::class.java)
        .apply { putExtra(EXTRA_FAILED_TO_LOAD, failedToLoad) }
        .also { it.flags = flags }
        .also(::startActivity)
}
