package org.thoughtcrime.securesms.conversation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.launch
import org.session.libsession.database.StorageProtocol
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.database.Storage

class ConversationSettingsViewModel(
    val threadId: Long,
    private val storage: StorageProtocol,
    private val prefs: TextSecurePreferences
): ViewModel() {

    val recipient get() = storage.getRecipientForThread(threadId)

    fun isPinned() = storage.isThreadPinned(threadId)

    fun togglePin() = viewModelScope.launch {
        val isPinned = storage.isThreadPinned(threadId)
        storage.setThreadPinned(threadId, !isPinned)
    }

    fun isTrusted() = recipient?.let { recipient ->
        storage.isContactTrusted(recipient)
    } ?: false

    fun setTrusted(isTrusted: Boolean) {
        val recipient = recipient ?: return
        storage.setContactTrusted(recipient, isTrusted)
    }

    fun isUserGroupAdmin(): Boolean = recipient?.let { recipient ->
        if (!recipient.isGroupRecipient) return@let false
        val localUserAddress = prefs.getLocalNumber() ?: return@let false
        val group = storage.getGroup(recipient.address.toGroupString())
        group?.admins?.contains(Address.fromSerialized(localUserAddress)) ?: false // this will have to be replaced for new closed groups
    } ?: false

    // DI-related
    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(threadId: Long): Factory
    }
    class Factory @AssistedInject constructor(
        @Assisted private val threadId: Long,
        private val storage: Storage,
        private val prefs: TextSecurePreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConversationSettingsViewModel(threadId, storage, prefs) as T
        }
    }

}