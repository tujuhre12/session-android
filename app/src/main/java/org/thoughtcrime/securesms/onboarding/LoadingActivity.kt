package org.thoughtcrime.securesms.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.onboarding.messagenotifications.startMessageNotificationsActivity
import org.thoughtcrime.securesms.onboarding.pickname.startPickDisplayNameActivity
import org.thoughtcrime.securesms.ui.ProgressArc
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.h7
import org.thoughtcrime.securesms.ui.setComposeContent
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo
import javax.inject.Inject

private const val EXTRA_MNEMONIC = "mnemonic"

@AndroidEntryPoint
class LoadingActivity: BaseActionBarActivity() {

    @Inject
    lateinit var configFactory: ConfigFactory

    @Inject
    lateinit var prefs: TextSecurePreferences

    private val viewModel: LoadingViewModel by viewModels()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        return
    }

    private fun register(skipped: Boolean) {
        prefs.setLastConfigurationSyncTime(System.currentTimeMillis())

        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        when {
            skipped -> startPickDisplayNameActivity(true, flags)
            else -> startMessageNotificationsActivity(flags)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ApplicationContext.getInstance(this).newAccount = false

        setComposeContent { LoadingScreen() }

        setUpActionBarSessionLogo(true)

        viewModel.restore(application, intent.getByteArrayExtra(EXTRA_MNEMONIC)!!)

        lifecycleScope.launch {
            viewModel.eventFlow.collect {
                when (it) {
                    Event.TIMEOUT -> register(skipped = true)
                    Event.SUCCESS -> register(skipped = false)
                }
            }
        }
    }

    @Composable
    fun LoadingScreen() {
        val state by viewModel.stateFlow.collectAsState()

        val animatable = remember { Animatable(initialValue = 0f, visibilityThreshold = 0.005f) }

        LaunchedEffect(state) {
            animatable.stop()
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = TweenSpec(durationMillis = state.duration.inWholeMilliseconds.toInt())
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.weight(1f))
            ProgressArc(
                animatable.value,
                modifier = Modifier.contentDescription(R.string.AccessibilityId_loading_animation)
            )
            Text(
                stringResource(R.string.waitOneMoment),
                style = h7
            )
            Text(
                stringResource(R.string.loadAccountProgressMessage),
                style = base
            )
            Spacer(modifier = Modifier.weight(2f))
        }
    }
}

fun Context.startLoadingActivity(mnemonic: ByteArray) {
    Intent(this, LoadingActivity::class.java)
        .apply { putExtra(EXTRA_MNEMONIC, mnemonic) }
        .also(::startActivity)
}
