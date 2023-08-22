package org.thoughtcrime.securesms.conversation.expiration

import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.divider.MaterialDividerItemDecoration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import network.loki.messenger.BuildConfig
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityExpirationSettingsBinding
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.preferences.ExpirationRadioOption
import org.thoughtcrime.securesms.preferences.RadioOptionAdapter
import org.thoughtcrime.securesms.preferences.radioOption
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
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
        val afterReadOptions = resources.getIntArray(R.array.read_expiration_time_values)
            .zip(resources.getStringArray(R.array.read_expiration_time_names)) { value, name ->
                radioOption(ExpiryMode.AfterRead(value.toLong()), name) { contentDescription(R.string.AccessibilityId_time_option) }
            }
        val afterSendOptions = resources.getIntArray(R.array.send_expiration_time_values)
            .zip(resources.getStringArray(R.array.send_expiration_time_names)) { value, name ->
                radioOption(ExpiryMode.AfterSend(value.toLong()), name) { contentDescription(R.string.AccessibilityId_time_option) }
            }
        viewModelFactory.create(threadId, mayAddTestExpiryOption(afterReadOptions), mayAddTestExpiryOption(afterSendOptions))
    }

    private fun mayAddTestExpiryOption(expiryOptions: List<ExpirationRadioOption>): List<ExpirationRadioOption> =
        if (BuildConfig.DEBUG) {
            when (expiryOptions.first().value) {
                is ExpiryMode.AfterRead -> ExpiryMode.AfterRead(60)
                is ExpiryMode.AfterSend -> ExpiryMode.AfterSend(60)
                is ExpiryMode.Legacy -> ExpiryMode.Legacy(60)
                ExpiryMode.NONE -> ExpiryMode.NONE // shouldn't happen
            }.let { radioOption(it, "1 Minute (for testing purposes)") }
                .let { expiryOptions.toMutableList().apply { add(1, it) } }
        } else expiryOptions

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

        val deleteTypeOptions = viewModel.getDeleteOptions()
        val deleteTypeOptionAdapter = RadioOptionAdapter {
            viewModel.onExpirationTypeSelected(it)
        }
        binding.recyclerViewDeleteTypes.apply {
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
            adapter = deleteTypeOptionAdapter
            addDividers()
            setHasFixedSize(true)
        }
        deleteTypeOptionAdapter.submitList(deleteTypeOptions)

        val timerOptionAdapter = RadioOptionAdapter {
            viewModel.onExpirationTimerSelected(it)
        }
        binding.recyclerViewTimerOptions.apply {
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
            adapter = timerOptionAdapter
            addDividers()
        }
        binding.buttonSet.setOnClickListener {
            viewModel.onSetClick()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    when (uiState.settingsSaved) {
                        true -> {
                            ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(this@ExpirationSettingsActivity)
                            finish()
                        }
                        false -> showToast(getString(R.string.ExpirationSettingsActivity_settings_not_updated))
                        else -> {}
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedExpirationType.collect { type ->
                    val position = deleteTypeOptions.indexOfFirst { it.value == type }
                    deleteTypeOptionAdapter.setSelectedPosition(max(0, position))
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedExpirationTimer.collect { option ->
                    val position =
                        viewModel.expirationTimerOptions.value.indexOfFirst { it.value == option?.value }
                    timerOptionAdapter.setSelectedPosition(max(0, position))
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.expirationTimerOptions.collect { options ->
                    binding.textViewTimer.isVisible =
                        options.isNotEmpty() && viewModel.uiState.value.showExpirationTypeSelector
                    binding.layoutTimer.isVisible = options.isNotEmpty()
                    timerOptionAdapter.submitList(options)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recipient.collect {
                    binding.textViewDeleteType.isVisible = viewModel.uiState.value.showExpirationTypeSelector
                    binding.layoutDeleteTypes.isVisible = viewModel.uiState.value.showExpirationTypeSelector
                    binding.textViewFooter.isVisible = it?.isClosedGroupRecipient == true
                    binding.textViewFooter.text = HtmlCompat.fromHtml(getString(R.string.activity_expiration_settings_group_footer), HtmlCompat.FROM_HTML_MODE_COMPACT)
                }
            }
        }

    }

    private fun RecyclerView.addDividers() {
        addItemDecoration(
            MaterialDividerItemDecoration(
                this@ExpirationSettingsActivity,
                RecyclerView.VERTICAL
            ).apply {
                isLastItemDecorated = false
            }
        )
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun setUpToolbar() {
        setSupportActionBar(binding.toolbar)
        val actionBar = supportActionBar ?: return
        actionBar.title = getString(R.string.activity_expiration_settings_title)
        actionBar.subtitle = if (viewModel.selectedExpirationType.value is ExpiryMode.AfterSend) {
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