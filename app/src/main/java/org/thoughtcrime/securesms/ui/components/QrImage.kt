package org.thoughtcrime.securesms.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.LocalLightCell
import org.thoughtcrime.securesms.ui.LocalOnLightCell
import org.thoughtcrime.securesms.util.QRCodeUtilities

@Composable
fun QrImageCard(
    string: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: Int = R.drawable.session_shield
) {
    Card(
        backgroundColor = LocalLightCell.current,
        elevation = 0.dp,
        modifier = modifier
    ) { QrImage(string, contentDescription, icon) }
}

@Composable
fun QrImage(string: String, contentDescription: String, icon: Int = R.drawable.session_shield) {
    var bitmap: Bitmap? by remember {
        mutableStateOf(null)
    }

    val scope = rememberCoroutineScope()
    LaunchedEffect(string) {
        scope.launch(Dispatchers.IO) {
            bitmap = QRCodeUtilities.encode(string, 400)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        AnimatedVisibility(
            visible = bitmap != null,
            enter = scaleIn()
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentDescription = contentDescription,
                    colorFilter = ColorFilter.tint(LocalOnLightCell.current)
                )
            }

        }

        Icon(
            painter = painterResource(id = icon),
            contentDescription = "",
            tint = LocalOnLightCell.current,
            modifier = Modifier
                .align(Alignment.Center)
                .width(46.dp)
                .height(56.dp)
                .background(color = LocalLightCell.current)
                .padding(horizontal = 3.dp, vertical = 1.dp)
        )
    }
}