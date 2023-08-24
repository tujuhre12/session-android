package org.thoughtcrime.securesms.conversation.expiration

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityExpirationSettingsBinding
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.CellNoMargin
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.ThemeResPreviewParameterProvider
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import javax.inject.Inject

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


//    override fun onSaveInstanceState(outState: Bundle) {
//        super.onSaveInstanceState(outState)
//        val scrollParcelArray = SparseArray<Parcelable>()
//        binding.scrollView.saveHierarchyState(scrollParcelArray)
//        outState.putSparseParcelableArray(SCROLL_PARCEL, scrollParcelArray)
//    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        binding = ActivityExpirationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUpToolbar()

        binding.container.setContent { DisappearingMessagesScreen() }

//        savedInstanceState?.let { bundle ->
//            val scrollStateParcel = bundle.getSparseParcelableArray<Parcelable>(SCROLL_PARCEL)
//            if (scrollStateParcel != null) {
//                binding.scrollView.restoreHierarchyState(scrollStateParcel)
//            }
//        }

//        val deleteTypeOptions = viewModel.getDeleteOptions()

//        binding.buttonSet.setOnClickListener {
//            viewModel.onSetClick()
//        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
//                    actionBar?.subtitle = if (state.selectedExpirationType.value is ExpiryMode.AfterSend) {
//                        getString(R.string.activity_expiration_settings_subtitle_sent)
//                    } else {
//                        getString(R.string.activity_expiration_settings_subtitle)
//                    }

//                    binding.textViewDeleteType.isVisible = state.showExpirationTypeSelector
//                    binding.layoutDeleteTypes.isVisible = state.showExpirationTypeSelector
//                    binding.textViewFooter.isVisible = state.recipient?.isClosedGroupRecipient == true
//                    binding.textViewFooter.text = HtmlCompat.fromHtml(getString(R.string.activity_expiration_settings_group_footer), HtmlCompat.FROM_HTML_MODE_COMPACT)

                    when (state.settingsSaved) {
                        true -> {
                            ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(this@ExpirationSettingsActivity)
                            finish()
                        }
                        false -> showToast(getString(R.string.ExpirationSettingsActivity_settings_not_updated))
                        else -> {}
                    }

//                    val position = deleteTypeOptions.indexOfFirst { it.value == state.selectedExpirationType }
//                    deleteTypeOptionAdapter.setSelectedPosition(max(0, position))
                }
            }
        }
//        lifecycleScope.launch {
//            repeatOnLifecycle(Lifecycle.State.STARTED) {
//                viewModel.selectedExpirationType.collect { type ->
//                    val position = deleteTypeOptions.indexOfFirst { it.value == type }
//                    deleteTypeOptionAdapter.setSelectedPosition(max(0, position))
//                }
//            }
//        }
//        lifecycleScope.launch {
//            repeatOnLifecycle(Lifecycle.State.STARTED) {
//                viewModel.selectedExpirationTimer.collect { option ->
//                    val position =
//                        viewModel.expirationTimerOptions.value.indexOfFirst { it.value == option?.value }
//                    timerOptionAdapter.setSelectedPosition(max(0, position))
//                }
//            }
//        }
//        lifecycleScope.launch {
//            repeatOnLifecycle(Lifecycle.State.STARTED) {
//                viewModel.expirationTimerOptions.collect { options ->
//                    binding.textViewTimer.isVisible =
//                        options.isNotEmpty() && viewModel.uiState.value.showExpirationTypeSelector
//                    binding.layoutTimer.isVisible = options.isNotEmpty()
//                    timerOptionAdapter.submitList(options)
//                }
//            }
//        }
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
        private const val SCROLL_PARCEL = "scroll_parcel"
        const val THREAD_ID = "thread_id"
    }

    @Composable
    fun DisappearingMessagesScreen() {
        val uiState by viewModel.uiState.collectAsState(UiState())
        AppTheme {
            DisappearingMessages(uiState, onSetClick = viewModel::onSetClick)
        }
    }
}

@Composable
fun DisappearingMessages(
    state: UiState,
    modifier: Modifier = Modifier,
    onSetClick: () -> Unit = {}
) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.cards.filter { it.options.isNotEmpty() }.forEach { OptionsCard(it) }
            }

            Gradient(100.dp, modifier = Modifier.align(Alignment.BottomCenter))
        }

        OutlineButton(
            stringResource(R.string.expiration_settings_set_button_title),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 20.dp),
            onClick = onSetClick
        )
    }
}

@Composable
fun OptionsCard(card: CardModel) {
    Text(text = card.title())
    CellNoMargin {
        LazyColumn(
            modifier = Modifier.heightIn(max = 5000.dp)
        ) {
            items(card.options) {
                TitledRadioButton(it)
            }
        }
    }
}

@Composable
fun Gradient(height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, MaterialTheme.colors.primary),
                    startY = 0f,
                    endY = height.value
                )
            )
    )
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
                Text(text = option.title())
                option.subtitle?.let { Text(text = it()) }
            }
        }
        RadioButton(
            selected = option.selected,
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
        border = BorderStroke(1.dp, MaterialTheme.colors.secondary),
        shape = RoundedCornerShape(50), // = 50% percent
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.secondary)
    ){
        Text(text = text)
    }
}

@Preview
@Composable
fun PreviewMessageDetails(
    @PreviewParameter(ThemeResPreviewParameterProvider::class) themeResId: Int
) {
    PreviewTheme(themeResId) {
        DisappearingMessages(
            UiState(
                cards = listOf(
                    CardModel(GetString(R.string.activity_expiration_settings_delete_type), typeOptions()),
                    CardModel(GetString(R.string.activity_expiration_settings_timer), timeOptions())
                )
            ),
            modifier = Modifier.size(400.dp, 600.dp)
        )
    }
}

fun typeOptions() = listOf(
    OptionModel(GetString(R.string.expiration_off)),
    OptionModel(GetString(R.string.expiration_type_disappear_legacy)),
    OptionModel(GetString(R.string.expiration_type_disappear_after_read)),
    OptionModel(GetString(R.string.expiration_type_disappear_after_send))
)

fun timeOptions() = listOf(
    OptionModel(GetString("1 Minute")),
    OptionModel(GetString("5 Minutes")),
    OptionModel(GetString("1 Week")),
    OptionModel(GetString("2 Weeks")),
)