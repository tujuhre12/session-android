package org.thoughtcrime.securesms.preferences

import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityBlockedContactsBinding
import org.thoughtcrime.securesms.ScreenLockActionBarActivity
import org.thoughtcrime.securesms.showSessionDialog

@AndroidEntryPoint
class BlockedContactsActivity: ScreenLockActionBarActivity() {

    lateinit var binding: ActivityBlockedContactsBinding

    val viewModel: BlockedContactsViewModel by viewModels()

    val adapter: BlockedContactsAdapter by lazy { BlockedContactsAdapter(viewModel) }

    private fun unblock() {
        showSessionDialog {
            title(getString(R.string.blockUnblock))
            text(viewModel.getText(context, viewModel.state.value.selectedItems))
            dangerButton(R.string.blockUnblock, R.string.AccessibilityId_unblockConfirm) { viewModel.unblock() }
            cancelButton()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        binding = ActivityBlockedContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.contactsRecyclerView.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state ->
                    adapter.submitList(state.items)
                    binding.emptyStateMessageTextView.isVisible = state.emptyStateMessageTextViewVisible
                    binding.nonEmptyStateGroup.isVisible = state.nonEmptyStateGroupVisible
                    binding.unblockButton.isEnabled = state.unblockButtonEnabled
                }
            }
        }

        binding.unblockButton.setOnClickListener { unblock() }
    }
}
