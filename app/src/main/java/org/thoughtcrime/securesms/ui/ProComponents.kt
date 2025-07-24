package org.thoughtcrime.securesms.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideSubcomposition
import com.bumptech.glide.integration.compose.RequestState
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.NonTranslatableStringConstants
import org.session.libsession.utilities.StringSubstitutionConstants.APP_PRO_KEY
import org.thoughtcrime.securesms.ui.components.AccentFillButtonRect
import org.thoughtcrime.securesms.ui.components.TertiaryFillButtonRect
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors

@Composable
fun ProBadgeText(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalType.current.h5,
    showBadge: Boolean = true,
    invertBadgeColor: Boolean = false,
    badgeAtStart: Boolean = false,
    onBadgeClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.xxxsSpacing)
    ) {
        val proBadgeContent = @Composable {
            if (showBadge) {
                var proBadgeModifier: Modifier = Modifier
                if (onBadgeClick != null) {
                    proBadgeModifier = proBadgeModifier.clickable {
                        onBadgeClick()
                    }
                }
                Image(
                    modifier = proBadgeModifier.height(textStyle.lineHeight.value.dp * 0.8f),
                    painter = painterResource(id = if (invertBadgeColor) R.drawable.ic_pro_badge_inverted else R.drawable.ic_pro_badge),
                    contentDescription = NonTranslatableStringConstants.APP_PRO,
                )
            }
        }

        val textContent = @Composable {
            Text(
                modifier = Modifier.weight(1f, fill = false),
                text = text,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (badgeAtStart) {
            proBadgeContent()
            textContent()
        } else {
            textContent()
            proBadgeContent()
        }
    }
}

@Preview
@Composable
private fun PreviewProBadgeText(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(LocalDimensions.current.smallSpacing),
            verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.xxsSpacing)
        ) {
            ProBadgeText(text = "Hello Pro", textStyle = LocalType.current.base)
            ProBadgeText(text = "Hello Pro")
            ProBadgeText(text = "Inverted Badge Color", invertBadgeColor = true)
            ProBadgeText(text = "Hello Pro with a very long name that should overflow")
            ProBadgeText(text = "No Badge", showBadge = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionProCTA(
    content: @Composable () -> Unit,
    text: String,
    features: List<CTAFeature>,
    modifier: Modifier = Modifier,
    onUpgrade: () -> Unit,
    onCancel: () -> Unit,
){
    BasicAlertDialog(
        modifier = modifier,
        onDismissRequest = onCancel,
        content = {
            DialogBg {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // hero image
                    BottomFadingEdgeBox(
                        fadingEdgeHeight = 70.dp,
                        fadingColor = LocalColors.current.backgroundSecondary,
                        content = { _ ->
                            content()
                        },
                    )

                    // content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(LocalDimensions.current.smallSpacing)
                    ) {
                        // title
                        ProBadgeText(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            text = stringResource(R.string.upgradeTo)
                        )

                        Spacer(Modifier.height(LocalDimensions.current.contentSpacing))

                        // main message
                        Text(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            text = text,
                            textAlign = TextAlign.Center,
                            style = LocalType.current.base.copy(
                                color = LocalColors.current.textSecondary
                            )
                        )

                        Spacer(Modifier.height(LocalDimensions.current.contentSpacing))

                        // features
                        features.forEachIndexed { index, feature ->
                            ProCTAFeature(data = feature)
                            if(index < features.size - 1){
                                Spacer(Modifier.height(LocalDimensions.current.xsSpacing))
                            }
                        }

                        Spacer(Modifier.height(LocalDimensions.current.contentSpacing))

                        // buttons
                        Row(
                            Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.xsSpacing),
                        ) {
                            AccentFillButtonRect(
                                modifier = Modifier.weight(1f).shimmerOverlay(),
                                text = stringResource(R.string.theContinue),
                                onClick = onUpgrade
                            )

                            TertiaryFillButtonRect(
                                modifier = Modifier.weight(1f),
                                text = stringResource(R.string.cancel),
                                onClick = onCancel
                            )
                        }
                    }
                }
            }
        }
    )
}

sealed interface CTAFeature {
    val text: String

    data class Icon(
        override val text: String,
        @DrawableRes val icon: Int = R.drawable.ic_circle_check,
    ): CTAFeature

    data class RainbowIcon(
        override val text: String,
        @DrawableRes val icon: Int = R.drawable.ic_pro_sparkle_custom,
    ): CTAFeature
}

@Composable
fun SimpleSessionProCTA(
    @DrawableRes heroImage: Int,
    text: String,
    features: List<CTAFeature>,
    modifier: Modifier = Modifier,
    onUpgrade: () -> Unit,
    onCancel: () -> Unit,
){
    SessionProCTA(
        modifier = modifier,
        text = text,
        features = features,
        onUpgrade = onUpgrade,
        onCancel = onCancel,
        content = { CTAImage(heroImage = heroImage) }
    )
}

@Composable
fun CTAImage(
    @DrawableRes heroImage: Int,
){
    Image(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalColors.current.accent),
        contentScale = ContentScale.FillWidth,
        painter = painterResource(id = heroImage),
        contentDescription = null,
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AnimatedSessionProCTA(
    @DrawableRes heroImageBg: Int,
    @DrawableRes heroImageAnimatedFg: Int,
    text: String,
    features: List<CTAFeature>,
    modifier: Modifier = Modifier,
    onUpgrade: () -> Unit,
    onCancel: () -> Unit,
){
    SessionProCTA(
        modifier = modifier,
        text = text,
        features = features,
        onUpgrade = onUpgrade,
        onCancel = onCancel,
        content = {
            CTAAnimatedImages(
                heroImageBg = heroImageBg,
                heroImageAnimatedFg = heroImageAnimatedFg
            )
        })
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun CTAAnimatedImages(
    @DrawableRes heroImageBg: Int,
    @DrawableRes heroImageAnimatedFg: Int,
){
    Image(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalColors.current.accent),
        contentScale = ContentScale.FillWidth,
        painter = painterResource(id = heroImageBg),
        contentDescription = null,
    )

    GlideSubcomposition(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        model = heroImageAnimatedFg,
    ){
        when (state) {
            is RequestState.Success -> {
                Image(
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                    painter = painter,
                    contentDescription = null,
                )
            }

            else -> {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionProActivatedCTA(
    content: @Composable () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
){
    BasicAlertDialog(
        modifier = modifier,
        onDismissRequest = onCancel,
        content = {
            DialogBg {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // hero image
                    BottomFadingEdgeBox(
                        fadingEdgeHeight = 70.dp,
                        fadingColor = LocalColors.current.backgroundSecondary,
                        content = { _ ->
                            content()
                        },
                    )

                    // content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(LocalDimensions.current.smallSpacing)
                    ) {
                        // title
                        ProBadgeText(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            text = stringResource(R.string.proActivated),
                            textStyle = LocalType.current.h5,
                            badgeAtStart = true
                        )

                        Spacer(Modifier.height(LocalDimensions.current.contentSpacing))

                        // already have pro
                        ProBadgeText(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            text = stringResource(R.string.proAlreadyPurchased),
                            textStyle = LocalType.current.base.copy(color = LocalColors.current.textSecondary)
                        )

                        Spacer(Modifier.height(2.dp))

                        // main message
                        Text(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            text = text,
                            textAlign = TextAlign.Center,
                            style = LocalType.current.base.copy(
                                color = LocalColors.current.textSecondary
                            )
                        )

                        Spacer(Modifier.height(LocalDimensions.current.contentSpacing))

                        // buttons
                        TertiaryFillButtonRect(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            text = stringResource(R.string.close),
                            onClick = onCancel
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun SimpleSessionProActivatedCTA(
    @DrawableRes heroImage: Int,
    text: String,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
){
    SessionProActivatedCTA(
        modifier = modifier,
        text = text,
        onCancel = onCancel,
        content = { CTAImage(heroImage = heroImage) }
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AnimatedSessionProActivatedCTA(
    @DrawableRes heroImageBg: Int,
    @DrawableRes heroImageAnimatedFg: Int,
    text: String,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
){
    SessionProActivatedCTA(
        modifier = modifier,
        text = text,
        onCancel = onCancel,
        content = {
            CTAAnimatedImages(
                heroImageBg = heroImageBg,
                heroImageAnimatedFg = heroImageAnimatedFg
            )
        })
}

/**
 * Added here for reusability since multiple screens need this dialog
 */
@Composable
fun PinProCTA(
    overTheLimit: Boolean,
    onUpgrade: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
){
    val context = LocalContext.current
    
    SimpleSessionProCTA(
        modifier = modifier,
        heroImage = R.drawable.cta_hero_pins,
        text = if(overTheLimit)
            Phrase.from(context, R.string.proCallToActionPinnedConversations)
                .put(APP_PRO_KEY, NonTranslatableStringConstants.SESSION_PRO)
                .format()
                .toString()
        else
            Phrase.from(context, R.string.proCallToActionPinnedConversationsMoreThan)
                .put(APP_PRO_KEY, NonTranslatableStringConstants.SESSION_PRO)
                .format()
                .toString(),
        features = listOf(
            CTAFeature.Icon(stringResource(R.string.proFeatureListPinnedConversations)),
            CTAFeature.Icon(stringResource(R.string.proFeatureListLargerGroups)),
            CTAFeature.RainbowIcon(stringResource(R.string.proFeatureListLoadsMore)),
        ),
        onUpgrade = onUpgrade,
        onCancel = onCancel
    )
}

@Preview
@Composable
private fun PreviewProCTA(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        SimpleSessionProCTA(
            heroImage = R.drawable.cta_hero_char_limit,
            text = "This is a description of this Pro feature",
            features = listOf(
                CTAFeature.Icon("Feature one"),
                CTAFeature.Icon("Feature two", R.drawable.ic_eye),
                CTAFeature.RainbowIcon("Feature three"),
            ),
            onUpgrade = {},
            onCancel = {}
        )
    }
}

@Preview
@Composable
private fun PreviewProActivatedCTA(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        SimpleSessionProActivatedCTA(
            heroImage = R.drawable.cta_hero_char_limit,
            text = "This is a description of this Pro feature",
            onCancel = {}
        )
    }
}

@Composable
fun ProCTAFeature(
    data: CTAFeature,
    modifier: Modifier = Modifier
){
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LocalDimensions.current.xxxsSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.xxsSpacing)
    ) {
        when(data){
            is CTAFeature.Icon -> {
                Image(
                    painter = painterResource(id = data.icon),
                    colorFilter = ColorFilter.tint(LocalColors.current.accentText ),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            is CTAFeature.RainbowIcon -> {
                AnimatedGradientDrawable(
                    vectorRes = data.icon
                )
            }
        }

        Text(
            text = data.text,
            style = LocalType.current.base
        )
    }
}