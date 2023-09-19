package org.thoughtcrime.securesms.conversation.expiration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.thoughtcrime.securesms.ui.CellNoMargin
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.OutlineButton
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.ThemeResPreviewParameterProvider
import org.thoughtcrime.securesms.ui.TitledRadioButton
import org.thoughtcrime.securesms.ui.fadingEdges

@Composable
fun DisappearingMessages(
    state: UiState,
    modifier: Modifier = Modifier,
    callbacks: Callbacks = NoOpCallbacks
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 20.dp)
                    .verticalScroll(scrollState)
                    .fadingEdges(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.cards.forEach {
                    OptionsCard(it, callbacks)
                }

                if (state.showGroupFooter) Text(text = stringResource(R.string.activity_expiration_settings_group_footer),
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight(400),
                        color = Color(0xFFA1A2A1),
                        textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth())
            }
        }

        OutlineButton(
            stringResource(R.string.expiration_settings_set_button_title),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 20.dp),
            onClick = callbacks::onSetClick
        )
    }
}

@Composable
fun OptionsCard(card: CardModel, callbacks: Callbacks) {
    Text(text = card.title())
    CellNoMargin {
        LazyColumn(
            modifier = Modifier.heightIn(max = 5000.dp)
        ) {
            itemsIndexed(card.options) { i, it ->
                if (i != 0) Divider()
                TitledRadioButton(it) { callbacks.setMode(it.value) }
            }
        }
    }
}

@Preview(widthDp = 450, heightDp = 700)
@Composable
fun PreviewStates(
    @PreviewParameter(StatePreviewParameterProvider::class) state: State
) {
    PreviewTheme(R.style.Classic_Dark) {
        DisappearingMessages(
            UiState(state)
        )
    }
}

class StatePreviewParameterProvider : PreviewParameterProvider<State> {
    override val values = newConfigValues.filter { it.expiryType != ExpiryType.LEGACY } + newConfigValues.map { it.copy(isNewConfigEnabled = false) }

    private val newConfigValues get() = sequenceOf(
        // new 1-1
        State(expiryMode = ExpiryMode.NONE),
        State(expiryMode = ExpiryMode.Legacy(43200)),
        State(expiryMode = ExpiryMode.AfterRead(300)),
        State(expiryMode = ExpiryMode.AfterSend(43200)),
        // new group non-admin
        State(isGroup = true, isSelfAdmin = false),
        State(isGroup = true, isSelfAdmin = false, expiryMode = ExpiryMode.Legacy(43200)),
        State(isGroup = true, isSelfAdmin = false, expiryMode = ExpiryMode.AfterSend(43200)),
        // new group admin
        State(isGroup = true),
        State(isGroup = true, expiryMode = ExpiryMode.Legacy(43200)),
        State(isGroup = true, expiryMode = ExpiryMode.AfterSend(43200)),
        // new note-to-self
        State(isNoteToSelf = true),
    )
}


@Preview
@Composable
fun PreviewThemes(
    @PreviewParameter(ThemeResPreviewParameterProvider::class) themeResId: Int
) {
    PreviewTheme(themeResId) {
        DisappearingMessages(
            UiState(State(expiryMode = ExpiryMode.AfterSend(43200))),
            modifier = Modifier.size(400.dp, 600.dp)
        )
    }
}
