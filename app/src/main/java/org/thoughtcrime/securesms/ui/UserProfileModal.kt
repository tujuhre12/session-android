package org.thoughtcrime.securesms.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.session.libsession.utilities.NonTranslatableStringConstants
import org.thoughtcrime.securesms.ui.components.Avatar
import org.thoughtcrime.securesms.ui.components.SlimAccentOutlineButton
import org.thoughtcrime.securesms.ui.components.SlimOutlineCopyButton
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.theme.ThemeColors
import org.thoughtcrime.securesms.ui.theme.monospace
import org.thoughtcrime.securesms.ui.theme.primaryRed
import org.thoughtcrime.securesms.util.AvatarUIData
import org.thoughtcrime.securesms.util.AvatarUIElement

@Composable
fun UserProfileModal(
    data: UserProfileModalData,
    sendCommand: (UserProfileModalCommands) -> Unit
){
    // the user profile modal
    AlertDialog(
        onDismissRequest = {
            sendCommand(UserProfileModalCommands.HideUserProfileModal)
        },
        showCloseButton = true,
        title = null as AnnotatedString?,
        content = {
            // avatar
            val scaleSpec: AnimationSpec<Dp> = tween(
                durationMillis = 300,
                delayMillis = 0
            )

            val animatedAvatarSize by animateDpAsState(
                targetValue = if (data.expandedAvatar) {
                    LocalDimensions.current.iconXXLargeAvatar
                } else {
                    LocalDimensions.current.iconXXLarge
                },
                animationSpec = scaleSpec,
                label = "avatar_size_animation"
            )

            val animatedBadgeSize by animateDpAsState(
                targetValue = if (data.expandedAvatar) {
                    LocalDimensions.current.iconLargeAvatar
                } else {
                    LocalDimensions.current.iconMedium
                },
                animationSpec = scaleSpec,
                label = "badge_size_animation"
            )

            // animating the inner padding of the badge otherwise the icon looks too big within the background
            val animatedBadgeInnerPadding by animateDpAsState(
                targetValue = if (data.expandedAvatar) {
                    LocalDimensions.current.xxsSpacing
                } else {
                    5.dp
                },
                animationSpec = scaleSpec,
                label = "badge_inner_pd_animation"
            )

            // animating the end padding of the badge to keep it touching the avatar
            val animatedBadgeEndPadding by animateDpAsState(
                targetValue = if (data.expandedAvatar) {
                    LocalDimensions.current.smallSpacing
                } else {
                    0.dp
                },
                animationSpec = scaleSpec,
                label = "badge_inner_pd_animation"
            )

            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null // the ripple doesn't look nice as a square with the plus icon on top too
                    ) {
                        sendCommand(UserProfileModalCommands.ToggleAvatarExpand)
                    },
                contentAlignment = Alignment.Center
            ) {
                // avatar and qr code

                Avatar(
                    size = animatedAvatarSize,
                    data = data.avatarUIData
                )

                // qr/avatar button switch
                Crossfade(
                    modifier = Modifier.align(Alignment.TopEnd)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null // the ripple doesn't look nice as a square with the plus icon on top too
                        ) {
                            sendCommand(UserProfileModalCommands.ToggleQR)
                        }
                        .padding(end = animatedBadgeEndPadding),
                    targetState = data.showQR,
                    animationSpec = tween(durationMillis = 300),
                    label = "icon_crossfade"
                ) { showQR ->
                    Image(
                        modifier = Modifier
                            .size(animatedBadgeSize)
                            .background(
                                shape = CircleShape,
                                color = LocalColors.current.accent
                            )
                            .padding(animatedBadgeInnerPadding),
                        painter = painterResource(id = when(showQR){
                            true -> R.drawable.ic_user_filled_custom
                            false -> R.drawable.ic_qr_code
                        }),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.Black)
                    )
                }
            }

            Spacer(modifier = Modifier.height(LocalDimensions.current.smallSpacing))

            // title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.xxxsSpacing)

            ) {
                Text(
                    text = data.name,
                    style = LocalType.current.h5
                )

                if(data.isPro) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_pro_badge),
                        contentScale = ContentScale.FillHeight,
                        contentDescription = NonTranslatableStringConstants.APP_PRO,
                    )
                }
            }

            if(!data.subtitle.isNullOrEmpty()){
                Spacer(modifier = Modifier.height(LocalDimensions.current.xxxsSpacing))
                Text(
                    text = data.subtitle,
                    style = LocalType.current.small.copy(color = LocalColors.current.textSecondary)
                )
            }

            Spacer(modifier = Modifier.height(LocalDimensions.current.smallSpacing))

            // account ID
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ){
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(color = LocalColors.current.borders)
                )

                Text(
                    modifier = Modifier
                        .border()
                        .padding(
                            horizontal = LocalDimensions.current.smallSpacing,
                            vertical = LocalDimensions.current.xxxsSpacing
                        )
                        ,
                    text = if(data.isBlinded) stringResource(R.string.blindedId) else stringResource(R.string.accountId),
                    style = LocalType.current.small.copy(color = LocalColors.current.textSecondary)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(color = LocalColors.current.borders)
                )
            }

            Spacer(modifier = Modifier.height(LocalDimensions.current.xsSpacing))

            Row {
                var accountIdModifier = Modifier.qaTag(R.string.qa_conversation_settings_account_id).weight(1f)
                if(!data.tooltipText.isNullOrEmpty()){
                    accountIdModifier = accountIdModifier.padding(horizontal = LocalDimensions.current.xsSpacing)
                }

                Text(
                    modifier = accountIdModifier,
                    text = data.address,
                    textAlign = TextAlign.Center,
                    style = LocalType.current.base.monospace(),
                    color = LocalColors.current.text
                )

                if(!data.tooltipText.isNullOrEmpty()){
                    var displayTooltip by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .size(LocalDimensions.current.iconXSmall)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_circle_help),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(LocalColors.current.text),
                            modifier = Modifier
                                .size(LocalDimensions.current.iconXSmall)
                                .clickable { displayTooltip = true }
                                .qaTag("Tooltip")
                        )

                        if (displayTooltip) {
                            SimplePopup(
                                onDismiss = { displayTooltip = false }
                            ) {
                                Text(
                                    text = data.tooltipText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .padding(
                                            horizontal = LocalDimensions.current.smallSpacing,
                                            vertical = LocalDimensions.current.xxsSpacing
                                        )
                                        .qaTag("Tooltip info"),
                                    style = LocalType.current.small
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(LocalDimensions.current.smallSpacing))

            // buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ){
                var buttonModifier: Modifier = Modifier
                if(data.isBlinded){ // this means there is no copy button so the message button should be full width
                    buttonModifier = buttonModifier.widthIn(LocalDimensions.current.minButtonWidth)
                } else { // the copy button will be there so allow for a max stretch with weight = 1f
                    buttonModifier = buttonModifier.weight(1f)
                }

                SlimAccentOutlineButton(
                    modifier = buttonModifier,
                    text = stringResource(R.string.message),
                    enabled = data.enableMessage,
                    onClick = {
                        sendCommand(UserProfileModalCommands.HideUserProfileModal)
                    }
                )

                if(!data.isBlinded){
                    Spacer(modifier = Modifier.width(LocalDimensions.current.xsSpacing))
                    SlimOutlineCopyButton(
                        Modifier.weight(1f),
                        color = LocalColors.current.accentText,
                        onClick = {
                            sendCommand(UserProfileModalCommands.CopyAccountId)
                        }
                    )
                }
            }
        }
    )


    // the pro CTA that comes with UPM
    if(data.showProCTA){
        AnimatedSessionProCTA(
            heroImageBg = R.drawable.cta_hero_generic_bg,
            heroImageAnimatedFg = R.drawable.cta_hero_generic_fg,
            text = stringResource(R.string.proUserProfileModalCallToAction),
            features = listOf(
                CTAFeature.Icon(stringResource(R.string.proFeatureListLargerGroups)),
                CTAFeature.Icon(stringResource(R.string.proFeatureListLongerMessages)),
                CTAFeature.RainbowIcon(stringResource(R.string.proFeatureListLoadsMore)),
            ),
            onUpgrade = {
                sendCommand(UserProfileModalCommands.HideSessionProCTA)
                //todo PRO go to screen once it exists
            },
            onCancel = {
                sendCommand(UserProfileModalCommands.HideSessionProCTA)
            }
        )
    }
}

data class UserProfileModalData(
    val name: String,
    val subtitle: String?,
    val isPro: Boolean,
    val address: String,
    val isBlinded: Boolean,
    val tooltipText: String?,
    val enableMessage: Boolean,
    val expandedAvatar: Boolean,
    val showQR: Boolean,
    val avatarUIData: AvatarUIData,
    val showProCTA: Boolean
)

sealed interface UserProfileModalCommands {
    object HideUserProfileModal: UserProfileModalCommands
    object HideSessionProCTA: UserProfileModalCommands
    object CopyAccountId: UserProfileModalCommands
    object ToggleAvatarExpand: UserProfileModalCommands
    object ToggleQR: UserProfileModalCommands
}

@Preview
@Composable
private fun PreviewUPM(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        var data by remember {
            mutableStateOf(
                UserProfileModalData(
                    name = "Atreyu",
                    subtitle = "(Neverending)",
                    isPro = false,
                    isBlinded = false,
                    tooltipText = "Some tooltip",
                    address = "053d30141d0d35d9c4b30a8f8880f8464e221ee71a8aff9f0dcefb1e60145cea5144",
                    enableMessage = false,
                    expandedAvatar = false,
                    showQR = false,
                    showProCTA = false,
                    avatarUIData = AvatarUIData(
                        listOf(
                            AvatarUIElement(
                                name = "TO",
                                color = primaryRed
                            )
                        )
                    )
                )
            )
        }

        UserProfileModal(
            data = data,
            sendCommand = { command ->
                when(command){
                    UserProfileModalCommands.HideUserProfileModal -> {}
                    UserProfileModalCommands.HideSessionProCTA -> {}
                    UserProfileModalCommands.ToggleQR -> {
                        data = data.copy(showQR = !data.showQR)
                    }
                    UserProfileModalCommands.ToggleAvatarExpand -> {
                        data = data.copy(expandedAvatar = !data.expandedAvatar)
                    }
                    else -> {}

                }
            }
        )
    }
}

@Preview
@Composable
private fun PreviewUPMQR(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        var data by remember {
            mutableStateOf(
            UserProfileModalData(
                    name = "Atreyu",
                    subtitle = "(Neverending)",
                    isPro = false,
                    isBlinded = true,
                    tooltipText = "Some tooltip",
                    address = "053d30141d0d35d9c4b30a8f8880f8464e221ee71a8aff9f0dcefb1e60145cea5144",
                    enableMessage = true,
                    expandedAvatar = false,
                    showQR = true,
                    showProCTA = false,
                    avatarUIData = AvatarUIData(
                        listOf(
                            AvatarUIElement(
                                name = "TO",
                                color = primaryRed
                            )
                        )
                    )
                )
            )
        }

        UserProfileModal(
            data = data,
            sendCommand = { command ->
                when(command){
                    UserProfileModalCommands.HideUserProfileModal -> {}
                    UserProfileModalCommands.HideSessionProCTA -> {}
                    UserProfileModalCommands.ToggleQR -> {
                        data = data.copy(showQR = !data.showQR)
                    }
                    UserProfileModalCommands.ToggleAvatarExpand -> {
                        data = data.copy(expandedAvatar = !data.expandedAvatar)
                    }
                    else -> {}

                }
            }
        )
    }
}

@Preview
@Composable
private fun PreviewUPMCTA(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        UserProfileModal(
            data = UserProfileModalData(
                name = "Atreyu",
                subtitle = "(Neverending)",
                isPro = false,
                isBlinded = true,
                tooltipText = "Some tooltip",
                address = "158342146b...c6ed734na5",
                enableMessage = false,
                expandedAvatar = true,
                showQR = false,
                showProCTA = true,
                avatarUIData = AvatarUIData(
                    listOf(
                        AvatarUIElement(
                            name = "TO",
                            color = primaryRed
                        )
                    )
                )
            ),
            sendCommand = {}
        )
    }
}