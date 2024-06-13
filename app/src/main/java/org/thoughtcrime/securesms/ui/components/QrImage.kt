package org.thoughtcrime.securesms.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.LocalColors
import org.thoughtcrime.securesms.ui.LocalOnLightCell
import org.thoughtcrime.securesms.util.QRCodeUtilities

@Composable
fun QrImage(
    string: String,
    modifier: Modifier = Modifier,
    icon: Int = R.drawable.session_shield
) {
    var bitmap: Bitmap? by remember {
        mutableStateOf(null)
    }

    val dark = LocalColors.current.onBackgroundLight.toArgb()

    val scope = rememberCoroutineScope()
    LaunchedEffect(string) {
        scope.launch(Dispatchers.IO) {
            bitmap = (300..500 step 100).firstNotNullOf {
                runCatching { QRCodeUtilities.encode(string, it, dark = dark) }.getOrNull()
            }
        }
    }

    if (LocalColors.current.isLight) {
        Content(bitmap, icon, modifier = modifier, backgroundColor = LocalColors.current.backgroundLight)
    } else {
        Card(
            backgroundColor = LocalColors.current.backgroundLight,
            elevation = 0.dp,
            modifier = modifier
        ) { Content(bitmap, icon, modifier = Modifier.padding(16.dp), backgroundColor = LocalColors.current.backgroundLight) }
    }
}

@Composable
private fun Content(
    bitmap: Bitmap?,
    icon: Int,
    modifier: Modifier = Modifier,
    qrColor: Color = LocalOnLightCell.current,
    backgroundColor: Color,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        AnimatedVisibility(
            visible = bitmap != null,
            enter = fadeIn(),
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    colorFilter = ColorFilter.tint(qrColor),
                    // Use FilterQuality.None to keep QR edges sharp
                    filterQuality = FilterQuality.None
                )
            }
        }

        Icon(
            painter = painterResource(id = icon),
            contentDescription = "",
            tint = LocalOnLightCell.current,
            modifier = Modifier
                .size(62.dp)
                .align(Alignment.Center)
                .background(color = backgroundColor)
                .size(66.dp)
        )
    }
}