package org.thoughtcrime.securesms.onboarding.messagenotifications

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.home.HomeActivity
import org.thoughtcrime.securesms.notifications.PushRegistry
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.OutlineButton
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.ThemeResPreviewParameterProvider
import org.thoughtcrime.securesms.ui.h8
import org.thoughtcrime.securesms.ui.h9
import org.thoughtcrime.securesms.ui.session_accent
import org.thoughtcrime.securesms.ui.small
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

        ComposeView(this)
            .apply { setContent { MessageNotifications() } }
            .let(::setContentView)
    }

    @Composable
    private fun MessageNotifications() {
        val state by viewModel.stateFlow.collectAsState()

        AppTheme {
            MessageNotifications(state, viewModel::setEnabled, ::register)
        }
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

@Preview
@Composable
fun MessageNotificationsPreview(
    @PreviewParameter(ThemeResPreviewParameterProvider::class) themeResId: Int
) {
    PreviewTheme(themeResId) {
        MessageNotifications()
    }
}

@Composable
fun MessageNotifications(
    state: MessageNotificationsState = MessageNotificationsState(),
    setEnabled: (Boolean) -> Unit = {},
    onContinue: () -> Unit = {}
) {
    Column(Modifier.padding(horizontal = 32.dp)) {
        Spacer(Modifier.weight(1f))
        Text("Message notifications", style = MaterialTheme.typography.h4)
        Spacer(Modifier.height(16.dp))
        Text("There are two ways Session can notify you of new messages.")
        Spacer(Modifier.height(16.dp))
        NotificationRadioButton(
            R.string.activity_pn_mode_fast_mode,
            R.string.activity_pn_mode_fast_mode_explanation,
            R.string.activity_pn_mode_recommended_option_tag,
            selected = state.pushEnabled,
            onClick = { setEnabled(true) }
        )
        Spacer(Modifier.height(16.dp))
        NotificationRadioButton(
            R.string.activity_pn_mode_slow_mode,
            R.string.activity_pn_mode_slow_mode_explanation,
            selected = state.pushDisabled,
            onClick = { setEnabled(false) }
        )
        Spacer(Modifier.weight(1f))
        OutlineButton(
            stringResource(R.string.continue_2),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(262.dp),
            onClick = onContinue
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun NotificationRadioButton(
    @StringRes title: Int,
    @StringRes explanation: Int,
    @StringRes tag: Int? = null,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(backgroundColor = MaterialTheme.colors.background, contentColor = Color.White),
            border = if (selected) BorderStroke(ButtonDefaults.OutlinedBorderSize, session_accent) else ButtonDefaults.outlinedBorder,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(stringResource(title), style = MaterialTheme.typography.h8)
                Text(stringResource(explanation), style = MaterialTheme.typography.small)
                tag?.let { Text(stringResource(it), color = session_accent, style = MaterialTheme.typography.h9) }
            }
        }
        RadioButton(selected = selected, modifier = Modifier.align(Alignment.CenterVertically), onClick = onClick)
    }
}

fun Context.startPNModeActivity(flags: Int = 0) {
    Intent(this, MessageNotificationsActivity::class.java)
        .also { it.flags = flags }
        .also(::startActivity)
}
