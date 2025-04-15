package org.thoughtcrime.securesms.util

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import network.loki.messenger.R
import org.session.libsession.avatars.ContactPhoto

class AvatarUtils {
}

data class AvatarUIData(
    val name: String? = null,
    val color: Color? = null,
    val contactPhoto: ContactPhoto? = null,
)

sealed class AvatarBadge(@DrawableRes val icon: Int){
    data object None: AvatarBadge(0)
    data object Admin: AvatarBadge(R.drawable.ic_crown_custom)
    data class Custom(@DrawableRes val iconRes: Int): AvatarBadge(iconRes)
}