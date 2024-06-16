package org.thoughtcrime.securesms.onboarding.pickname

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.home.startHomeActivity
import org.thoughtcrime.securesms.onboarding.messagenotifications.startMessageNotificationsActivity
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.components.OutlineButton
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.h4
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

    @Preview
    @Composable
    fun PreviewDisplayName() {
        PreviewTheme {
            DisplayName(State())
        }
    }

    @Composable
    fun DisplayName(state: State, onChange: (String) -> Unit = {}, onContinue: () -> Unit = {}) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .padding(horizontal = 50.dp)
                .padding(bottom = 12.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(stringResource(state.title), style = h4)
            Text(
                stringResource(state.description),
                style = base,
                modifier = Modifier.padding(bottom = 12.dp))

            SessionOutlinedTextField(
                text = state.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .contentDescription(R.string.AccessibilityId_enter_display_name),
                placeholder = stringResource(R.string.displayNameEnter),
                onChange = onChange,
                onContinue = onContinue,
                error = state.error?.let { stringResource(it) }
            )

            Spacer(modifier = Modifier.weight(2f))

            OutlineButton(
                stringResource(R.string.continue_2),
                modifier = Modifier
                    .contentDescription(R.string.AccessibilityId_continue)
                    .align(Alignment.CenterHorizontally)
                    .width(262.dp),
                onClick = onContinue,
            )
        }
    }
}

fun Context.startPickDisplayNameActivity(failedToLoad: Boolean = false, flags: Int = 0) {
    ApplicationContext.getInstance(this).newAccount = !failedToLoad

    Intent(this, PickDisplayNameActivity::class.java)
        .apply { putExtra(EXTRA_FAILED_TO_LOAD, failedToLoad) }
        .also { it.flags = flags }
        .also(::startActivity)
}
