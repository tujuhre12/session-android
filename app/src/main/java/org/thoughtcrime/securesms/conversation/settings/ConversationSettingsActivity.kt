package org.thoughtcrime.securesms.conversation.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityConversationSettingsBinding
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.MediaOverviewActivity
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.database.LokiThreadDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.mms.GlideApp
import javax.inject.Inject

@AndroidEntryPoint
class ConversationSettingsActivity: PassphraseRequiredActionBarActivity(), View.OnClickListener {

    companion object {
        // used to trigger displaying conversation search in calling parent activity
        const val RESULT_SEARCH = 22
    }

    lateinit var binding: ActivityConversationSettingsBinding

    @Inject lateinit var threadDb: ThreadDatabase
    @Inject lateinit var lokiThreadDb: LokiThreadDatabase
    @Inject lateinit var viewModelFactory: ConversationSettingsViewModel.AssistedFactory
    val viewModel: ConversationSettingsViewModel by viewModels {
        val threadId = intent.getLongExtra(ConversationActivityV2.THREAD_ID, -1L)
        if (threadId == -1L) {
            finish()
        }
        viewModelFactory.create(threadId)
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        binding = ActivityConversationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.profilePictureView.root.glide = GlideApp.with(this)
        updateRecipientDisplay()
        binding.searchConversation.setOnClickListener(this)
        binding.allMedia.setOnClickListener(this)
        binding.pinConversation.setOnClickListener(this)
    }

    override fun onResume() {
        super.onResume()

    }

    override fun onPause() {
        super.onPause()

    }

    private fun updateRecipientDisplay() {
        val recipient = viewModel.recipient ?: return
        // Setup profile image
        binding.profilePictureView.root.update(recipient)
        // Setup name
        binding.conversationName.text = when {
            recipient.isLocalNumber -> getString(R.string.note_to_self)
            else -> recipient.toShortString()
        }
        // Setup group description (if group)
        binding.conversationSubtitle.isVisible = recipient.isClosedGroupRecipient.apply {
            binding.conversationSubtitle.text = "TODO: This is a test for group descriptions"
        }
        // Toggle group-specific settings

        // Set pinned state
        binding.pinConversation.setText(
            if (viewModel.isPinned()) R.string.conversation_settings_unpin_conversation
            else R.string.conversation_settings_pin_conversation
        )
        // Set auto-download state
    }

    override fun onClick(v: View?) {
        if (v === binding.searchConversation) {
            setResult(RESULT_SEARCH)
            finish()
        } else if (v === binding.allMedia) {
            val threadRecipient = viewModel.recipient ?: return
            val intent = Intent(this, MediaOverviewActivity::class.java).apply {
                putExtra(MediaOverviewActivity.ADDRESS_EXTRA, threadRecipient.address)
            }
            startActivity(intent)
        } else if (v === binding.pinConversation) {
            viewModel.togglePin().invokeOnCompletion { e ->
                if (e != null) {
                    // something happened
                    Log.e("ConversationSettings", "Failed to toggle pin on thread", e)
                } else {
                    updateRecipientDisplay()
                }
            }
        }
    }
}