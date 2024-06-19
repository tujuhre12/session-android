package org.thoughtcrime.securesms.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.baseBold
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.color.outlinedTextFieldColors

@Composable
fun SessionOutlinedTextField(
        text: String,
        placeholder: String,
        modifier: Modifier = Modifier,
        onChange: (String) -> Unit,
        onContinue: () -> Unit,
        error: String? = null
) {
    Column(modifier = modifier.animateContentSize()) {
        OutlinedTextField(
            value = text,
            modifier = Modifier.fillMaxWidth(),
            textStyle = base,
            onValueChange = { onChange(it) },
            placeholder = {
                Text(
                    placeholder,
                    style = base
                )
            },
            colors = LocalColors.current.outlinedTextFieldColors(error != null),
            singleLine = true,
            keyboardActions = KeyboardActions(
                onDone = { onContinue() },
                onGo = { onContinue() },
                onSearch = { onContinue() },
                onSend = { onContinue() },
            ),
            isError = error != null,
            shape = MaterialTheme.shapes.small
        )
        error?.let {
            Spacer(modifier = Modifier.height(LocalDimensions.current.xsItemSpacing))
            Text(
                it,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = baseBold,
                color = LocalColors.current.danger
            )
        }
    }
}

@Composable
fun AnnotatedTextWithIcon(
    text: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = base,
    color: Color = Color.Unspecified,
    iconSize: TextUnit = 12.sp
) {
    val myId = "inlineContent"
    val annotated = buildAnnotatedString {
        append(text)
        appendInlineContent(myId, "[icon]")
    }

    val inlineContent = mapOf(
        Pair(
            myId,
            InlineTextContent(
                Placeholder(
                    width = iconSize,
                    height = iconSize,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                )
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.padding(1.dp),
                    tint = color
                )
            }
        )
    )

    Text(
        text = annotated,
        modifier = modifier.fillMaxWidth(),
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        inlineContent = inlineContent
    )
}