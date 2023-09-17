package org.thoughtcrime.securesms.conversation.expiration

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
            DisappearingMessages(uiState, callbacks = viewModel)
        }
    }
}
