package org.thoughtcrime.securesms.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonColors
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.accompanist.pager.HorizontalPagerIndicator

@Composable
fun ItemButton(
    text: String,
    @DrawableRes icon: Int,
    colors: ButtonColors = transparentButtonColors(),
    onClick: () -> Unit
) {
    TextButton(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        colors = colors,
        onClick = onClick,
        shape = RectangleShape,
    ) {
        Box(modifier = Modifier
            .width(80.dp)
            .fillMaxHeight()) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = "",
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Text(text, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun Cell(content: @Composable () -> Unit) {
    CellWithPaddingAndMargin(0.dp) { content() }
}
@Composable
fun CellNoMargin(content: @Composable () -> Unit) {
    CellWithPaddingAndMargin(0.dp, 0.dp) { content() }
}

@Composable
fun CellWithPaddingAndMargin(
    padding: Dp = 24.dp,
    margin: Dp = 32.dp,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = 0.dp,
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .padding(horizontal = margin),
        backgroundColor = LocalExtraColors.current.settingsBackground,
        // probably wrong
        contentColor = MaterialTheme.colors.onSurface
    ) { Box(Modifier.padding(padding)) { content() } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionHorizontalPagerIndicator(modifier: Modifier, pagerState: PagerState, pageCount: Int) {
    Card(shape = RoundedCornerShape(50.dp),
        backgroundColor = Color.Black.copy(alpha = 0.4f),
        modifier = Modifier.padding(8.dp).then(modifier)) {
        Box(modifier = Modifier.padding(8.dp)) {
            HorizontalPagerIndicator(pagerState = pagerState, pageCount = pageCount)
        }
    }
}
