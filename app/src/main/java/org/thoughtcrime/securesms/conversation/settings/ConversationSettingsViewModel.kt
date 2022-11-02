package org.thoughtcrime.securesms.conversation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.database.Storage

class ConversationSettingsViewModel(
    val threadId: Long,
    private val storage: Storage
): ViewModel() {

    val recipient get() = storage.getRecipientForThread(threadId)

    fun isPinned() = storage.isThreadPinned(threadId)

    fun togglePin() = viewModelScope.launch {
        val isPinned = storage.isThreadPinned(threadId)
        storage.setThreadPinned(threadId, !isPinned)
    }

    // DI-related
    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(threadId: Long): Factory
    }
    class Factory @AssistedInject constructor(
        @Assisted private val threadId: Long,
        private val storage: Storage
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConversationSettingsViewModel(threadId, storage) as T
        }
    }

}