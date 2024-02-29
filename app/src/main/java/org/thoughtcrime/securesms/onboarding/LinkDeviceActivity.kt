package org.thoughtcrime.securesms.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.OutlineButton
import org.thoughtcrime.securesms.ui.baseBold
import org.thoughtcrime.securesms.ui.colorDestructive
import javax.inject.Inject

@AndroidEntryPoint
class LinkDeviceActivity : BaseActionBarActivity() {

    @Inject
    lateinit var prefs: TextSecurePreferences

    val viewModel: LinkDeviceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Load Account"
        prefs.setHasViewedSeed(true)
        prefs.setConfigurationMessageSynced(false)
        prefs.setRestorationTime(System.currentTimeMillis())
        prefs.setLastProfileUpdateTime(0)

        lifecycleScope.launch {
            viewModel.eventFlow.collect {
                startLoadingActivity(it.mnemonic)
            }
        }


        ComposeView(this).apply {
            setContent {
                val state by viewModel.stateFlow.collectAsState()
                AppTheme {
                    LoadAccountScreen(state, viewModel::onChange, viewModel::onRecoveryPhrase)
                }
            }
        }.let(::setContentView)
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun LoadAccountScreen(state: LinkDeviceState, onChange: (String) -> Unit = {}, onContinue: () -> Unit = {}) {
        val tabs = listOf(R.string.activity_recovery_password, R.string.activity_link_device_scan_qr_code)
        val pagerState = rememberPagerState { tabs.size }

        Column {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.height(48.dp)
            ) {
                tabs.forEachIndexed { i, it ->
                    Tab(i == pagerState.currentPage, onClick = { pagerState.targetPage }) {
                        Text(stringResource(id = it))
                    }
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { i ->
                when(tabs[i]) {
                    R.string.activity_recovery_password -> RecoveryPassword(state, onChange, onContinue)
                    R.string.activity_link_device_scan_qr_code -> ScanQrCode()
                }
            }
        }
    }
}

@Composable
fun RecoveryPassword(state: LinkDeviceState, onChange: (String) -> Unit = {}, onContinue: () -> Unit = {}) {
    Column(
        modifier = Modifier.padding(horizontal = 60.dp)
    ) {
        Spacer(Modifier.weight(1f))
        Row {
            Text("Recovery Password", style = MaterialTheme.typography.h4)
            Spacer(Modifier.width(6.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_recovery_phrase),
                contentDescription = "",
            )
        }
        Spacer(Modifier.size(28.dp))
        Text("Enter your recovery password to load your account. If you haven't saved it, you can find it in your app settings.")
        Spacer(Modifier.size(24.dp))
        OutlinedTextField(
            value = state.recoveryPhrase,
            onValueChange = { onChange(it) },
            placeholder = { Text("Enter your recovery password") },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = state.error?.let { colorDestructive } ?: LocalContentColor.current.copy(LocalContentAlpha.current),
                focusedBorderColor = Color(0xff414141),
                unfocusedBorderColor = Color(0xff414141),
                cursorColor = LocalContentColor.current,
                placeholderColor = state.error?.let { colorDestructive } ?: MaterialTheme.colors.onSurface.copy(ContentAlpha.medium)
            ),
            singleLine = true,
            keyboardActions = KeyboardActions(
                onDone = { onContinue() },
                onGo = { onContinue() },
                onSearch = { onContinue() },
                onSend = { onContinue() },
            ),
            isError = state.error != null,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.size(12.dp))
        state.error?.let {
            Text(it, style = MaterialTheme.typography.baseBold, color = MaterialTheme.colors.error)
        }
        Spacer(Modifier.weight(2f))
        OutlineButton(
            text = stringResource(id = R.string.continue_2),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 64.dp, vertical = 20.dp).width(200.dp)
        ) { onContinue() }
    }
}

@Composable
fun ScanQrCode() {

}

fun Context.startLinkDeviceActivity() {
    Intent(this, LinkDeviceActivity::class.java).let(::startActivity)
}
