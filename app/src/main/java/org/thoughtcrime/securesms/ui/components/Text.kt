package org.thoughtcrime.securesms.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.baseBold
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
            textStyle = MaterialTheme.typography.base,
            onValueChange = { onChange(it) },
            placeholder = {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.base
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
                style = MaterialTheme.typography.baseBold,
                color = MaterialTheme.colors.error
            )
        }
    }
}

@Composable
fun AnnotatedTextWithIcon(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
    iconDescription: String = "",
    iconSize: TextUnit = 12.sp,
    textWidth: Dp = 100.dp
) {
    val myId = "inlineContent"
    val annotatedText = buildAnnotatedString {
        append(text)
        appendInlineContent(myId, "[icon]")
    }

    val inlineContent = mapOf(
        myId to Placeholder(
            width = iconSize,
            height = iconSize,
            placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline
        ).let { InlineTextContent(it) { Icon(icon, iconDescription, tint = iconTint) } }
    )

    Text(
        text = annotatedText,
        modifier = modifier.width(textWidth),
        inlineContent = inlineContent
    )
}
