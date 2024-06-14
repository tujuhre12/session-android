package org.thoughtcrime.securesms.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.Icon
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
import org.thoughtcrime.securesms.ui.LocalColors
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.LocalType
import org.thoughtcrime.securesms.ui.bold
import org.thoughtcrime.securesms.ui.outlinedTextFieldColors

@Composable
fun SessionOutlinedTextField(
        text: String,
        placeholder: String,
        modifier: Modifier = Modifier,
        onChange: (String) -> Unit,
        onContinue: () -> Unit,
        error: String? = null
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = text,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalType.current.base,
            onValueChange = { onChange(it) },
            placeholder = {
                Text(
                    placeholder,
                    style = LocalType.current.base
                )
            },
            colors = outlinedTextFieldColors(error != null),
            singleLine = true,
            keyboardActions = KeyboardActions(
                onDone = { onContinue() },
                onGo = { onContinue() },
                onSearch = { onContinue() },
                onSend = { onContinue() },
            ),
            isError = error != null,
            shape = RoundedCornerShape(12.dp)
        )
        error?.let {
            Text(
                it,
                modifier = Modifier.padding(top = LocalDimensions.current.marginExtraExtraSmall),
                textAlign = TextAlign.Center,
                style = LocalType.current.base.bold(),
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
    style: TextStyle = LocalType.current.base,
    iconTint: Color = Color.Unspecified,
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
                    placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline
                )
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.padding(1.dp),
                    tint = iconTint
                )
            }
        )
    )

    Text(
        text = annotated,
        modifier = modifier.fillMaxWidth(),
        style = style,
        inlineContent = inlineContent
    )
}