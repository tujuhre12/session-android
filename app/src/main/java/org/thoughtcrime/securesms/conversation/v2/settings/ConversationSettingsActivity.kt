package org.thoughtcrime.securesms.conversation.v2.settings

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import dagger.hilt.android.AndroidEntryPoint
import org.thoughtcrime.securesms.FullComposeScreenLockActivity

@AndroidEntryPoint
class ConversationSettingsActivity: FullComposeScreenLockActivity() {

    companion object {
        const val THREAD_ID = "conversation_settings_thread_id"

        fun createIntent(context: Context, threadId: Long): Intent {
            return Intent(context, ConversationSettingsActivity::class.java).apply {
                putExtra(THREAD_ID, threadId)
            }
        }
    }


    @Composable
    override fun ComposeContent() {
        ConversationSettingsScreen(
            threadId = intent.getLongExtra(THREAD_ID, 0),
            onBack = this::finish
        )
    }
}
