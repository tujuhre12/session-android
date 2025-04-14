package org.thoughtcrime.securesms.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.session.libsession.avatars.ContactPhoto
import org.thoughtcrime.securesms.ui.theme.LocalDimensions


@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    name: String? = null,
    color: Color? = null,
    contactPhoto: ContactPhoto? = null,
    isAdmin: Boolean = false
){
    Box(
        modifier = modifier
    ) {
        // badge
        if (isAdmin) {
            Image(
                painter = painterResource(id = R.drawable.ic_crown_custom),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(1.dp, 1.dp) // used to make up for transparent padding in icon
                    .size(LocalDimensions.current.badgeSize)
            )
        }
    }
}

@Composable
private fun AvatarElement(
    modifier: Modifier = Modifier,
    name: String? = null,
    color: Color? = null,
    contactPhoto: ContactPhoto? = null,
){

}
