package org.thoughtcrime.securesms.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.database.DatabaseContentProviders
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.util.observeChanges
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
        private val threadDb: ThreadDatabase,
        @ApplicationContext appContext: Context,
) : ViewModel() {
    // SharedFlow that emits whenever the user asks us to reload  the conversation
    private val manualReloadTrigger = MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * A [StateFlow] that emits the list of threads in the conversation list.
     *
     * This flow will emit whenever the user asks us to reload the conversation list or
     * whenever the conversation list changes.
     */
    @Suppress("OPT_IN_USAGE")
    val threads: StateFlow<List<ThreadRecord>?> = merge(
            manualReloadTrigger,
            appContext.contentResolver.observeChanges(DatabaseContentProviders.ConversationList.CONTENT_URI))
            .debounce(CHANGE_NOTIFICATION_DEBOUNCE_MILLS)
            .onStart { emit(Unit) }
            .mapLatest { _ ->
                withContext(Dispatchers.IO) {
                    threadDb.approvedConversationList.use { openCursor ->
                        val reader = threadDb.readerFor(openCursor)
                        buildList(reader.length) {
                            while (true) {
                                add(reader.next ?: break)
                            }
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun tryUpdateChannel() = manualReloadTrigger.tryEmit(Unit)

    companion object {
        private const val CHANGE_NOTIFICATION_DEBOUNCE_MILLS = 100L
    }
}
