package org.thoughtcrime.securesms.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
            onValueChange = { onChange(it) },
            placeholder = { Text(placeholder) },
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
                modifier = Modifier.padding(top = 12.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.baseBold,
                color = MaterialTheme.colors.error
            )
        }
    }
}
