package org.thoughtcrime.securesms.onboarding.recoverypassword

import android.os.Bundle
import androidx.activity.viewModels
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.ui.setComposeContent

class RecoveryPasswordActivity : BaseActionBarActivity() {

    private val viewModel: RecoveryPasswordViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.title = resources.getString(R.string.sessionRecoveryPassword)

        setComposeContent {
            RecoveryPasswordScreen(
                viewModel.seed,
                { viewModel.copySeed(this) }
            ) { onHide() }
        }
    }

    private fun onHide() {
        showSessionDialog {
            title(R.string.recoveryPasswordHidePermanently)
            htmlText(R.string.recoveryPasswordHidePermanentlyDescription1)
            destructiveButton(R.string.continue_2, R.string.AccessibilityId_continue) { onHideConfirm() }
            cancelButton()
        }
    }

    private fun onHideConfirm() {
        showSessionDialog {
            title(R.string.recoveryPasswordHidePermanently)
            text(R.string.recoveryPasswordHidePermanentlyDescription2)
            cancelButton()
            destructiveButton(
                R.string.yes,
                contentDescription = R.string.AccessibilityId_confirm_button
            ) {
                viewModel.permanentlyHidePassword()
                finish()
            }
        }
    }
}
