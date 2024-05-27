package org.thoughtcrime.securesms.home

import android.content.ContentResolver
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.DatabaseContentProviders
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.util.observeChanges
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext as ApplicationContextQualifier

@HiltViewModel
class HomeViewModel @Inject constructor(
        private val threadDb: ThreadDatabase,
        private val contentResolver: ContentResolver,
        @ApplicationContextQualifier private val context: Context,
) : ViewModel() {
    // SharedFlow that emits whenever the user asks us to reload  the conversation
    private val manualReloadTrigger = MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * A [StateFlow] that emits the list of threads and the typing status of each thread.
     *
     * This flow will emit whenever the user asks us to reload the conversation list or
     * whenever the conversation list changes.
     */
    val threads: StateFlow<Data?> = combine(observeConversationList(), observeTypingStatus(), ::Data)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun observeTypingStatus(): Flow<Set<Long>> =
            ApplicationContext.getInstance(context).typingStatusRepository
                    .typingThreads
                    .asFlow()
                    .onStart { emit(emptySet()) }
                    .distinctUntilChanged()

    @Suppress("OPT_IN_USAGE")
    private fun observeConversationList(): Flow<List<ThreadRecord>> = merge(
            manualReloadTrigger,
            contentResolver.observeChanges(DatabaseContentProviders.ConversationList.CONTENT_URI))
            .debounce(CHANGE_NOTIFICATION_DEBOUNCE_MILLS)
            .onStart { emit(Unit) }
            .mapLatest { _ ->
                withContext(Dispatchers.IO) {
                    threadDb.approvedConversationList.use { openCursor ->
                        val reader = threadDb.readerFor(openCursor)
                        buildList(reader.count) {
                            while (true) {
                                add(reader.next ?: break)
                            }
                        }
                    }
                }
            }

    fun tryReload() = manualReloadTrigger.tryEmit(Unit)

    data class Data(
            val threads: List<ThreadRecord>,
            val typingThreadIDs: Set<Long>
    )

    companion object {
        private const val CHANGE_NOTIFICATION_DEBOUNCE_MILLS = 100L
    }
}
