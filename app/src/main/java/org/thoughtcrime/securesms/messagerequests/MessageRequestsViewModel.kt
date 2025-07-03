package org.thoughtcrime.securesms.messagerequests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.session.libsession.utilities.Address
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.repository.ConversationRepository
import javax.inject.Inject

@HiltViewModel
class MessageRequestsViewModel @Inject constructor(
    private val repository: ConversationRepository,
    private val threadDatabase: ThreadDatabase
) : ViewModel() {
    private val reloadTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val threads: StateFlow<List<ThreadRecord>> = reloadTrigger
        .onStart { emit(Unit) }
        .map {
            withContext(Dispatchers.Default) {
                threadDatabase.unapprovedConversationList
                    .apply { sortWith(COMPARATOR) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())


    // We assume thread.recipient is a contact or thread.invitingAdmin is not null
    fun blockMessageRequest(thread: ThreadRecord, blockRecipient: Address) = viewModelScope.launch {
        repository.setBlocked(blockRecipient, true)
        deleteMessageRequest(thread)
        reloadTrigger.emit(Unit)
    }

    fun deleteMessageRequest(thread: ThreadRecord) = viewModelScope.launch {
        repository.deleteMessageRequest(thread)
        reloadTrigger.emit(Unit)
    }

    fun clearAllMessageRequests(block: Boolean) = viewModelScope.launch {
        repository.clearAllMessageRequests(block)
        reloadTrigger.emit(Unit)
    }

    companion object {
        private val COMPARATOR get() = compareByDescending<ThreadRecord> { it.lastMessage?.timestamp ?: 0 }
            .thenByDescending { it.date }
            .thenBy { it.recipient.displayName }
    }
}
