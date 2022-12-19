package org.thoughtcrime.securesms.conversation.expiration

import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import network.loki.messenger.BuildConfig
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityExpirationSettingsBinding
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsignal.protos.SignalServiceProtos.Content.ExpirationType
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.preferences.RadioOption
import org.thoughtcrime.securesms.preferences.RadioOptionAdapter
import javax.inject.Inject
import kotlin.math.max

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
        val afterReadOptions = resources.getIntArray(R.array.read_expiration_time_values).map(Int::toString)
            .zip(resources.getStringArray(R.array.read_expiration_time_names)) { value, name -> RadioOption(value, name)}
        val afterSendOptions = resources.getIntArray(R.array.send_expiration_time_values).map(Int::toString)
            .zip(resources.getStringArray(R.array.send_expiration_time_names)) { value, name -> RadioOption(value, name)}
        viewModelFactory.create(threadId, mayAddTestExpiryOption(afterReadOptions), mayAddTestExpiryOption(afterSendOptions))
    }

    private fun mayAddTestExpiryOption(expiryOptions: List<RadioOption>): List<RadioOption> {
        return if (BuildConfig.DEBUG) {
            val options = expiryOptions.toMutableList()
            options.add(1, RadioOption("60", "1 Minute (for testing purposes)"))
            options
        } else expiryOptions
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val scrollParcelArray = SparseArray<Parcelable>()
        binding.scrollView.saveHierarchyState(scrollParcelArray)
        outState.putSparseParcelableArray(SCROLL_PARCEL, scrollParcelArray)
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        binding = ActivityExpirationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUpToolbar()

        savedInstanceState?.let { bundle ->
            val scrollStateParcel = bundle.getSparseParcelableArray<Parcelable>(SCROLL_PARCEL)
            if (scrollStateParcel != null) {
                binding.scrollView.restoreHierarchyState(scrollStateParcel)
            }
        }

        val deleteTypeOptions = getDeleteOptions()
        val deleteTypeOptionAdapter = RadioOptionAdapter {
            viewModel.onExpirationTypeSelected(it)
        }
        binding.recyclerViewDeleteTypes.apply {
            adapter = deleteTypeOptionAdapter
            addItemDecoration(ContextCompat.getDrawable(this@ExpirationSettingsActivity, R.drawable.conversation_menu_divider)!!.let {
                DividerItemDecoration(this@ExpirationSettingsActivity, RecyclerView.VERTICAL).apply {
                    setDrawable(it)
                }
            })
            setHasFixedSize(true)
        }
        deleteTypeOptionAdapter.submitList(deleteTypeOptions)

        val timerOptionAdapter = RadioOptionAdapter {
            viewModel.onExpirationTimerSelected(it)
        }
        binding.recyclerViewTimerOptions.apply {
            adapter = timerOptionAdapter
            addItemDecoration(ContextCompat.getDrawable(this@ExpirationSettingsActivity, R.drawable.conversation_menu_divider)!!.let {
                DividerItemDecoration(this@ExpirationSettingsActivity, RecyclerView.VERTICAL).apply {
                    setDrawable(it)
                }
            })
        }
        binding.buttonSet.setOnClickListener {
            viewModel.onSetClick()
        }
        lifecycleScope.launchWhenStarted {
            launch {
                viewModel.uiState.collect { uiState ->
                    if (uiState.settingsSaved == true) {
                        finish()
                    }
                }
            }
            launch {
                viewModel.selectedExpirationType.collect { type ->
                    val position = deleteTypeOptions.indexOfFirst { it.value.toIntOrNull() == type }
                    deleteTypeOptionAdapter.setSelectedPosition(max(0, position))
                }
            }
            launch {
                viewModel.selectedExpirationTimer.collect { option ->
                    val position = viewModel.expirationTimerOptions.value.indexOfFirst { it.value == option?.value }
                    timerOptionAdapter.setSelectedPosition(max(0, position))
                }
            }
            launch {
                viewModel.expirationTimerOptions.collect { options ->
                    binding.textViewTimer.isVisible = options.isNotEmpty() && viewModel.uiState.value.showExpirationTypeSelector
                    binding.layoutTimer.isVisible = options.isNotEmpty()
                    timerOptionAdapter.submitList(options)
                }
            }
            launch {
                viewModel.recipient.collect {
                    binding.textViewDeleteType.isVisible = viewModel.uiState.value.showExpirationTypeSelector
                    binding.layoutDeleteTypes.isVisible = viewModel.uiState.value.showExpirationTypeSelector
                    binding.textViewFooter.isVisible = it?.isClosedGroupRecipient == true
                    binding.textViewFooter.text = HtmlCompat.fromHtml(getString(R.string.activity_expiration_settings_group_footer), HtmlCompat.FROM_HTML_MODE_COMPACT)
                }
            }
        }

    }

    private fun getDeleteOptions(): List<RadioOption> {
        if (!viewModel.uiState.value.showExpirationTypeSelector) return emptyList()

        val deleteTypeOptions = mutableListOf<RadioOption>()
        if (ExpirationConfiguration.isNewConfigEnabled) {
            if (viewModel.recipient.value?.isContactRecipient == true && viewModel.recipient.value?.isLocalNumber == false) {
                deleteTypeOptions.addAll(
                    listOf(
                        RadioOption(value = "-1", title = getString(R.string.expiration_off)),
                        RadioOption(
                            value = ExpirationType.DELETE_AFTER_READ_VALUE.toString(),
                            title = getString(R.string.expiration_type_disappear_after_read),
                            subtitle = getString(R.string.expiration_type_disappear_after_read_description)
                        ),
                        RadioOption(
                            value = ExpirationType.DELETE_AFTER_SEND_VALUE.toString(),
                            title = getString(R.string.expiration_type_disappear_after_send),
                            subtitle = getString(R.string.expiration_type_disappear_after_send_description)
                        )
                    )
                )
            }
        } else {
            if (viewModel.recipient.value?.isContactRecipient == true && viewModel.recipient.value?.isLocalNumber == false) {
                deleteTypeOptions.addAll(
                    listOf(
                        RadioOption(value = "-1", title = getString(R.string.expiration_off)),
                        RadioOption(
                            value = "0",
                            title = getString(R.string.expiration_type_disappear_legacy),
                            subtitle = getString(R.string.expiration_type_disappear_legacy_description)
                        ),
                        RadioOption(
                            value = ExpirationType.DELETE_AFTER_READ_VALUE.toString(),
                            title = getString(R.string.expiration_type_disappear_after_read),
                            subtitle = getString(R.string.expiration_type_disappear_after_read_description),
                            enabled = false
                        ),
                        RadioOption(
                            value = ExpirationType.DELETE_AFTER_SEND_VALUE.toString(),
                            title = getString(R.string.expiration_type_disappear_after_send),
                            subtitle = getString(R.string.expiration_type_disappear_after_send_description),
                            enabled = false
                        )
                    )
                )
            } else {
                deleteTypeOptions.addAll(
                    listOf(
                        RadioOption(value = "-1", title = getString(R.string.expiration_off)),
                        RadioOption(
                            value = "0",
                            title = getString(R.string.expiration_type_disappear_legacy),
                            subtitle = getString(R.string.expiration_type_disappear_legacy_description)
                        ),
                        RadioOption(
                            value = ExpirationType.DELETE_AFTER_SEND_VALUE.toString(),
                            title = getString(R.string.expiration_type_disappear_after_send),
                            subtitle = getString(R.string.expiration_type_disappear_after_send_description),
                            enabled = false
                        )
                    )
                )
            }
        }
        return deleteTypeOptions
    }

    private fun setUpToolbar() {
        setSupportActionBar(binding.toolbar)
        val actionBar = supportActionBar ?: return
        actionBar.title = getString(R.string.activity_expiration_settings_title)
        actionBar.subtitle = if (viewModel.selectedExpirationType.value == ExpirationType.DELETE_AFTER_SEND.number) {
            getString(R.string.activity_expiration_settings_subtitle_sent)
        } else {
            getString(R.string.activity_expiration_settings_subtitle)
        }
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setHomeButtonEnabled(true)
    }

    companion object {
        private const val SCROLL_PARCEL = "scroll_parcel"
        const val THREAD_ID = "thread_id"
    }

}