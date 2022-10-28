package org.thoughtcrime.securesms.conversation.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.databinding.ActivityConversationSettingsBinding
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog
import org.thoughtcrime.securesms.util.ActivityDispatcher

@AndroidEntryPoint
class ConversationSettingsActivity: PassphraseRequiredActionBarActivity(), ActivityDispatcher {

    lateinit var binding: ActivityConversationSettingsBinding
    val viewModel: ConversationSettingsViewModel by viewModels()

    override fun dispatchIntent(body: (Context) -> Intent?) {
        TODO()
    }

    override fun showDialog(baseDialog: BaseDialog, tag: String?) {
        TODO()
    }


    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        binding = ActivityConversationSettingsBinding.inflate(layoutInflater)
    }
}