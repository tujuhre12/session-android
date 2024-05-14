package org.thoughtcrime.securesms.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
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
fun QrImage(
    string: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: Int = R.drawable.session_shield
) {
    var bitmap: Bitmap? by remember {
        mutableStateOf(null)
    }

    val scope = rememberCoroutineScope()
    LaunchedEffect(string) {
        scope.launch(Dispatchers.IO) {
            val c = 100
            val w = c * 2
            bitmap = QRCodeUtilities.encode(string, w).also {
                val hw = 30
                for (y in c - hw until c + hw) {
                    for (x in c - hw until c + hw) {
                        it.setPixel(x, y, 0x00000000)
                    }
                }
            }
        }
    }

    @Composable
    fun content(modifier: Modifier = Modifier) {
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
                    .size(60.dp)
            )
        }
    }

    if (MaterialTheme.colors.isLight) {
        content(modifier)
    } else {
        Card(
            backgroundColor = LocalLightCell.current,
            elevation = 0.dp,
            modifier = modifier
        ) { content() }
    }
}
