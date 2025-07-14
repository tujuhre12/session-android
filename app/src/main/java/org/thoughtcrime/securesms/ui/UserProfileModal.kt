package org.thoughtcrime.securesms.ui

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
import androidx.compose.runtime.remember
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
            Box(
                modifier = Modifier
                    .size(LocalDimensions.current.iconXXLarge)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null // the ripple doesn't look nice as a square with the plus icon on top too
                    ) {
                       //todo UPM implement
                    },
                contentAlignment = Alignment.Center
            ) {
                // the image content will depend on state type
                Avatar(
                    size = LocalDimensions.current.iconXXLarge,
                    data = data.avatarUIData
                )

                // qr/avatar button switch
                Image(
                    modifier = Modifier
                        .size(LocalDimensions.current.spacing)
                        .background(
                            shape = CircleShape,
                            color = LocalColors.current.accent
                        )
                        .padding(LocalDimensions.current.xxxsSpacing)
                        .align(Alignment.TopEnd)
                    ,
                    painter = painterResource(id = when(data.headerState){
                        UserProfileModalData.HeaderState.Avatar -> R.drawable.ic_qr_code
                        UserProfileModalData.HeaderState.QR -> R.drawable.ic_user_filled_custom
                    }),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.Black)
                )
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
                    modifier = Modifier.weight(1f)
                        .height(1.dp)
                        .background(color = LocalColors.current.borders)
                )

                Text(
                    modifier = Modifier.border()
                        .padding(
                            horizontal = LocalDimensions.current.smallSpacing,
                            vertical = LocalDimensions.current.xxxsSpacing
                        )
                        ,
                    text = if(data.isBlinded) stringResource(R.string.blindedId) else stringResource(R.string.accountId),
                    style = LocalType.current.small.copy(color = LocalColors.current.textSecondary)
                )

                Box(
                    modifier = Modifier.weight(1f)
                        .height(1.dp)
                        .background(color = LocalColors.current.borders)
                )
            }

            Spacer(modifier = Modifier.height(LocalDimensions.current.xsSpacing))

            Row {
                Text(
                    modifier = Modifier.qaTag(R.string.qa_conversation_settings_account_id),
                    text = data.address,
                    textAlign = TextAlign.Center,
                    style = LocalType.current.base.monospace(),
                    color = LocalColors.current.text
                )

                if(!data.tooltipText.isNullOrEmpty()){

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
    val headerState: HeaderState,
    val avatarUIData: AvatarUIData,
    val showProCTA: Boolean
){
    sealed interface HeaderState {
        object Avatar: HeaderState
        object QR: HeaderState
    }
}

sealed interface UserProfileModalCommands {
    object HideUserProfileModal: UserProfileModalCommands
    object HideSessionProCTA: UserProfileModalCommands
    object CopyAccountId: UserProfileModalCommands
}

@Preview
@Composable
private fun PreviewUPM(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: ThemeColors
) {
    PreviewTheme(colors) {
        UserProfileModal(
            data = UserProfileModalData(
                name = "Atreyu",
                subtitle = null,
                isPro = true,
                isBlinded = false,
                tooltipText = null,
                address = "053d30141d0d35d9c4b30a8f8880f8464e221ee71a8aff9f0dcefb1e60145cea5144",
                enableMessage = true,
                headerState = UserProfileModalData.HeaderState.Avatar,
                showProCTA = false,
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

@Preview
@Composable
private fun PreviewUPMQR(
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
                address = "053d30141d0d35d9c4b30a8f8880f8464e221ee71a8aff9f0dcefb1e60145cea5144",
                enableMessage = false,
                headerState = UserProfileModalData.HeaderState.QR,
                showProCTA = false,
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
                headerState = UserProfileModalData.HeaderState.QR,
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