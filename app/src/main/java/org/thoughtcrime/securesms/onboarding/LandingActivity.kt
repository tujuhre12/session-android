package org.thoughtcrime.securesms.onboarding

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.onboarding.pickname.startPickDisplayNameActivity
import org.thoughtcrime.securesms.service.KeyCachingService
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
                Text("Privacy in your pocket.", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.h4, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                IncomingText("Welcome to Session \uD83D\uDC4B")
                Spacer(modifier = Modifier.height(14.dp))
                OutgoingText("Session is engineered\nto protect your privacy.")
                Spacer(modifier = Modifier.height(14.dp))
                IncomingText("You don’t even need a phone number to sign up. ")
                Spacer(modifier = Modifier.height(14.dp))
                OutgoingText("Creating an account is \ninstant, free, and \nanonymous \uD83D\uDC47")
                Spacer(modifier = Modifier.weight(1f))

                OutlineButton(text = "Create account", modifier = Modifier
                    .width(262.dp)
                    .align(Alignment.CenterHorizontally)) { startPickDisplayNameActivity() }
                Spacer(modifier = Modifier.height(14.dp))
                FilledButton(text = "I have an account", modifier = Modifier
                    .width(262.dp)
                    .align(Alignment.CenterHorizontally)) { startLinkDeviceActivity() }
                Spacer(modifier = Modifier.height(8.dp))
                BorderlessButton(
                    text = "By using this service, you agree to our Terms of Service and Privacy Policy",
                    modifier = Modifier
                        .width(262.dp)
                        .align(Alignment.CenterHorizontally),
                    fontSize = 11.sp,
                    lineHeight = 13.sp
                ) {  }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
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
