package org.thoughtcrime.securesms.onboarding

import android.os.Bundle
import androidx.activity.viewModels
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.baseBold
import org.thoughtcrime.securesms.ui.components.MaybeScanQrCode
import org.thoughtcrime.securesms.ui.components.OutlineButton
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.components.SessionTabRow
import org.thoughtcrime.securesms.ui.contentDescription
import javax.inject.Inject

private const val TAG = "LinkDeviceActivity"

private val TITLES = listOf(R.string.sessionRecoveryPassword, R.string.qrScan)

@AndroidEntryPoint
@androidx.annotation.OptIn(ExperimentalGetImage::class)
class LinkDeviceActivity : BaseActionBarActivity() {

    @Inject
    lateinit var prefs: TextSecurePreferences

    val viewModel: LinkDeviceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setTitle(R.string.activity_link_load_account)
        prefs.setHasViewedSeed(true)
        prefs.setConfigurationMessageSynced(false)
        prefs.setRestorationTime(System.currentTimeMillis())
        prefs.setLastProfileUpdateTime(0)

        lifecycleScope.launch {
            viewModel.eventFlow.collect {
                startLoadingActivity(it.mnemonic)
                finish()
            }
        }

        ComposeView(this).apply {
            setContent {
                val state by viewModel.stateFlow.collectAsState()
                AppTheme {
                    LoadAccountScreen(state, viewModel::onChange, viewModel::onContinue, viewModel::onScanQrCode)
                }
            }
        }.let(::setContentView)
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun LoadAccountScreen(
        state: LinkDeviceState,
        onChange: (String) -> Unit = {},
        onContinue: () -> Unit = {},
        onScan: (String) -> Unit = {}
    ) {
        val pagerState = rememberPagerState { TITLES.size }

        Column {
            SessionTabRow(pagerState, TITLES)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val title = TITLES[page]

                when (title) {
                    R.string.sessionRecoveryPassword -> RecoveryPassword(state, onChange, onContinue)
                    R.string.qrScan -> MaybeScanQrCode(viewModel.qrErrorsFlow, onScan = onScan)
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewRecoveryPassword() = RecoveryPassword(state = LinkDeviceState())

@Composable
fun RecoveryPassword(state: LinkDeviceState, onChange: (String) -> Unit = {}, onContinue: () -> Unit = {}) {
    Column(
        modifier = Modifier.padding(horizontal = LocalDimensions.current.marginLarge)
    ) {
        Spacer(Modifier.weight(1f))
        Row {
            Text(
                stringResource(R.string.sessionRecoveryPassword),
                style = MaterialTheme.typography.h4
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_shield_outline),
                contentDescription = null,
            )
        }
        Spacer(Modifier.size(28.dp))
        Text(
            stringResource(R.string.activity_link_enter_your_recovery_password_to_load_your_account_if_you_haven_t_saved_it_you_can_find_it_in_your_app_settings),
            style = MaterialTheme.typography.base
        )
        Spacer(Modifier.size(24.dp))
        SessionOutlinedTextField(
            text = state.recoveryPhrase,
            modifier = Modifier
                .fillMaxWidth()
                .contentDescription(R.string.AccessibilityId_recovery_phrase_input),
            placeholder = stringResource(R.string.recoveryPasswordEnter),
            onChange = onChange,
            onContinue = onContinue,
            error = state.error
        )
        Spacer(Modifier.weight(2f))
        OutlineButton(
            textId = R.string.continue_2,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = LocalDimensions.current.marginLarge, vertical = 20.dp)
                .width(200.dp),
            onClick = onContinue
        )
    }
}
