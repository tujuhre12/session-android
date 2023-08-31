package org.thoughtcrime.securesms.conversation.expiration

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityExpirationSettingsBinding
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.CellNoMargin
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.LocalExtraColors
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.ThemeResPreviewParameterProvider
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import javax.inject.Inject
import kotlin.math.min

@AndroidEntryPoint
class ExpirationSettingsActivity: PassphraseRequiredActionBarActivity() {

    private lateinit var binding : ActivityExpirationSettingsBinding

    @Inject lateinit var recipientDb: RecipientDatabase
    @Inject lateinit var threadDb: ThreadDatabase
    @Inject lateinit var viewModelFactory: ExpirationSettingsViewModel.AssistedFactory

    private val threadId: Long by lazy {
        intent.getLongExtra(THREAD_ID, -1)
    }

    private val viewModel: ExpirationSettingsViewModel by viewModels {
        viewModelFactory.create(threadId)
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        binding = ActivityExpirationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUpToolbar()

        binding.container.setContent { DisappearingMessagesScreen() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.event.collect {
                    when (it) {
                        Event.SUCCESS -> finish()
                        Event.FAIL -> showToast(getString(R.string.ExpirationSettingsActivity_settings_not_updated))
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    supportActionBar?.subtitle = state.subtitle(this@ExpirationSettingsActivity)
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun setUpToolbar() {
        setSupportActionBar(binding.toolbar)
        val actionBar = supportActionBar ?: return
        actionBar.title = getString(R.string.activity_expiration_settings_title)
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setHomeButtonEnabled(true)
    }

    companion object {
        const val THREAD_ID = "thread_id"
    }

    @Composable
    fun DisappearingMessagesScreen() {
        val uiState by viewModel.uiState.collectAsState(UiState())
        AppTheme {
            DisappearingMessages(uiState)
        }
    }
}

@Composable
fun DisappearingMessages(
    state: UiState,
    modifier: Modifier = Modifier
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
                state.cards.filter { it.options.isNotEmpty() }.forEach {
                    OptionsCard(it)
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
            onClick = state.callbacks::onSetClick
        )
    }
}

fun Modifier.fadingEdges(
    scrollState: ScrollState,
    topEdgeHeight: Dp = 0.dp,
    bottomEdgeHeight: Dp = 20.dp
): Modifier = this.then(
    Modifier
        // adding layer fixes issue with blending gradient and content
        .graphicsLayer { alpha = 0.99F }
        .drawWithContent {
            drawContent()

            val topColors = listOf(Color.Transparent, Color.Black)
            val topStartY = scrollState.value.toFloat()
            val topGradientHeight = min(topEdgeHeight.toPx(), topStartY)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = topColors,
                    startY = topStartY,
                    endY = topStartY + topGradientHeight
                ),
                blendMode = BlendMode.DstIn
            )

            val bottomColors = listOf(Color.Black, Color.Transparent)
            val bottomEndY = size.height - scrollState.maxValue + scrollState.value
            val bottomGradientHeight =
                min(bottomEdgeHeight.toPx(), scrollState.maxValue.toFloat() - scrollState.value)
            if (bottomGradientHeight != 0f) drawRect(
                brush = Brush.verticalGradient(
                    colors = bottomColors,
                    startY = bottomEndY - bottomGradientHeight,
                    endY = bottomEndY
                ),
                blendMode = BlendMode.DstIn
            )
        }
)

@Composable
fun OptionsCard(card: CardModel) {
    Text(text = card.title())
    CellNoMargin {
        LazyColumn(
            modifier = Modifier.heightIn(max = 5000.dp)
        ) {
            itemsIndexed(card.options) { i, it ->
                if (i != 0) Divider()
                TitledRadioButton(it)
            }
        }
    }
}

@Composable
fun TitledRadioButton(option: OptionModel) {
    Row(modifier = Modifier
        .heightIn(min = 60.dp)
        .padding(horizontal = 34.dp)) {
        Column(modifier = Modifier
            .weight(1f)
            .align(Alignment.CenterVertically)) {
            Column {
                Text(
                    text = option.title(),
                    fontSize = 16.sp,
                    modifier = Modifier.alpha(if (option.enabled) 1f else 0.5f)
                )
                option.subtitle?.let {
                    Text(
                        text = it(),
                        fontSize = 11.sp,
                        modifier = Modifier.alpha(if (option.enabled) 1f else 0.5f)
                    )
                }
            }
        }
        RadioButton(
            selected = option.selected,
            enabled = option.enabled,
            onClick = option.onClick,
            modifier = Modifier
                .height(26.dp)
                .align(Alignment.CenterVertically)
        )
    }
}

@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        modifier = modifier.size(108.dp, 34.dp),
        onClick = onClick,
        border = BorderStroke(1.dp, LocalExtraColors.current.prominentButtonColor),
        shape = RoundedCornerShape(50), // = 50% percent
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = LocalExtraColors.current.prominentButtonColor,
            backgroundColor = MaterialTheme.colors.background
        )
    ){
        Text(text = text)
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
