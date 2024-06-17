package org.thoughtcrime.securesms.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.onboarding.pickname.startPickDisplayNameActivity
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.showOpenUrlDialog
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.components.BorderlessHtmlButton
import org.thoughtcrime.securesms.ui.components.PrimaryFillButton
import org.thoughtcrime.securesms.ui.components.PrimaryOutlineButton
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.h4
import org.thoughtcrime.securesms.ui.large
import org.thoughtcrime.securesms.ui.setComposeContent
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.util.start
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class LandingActivity: BaseActionBarActivity() {

    @Inject lateinit var prefs: TextSecurePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // We always hit this LandingActivity on launch - but if there is a previous instance of
        // Session then close this activity to resume the last activity from the previous instance.
        if (!isTaskRoot) { finish(); return }

        setUpActionBarSessionLogo(true)

        setComposeContent {
            LandingScreen(
                createAccount = {
                    prefs.setHasViewedSeed(false)
                    startPickDisplayNameActivity()
                }
            )
        }

        IdentityKeyUtil.generateIdentityKeyPair(this)
        TextSecurePreferences.setPasswordDisabled(this, true)
        // AC: This is a temporary workaround to trick the old code that the screen is unlocked.
        KeyCachingService.setMasterSecret(applicationContext, Object())
    }

    @Preview
    @Composable
    private fun PreviewLandingScreen(
        @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
    ) {
        PreviewTheme(colors) {
            LandingScreen {}
        }
    }

    @Composable
    private fun LandingScreen(createAccount: () -> Unit) {
        var count by remember { mutableStateOf(0) }
        val listState = rememberLazyListState()

        LaunchedEffect(Unit) {
            delay(500.milliseconds)
            while(count < MESSAGES.size) {
                count += 1
                listState.animateScrollToItem(0.coerceAtLeast((count - 1)))
                delay(1500L)
            }
        }

        Column {
            Column(modifier = Modifier
                .weight(1f)
                .padding(horizontal = LocalDimensions.current.marginOnboarding)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.onboardingBubblePrivacyInYourPocket),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = h4,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(LocalDimensions.current.itemSpacingMedium))

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .heightIn(min = LocalDimensions.current.minScrollableViewHeight)
                        .fillMaxWidth()
                        .weight(3f),
                    verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.itemSpacingSmall)
                ) {
                    items(
                        MESSAGES.take(count),
                        key = { it.stringId }
                    ) { item ->
                        AnimateMessageText(
                            stringResource(item.stringId),
                            item.isOutgoing
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            Column(modifier = Modifier.padding(horizontal = LocalDimensions.current.marginLarge)) {
                PrimaryFillButton(
                    text = stringResource(R.string.onboardingAccountCreate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .contentDescription(R.string.AccessibilityId_create_account_button),
                    onClick = createAccount
                )
                Spacer(modifier = Modifier.height(LocalDimensions.current.itemSpacingExtraSmall))
                PrimaryOutlineButton(
                    stringResource(R.string.onboardingAccountExists),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .contentDescription(R.string.AccessibilityId_restore_account_button),
                ) { start<LinkDeviceActivity>() }
                BorderlessHtmlButton(
                    textId = R.string.onboardingTosPrivacy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .contentDescription(R.string.AccessibilityId_open_url),
                    onClick = ::openDialog
                )
                Spacer(modifier = Modifier.height(LocalDimensions.current.itemSpacingExtraSmall))
            }
        }
    }

    private fun openDialog() {
        showOpenUrlDialog {
            button(
                R.string.activity_landing_terms_of_service,
                contentDescriptionRes = R.string.AccessibilityId_terms_of_service_button
            ) { open("https://getsession.org/terms-of-service") }
            button(
                R.string.activity_landing_privacy_policy,
                contentDescriptionRes = R.string.AccessibilityId_privacy_policy_button
            ) { open("https://getsession.org/privacy-policy") }
        }
    }

    private fun open(url: String) {
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).let(::startActivity)
    }
}

@Composable
private fun AnimateMessageText(text: String, isOutgoing: Boolean, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box {
        // TODO [SES-2077] Use LazyList itemAnimation when we update to compose 1.7 or so.
        MessageText(text, isOutgoing, Modifier.alpha(0f))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(durationMillis = 300)) +
                    slideInVertically(animationSpec = tween(durationMillis = 300)) { it }
        ) {
            MessageText(text, isOutgoing, modifier)
        }
    }
}

@Composable
private fun MessageText(text: String, isOutgoing: Boolean, modifier: Modifier) {
    Box(modifier = modifier then Modifier.fillMaxWidth()) {
        MessageText(
            text,
            color = if (isOutgoing) LocalColors.current.backgroundBubbleSent else LocalColors.current.backgroundBubbleReceived,
            textColor = if (isOutgoing) LocalColors.current.textBubbleSent else LocalColors.current.textBubbleReceived,
            modifier = Modifier.align(if (isOutgoing) Alignment.TopEnd else Alignment.TopStart)
        )
    }
}

@Composable
private fun MessageText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified
) {
    Card(
        modifier = modifier.fillMaxWidth(0.666f),
        shape = RoundedCornerShape(size = 13.dp),
        backgroundColor = color,
        elevation = 0.dp
    ) {
        Text(
            text,
            style = large,
            color = textColor,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

private data class TextData(
    @StringRes val stringId: Int,
    val isOutgoing: Boolean = false
)

private val MESSAGES = listOf(
    TextData(R.string.onboardingBubbleWelcomeToSession),
    TextData(R.string.onboardingBubbleSessionIsEngineered, isOutgoing = true),
    TextData(R.string.onboardingBubbleNoPhoneNumber),
    TextData(R.string.onboardingBubbleCreatingAnAccountIsEasy, isOutgoing = true)
)
