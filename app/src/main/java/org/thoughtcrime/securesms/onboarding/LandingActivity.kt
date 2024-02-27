package org.thoughtcrime.securesms.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.onboarding.pickname.startPickDisplayNameActivity
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.BorderlessButton
import org.thoughtcrime.securesms.ui.FilledButton
import org.thoughtcrime.securesms.ui.OutlineButton
import org.thoughtcrime.securesms.ui.classicDarkColors
import org.thoughtcrime.securesms.ui.session_accent
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo

class LandingActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // We always hit this LandingActivity on launch - but if there is a previous instance of
        // Session then close this activity to resume the last activity from the previous instance.
        if (!isTaskRoot) { finish(); return }

        setUpActionBarSessionLogo(true)

        ComposeView(this)
            .apply { setContent { LandingScreen() } }
            .let(::setContentView)

        IdentityKeyUtil.generateIdentityKeyPair(this)
        TextSecurePreferences.setPasswordDisabled(this, true)
        // AC: This is a temporary workaround to trick the old code that the screen is unlocked.
        KeyCachingService.setMasterSecret(applicationContext, Object())
    }

    @Preview
    @Composable
    private fun LandingScreen() {
        AppTheme {
            Column(modifier = Modifier.padding(horizontal = 36.dp)) {
                Spacer(modifier = Modifier.weight(1f))
                Text(stringResource(R.string.onboarding_privacy_in_your_pocket), modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.h4, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                IncomingText(stringResource(R.string.onboarding_welcome_to_session))
                Spacer(modifier = Modifier.height(14.dp))
                OutgoingText(stringResource(R.string.onboarding_session_is_engineered_to_protect_your_privacy))
                Spacer(modifier = Modifier.height(14.dp))
                IncomingText(stringResource(R.string.onboarding_you_don_t_even_need_a_phone_number_to_sign_up))
                Spacer(modifier = Modifier.height(14.dp))
                OutgoingText(stringResource(R.string.onboarding_creating_an_account_is_instant_free_and_anonymous))
                Spacer(modifier = Modifier.weight(1f))

                OutlineButton(text = stringResource(R.string.onboarding_create_account), modifier = Modifier
                    .width(262.dp)
                    .align(Alignment.CenterHorizontally)) { startPickDisplayNameActivity() }
                Spacer(modifier = Modifier.height(14.dp))
                FilledButton(text = stringResource(R.string.onboarding_i_have_an_account), modifier = Modifier
                    .width(262.dp)
                    .align(Alignment.CenterHorizontally)) { startLinkDeviceActivity() }
                Spacer(modifier = Modifier.height(8.dp))
                BorderlessButton(
                    text = stringResource(R.string.onboarding_by_using_this_service_you_agree_to_our_terms_of_service_and_privacy_policy),
                    modifier = Modifier
                        .width(262.dp)
                        .align(Alignment.CenterHorizontally),
                    fontSize = 11.sp,
                    lineHeight = 13.sp
                ) { openDialog() }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    private fun openDialog() {
        showSessionDialog {
            title(R.string.activity_landing_open_url_title)
            text(R.string.activity_landing_open_url_explanation)
            button(R.string.activity_landing_terms_of_service) { open("https://getsession.org/terms-of-service") }
            button(R.string.activity_landing_privacy_policy) { open("https://getsession.org/privacy-policy") }
        }
    }

    private fun open(url: String) {
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).let(::startActivity)
    }

    @Composable
    private fun IncomingText(text: String) {
        ChatText(
            text,
            color = classicDarkColors[2]
        )
    }

    @Composable
    private fun ColumnScope.OutgoingText(text: String) {
        ChatText(
            text,
            color = session_accent,
            textColor = MaterialTheme.colors.primary,
            modifier = Modifier.align(Alignment.End)
        )
    }

    @Composable
    private fun ChatText(
        text: String,
        color: Color,
        textColor: Color = Color.Unspecified,
        modifier: Modifier = Modifier
    ) {
        Text(
            text,
            fontSize = 16.sp,
            lineHeight = 19.sp,
            color = textColor,
            modifier = modifier
                .fillMaxWidth(0.666f)
                .background(
                    color = color,
                    shape = RoundedCornerShape(size = 13.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
