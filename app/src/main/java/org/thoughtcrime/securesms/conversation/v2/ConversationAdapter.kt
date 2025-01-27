package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.database.Cursor
import android.util.SparseArray
import android.util.SparseBooleanArray
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.WorkerThread
import androidx.core.util.getOrDefault
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.conversation.v2.messages.ControlMessageView
import org.thoughtcrime.securesms.conversation.v2.messages.VisibleMessageView
import org.thoughtcrime.securesms.conversation.v2.messages.VisibleMessageViewDelegate
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class ConversationAdapter(
    context: Context,
    cursor: Cursor?,
    originalLastSeen: Long,
    private val isReversed: Boolean,
    private val onItemPress: (MessageRecord, Int, VisibleMessageView, MotionEvent) -> Unit,
    private val onItemSwipeToReply: (MessageRecord, Int) -> Unit,
    private val onItemLongPress: (MessageRecord, Int, View) -> Unit,
    private val onDeselect: (MessageRecord, Int) -> Unit,
    private val onAttachmentNeedsDownload: (DatabaseAttachment) -> Unit,
    private val glide: RequestManager,
    lifecycleCoroutineScope: LifecycleCoroutineScope
) : CursorRecyclerViewAdapter<RecyclerView.ViewHolder>(context, cursor) {

    // We’ll hold one MmsSmsDatabase and a single "reader" for the current cursor.
    private val messageDB by lazy { DatabaseComponent.get(context).mmsSmsDatabase() }
    private val contactDB by lazy { DatabaseComponent.get(context).sessionContactDatabase() }

    // The single cached reader
    private var mmsSmsReader: MmsSmsDatabase.Reader? = null

    var selectedItems = mutableSetOf<MessageRecord>()
    var isAdmin: Boolean = false
    private var searchQuery: String? = null
    var visibleMessageViewDelegate: VisibleMessageViewDelegate? = null

    private val updateQueue = Channel<String>(1024, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val contactCache = SparseArray<Contact>(100)
    private val contactLoadedCache = SparseBooleanArray(100)
    private val lastSeen = AtomicLong(originalLastSeen)
    private var lastSentMessageId: Long = -1L

    init {
        // If the adapter was constructed with a non-null cursor, create a reader right away.
        if (cursor != null) {
            mmsSmsReader = messageDB.readerFor(cursor, /* getQuote = */ true)
        }

        // Launch a background worker to load contact info for senders
        lifecycleCoroutineScope.launch(IO) {
            while (isActive) {
                val senderId = updateQueue.receive()
                val contact = getSenderInfo(senderId) ?: continue
                contactCache[senderId.hashCode()] = contact
                contactLoadedCache.put(senderId.hashCode(), true)
            }
        }
    }

    @WorkerThread
    private fun getSenderInfo(sender: String): Contact? {
        return contactDB.getContactWithAccountID(sender)
    }

    sealed class ViewType(val rawValue: Int) {
        object Visible : ViewType(0)
        object Control : ViewType(1)

        companion object {
            val allValues: Map<Int, ViewType> get() = mapOf(
                Visible.rawValue to Visible,
                Control.rawValue to Control
            )
        }
    }

    class VisibleMessageViewHolder(val view: VisibleMessageView) : RecyclerView.ViewHolder(view)
    class ControlMessageViewHolder(val view: ControlMessageView) : RecyclerView.ViewHolder(view)

    // ----- CursorRecyclerViewAdapter overrides -----

    // Decide which enum type each row gets, based on the cached record (obtained from
    // mmsSmsReader.getMessageAt(cursor.position).
    override fun getItemViewType(cursor: Cursor): Int {
        val position = cursor.position
        val record = mmsSmsReader?.getMessageAt(position) ?: return ViewType.Visible.rawValue
        return if (record.isControlMessage) ViewType.Control.rawValue else ViewType.Visible.rawValue
    }

    override fun onCreateItemViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val typedViewType = ViewType.allValues[viewType]
        return when (typedViewType) {
            ViewType.Visible -> VisibleMessageViewHolder(VisibleMessageView(context))
            ViewType.Control -> ControlMessageViewHolder(ControlMessageView(context))
            else ->
                throw IllegalStateException("Unexpected view type: $typedViewType.")
        }
    }

    // Bind a row using the cached record. We do NOT re-parse the cursor here; we simply
    // read from mmsSmsReader.getMessageAt(position).
    override fun onBindItemViewHolder(viewHolder: RecyclerView.ViewHolder, cursor: Cursor) {
        val position = viewHolder.adapterPosition
        val message = getMessageAtPosition(position) ?: return

        // We also fetch the message "before" and "after" in conversation order
        val messageBefore = getMessageBefore(position)
        val messageAfter = getMessageAfter(position)

        when (viewHolder) {
            is VisibleMessageViewHolder -> {
                val visibleMessageView = viewHolder.view
                val isSelected = selectedItems.contains(message)
                visibleMessageView.snIsSelected = isSelected
                visibleMessageView.indexInAdapter = position

                // Contact-lookup
                val senderId = message.individualRecipient.address.serialize()
                val senderIdHash = senderId.hashCode()
                updateQueue.trySend(senderId)

                if (contactCache[senderIdHash] == null &&
                    !contactLoadedCache.getOrDefault(senderIdHash, false)
                ) {
                    val contact = getSenderInfo(senderId)
                    contact?.let { contactCache[senderIdHash] = it }
                }
                val contact = contactCache[senderIdHash]

                visibleMessageView.bind(
                    message = message,
                    previous = messageBefore,
                    next = messageAfter,
                    glide = glide,
                    searchQuery = searchQuery,
                    contact = contact,
                    senderAccountID = senderId,
                    lastSeen = lastSeen.get(),
                    delegate = visibleMessageViewDelegate,
                    onAttachmentNeedsDownload = onAttachmentNeedsDownload,
                    lastSentMessageId = lastSentMessageId
                )

                if (!message.isDeleted) {
                    visibleMessageView.onPress = { event ->
                        onItemPress(message, position, visibleMessageView, event)
                    }
                    visibleMessageView.onSwipeToReply = {
                        onItemSwipeToReply(message, position)
                    }
                    visibleMessageView.onLongPress = {
                        onItemLongPress(message, position, visibleMessageView)
                    }
                } else {
                    visibleMessageView.onPress = null
                    visibleMessageView.onSwipeToReply = null
                    // "Deleted" messages can still be long-pressed
                    visibleMessageView.onLongPress = {
                        onItemLongPress(message, position, visibleMessageView)
                    }
                }
            }

            is ControlMessageViewHolder -> {
                viewHolder.view.bind(
                    message = message,
                    previous = messageBefore,
                    longPress = {
                        onItemLongPress(message, position, viewHolder.view)
                    }
                )
            }
        }
    }

    // If we toggle selection, then notify that item changed so it can be visually updated.
    fun toggleSelection(message: MessageRecord, position: Int) {
        if (selectedItems.contains(message)) selectedItems.remove(message) else selectedItems.add(message)
        notifyItemChanged(position)
    }

    override fun onItemViewRecycled(viewHolder: RecyclerView.ViewHolder?) {
        when (viewHolder) {
            is VisibleMessageViewHolder -> viewHolder.view.recycle()
            is ControlMessageViewHolder -> viewHolder.view.recycle()
        }
        super.onItemViewRecycled(viewHolder)
    }

    // Override changeCursor so we can destroy the old reader and create a new one, ensuring
    // a single top-level cache for the new Cursor.
    override fun changeCursor(cursor: Cursor?) {
        // Close old reader
        mmsSmsReader?.close()
        mmsSmsReader = null

        super.changeCursor(cursor)

        // If we have a new cursor, create a new single-level Reader
        if (cursor != null) {
            mmsSmsReader = messageDB.readerFor(cursor, /* getQuote = */ true)
        }

        // Re-validate any selected items (some may have disappeared or changed)
        val toRemove = mutableSetOf<MessageRecord>()
        val toDeselect = mutableSetOf<Pair<Int, MessageRecord>>()

        for (selected in selectedItems) {
            val position = getItemPositionForTimestamp(selected.timestamp)
            if (position == null || position == -1) {
                toRemove += selected
            } else {
                val item = getMessageAtPosition(position)
                if (item == null || item.isDeleted) {
                    toDeselect += (position to selected)
                }
            }
        }

        selectedItems -= toRemove
        toDeselect.forEach { (pos, record) ->
            onDeselect(record, pos)
        }
    }

    // ----- Helpers for retrieving MessageRecords by position -----

    private fun getMessageAtPosition(position: Int): MessageRecord? {
        val reader = mmsSmsReader ?: return null
        if (position < 0 || position >= itemCount) return null
        return reader.getMessageAt(position)
    }

    private fun getMessageBefore(currentPos: Int): MessageRecord? {
        // "Before" is +1 if reversed, -1 if not reversed
        val newPos = if (isReversed) currentPos + 1 else currentPos - 1
        return getMessageAtPosition(newPos)
    }

    private fun getMessageAfter(currentPos: Int): MessageRecord? {
        // "After" is -1 if reversed, +1 if not reversed
        val newPos = if (isReversed) currentPos - 1 else currentPos + 1
        return getMessageAtPosition(newPos)
    }

    // Find the item position for a given timestamp, if it exists
    fun getItemPositionForTimestamp(timestamp: Long): Int? {
        if (timestamp <= 0L || !isActiveCursor) return null
        for (i in 0 until itemCount) {
            val record = getMessageAtPosition(i) ?: continue
            if (record.timestamp == timestamp) {
                return i
            }
        }
        return null
    }

    // Find the position where the user last read (lastSeenTimestamp), scanning from
    // newest to oldest or oldest to newest depending on isReversed.
    fun findLastSeenItemPosition(lastSeenTimestamp: Long): Int? {
        if (!isActiveCursor) return null

        if (lastSeenTimestamp == 0L) {
            // If there's no lastSeen, pick the last or first (depending on reversed)
            if (isReversed && itemCount > 0) return 0
            if (!isReversed && itemCount > 0) return (itemCount - 1)
        }

        // We loop until we find a message that is outgoing or older than lastSeen
        // If reversed, we start from i=0 (the newest visually) and go forward
        // If not reversed, we start from the end and go backward
        if (isReversed) {
            for (i in 0 until itemCount) {
                val record = getMessageAtPosition(i) ?: continue
                if (record.isOutgoing || record.timestamp <= lastSeenTimestamp) {
                    return i
                }
            }
        } else {
            // Not reversed: start from newest = itemCount-1, go down to 0
            for (i in 0 until itemCount) {
                val index = (itemCount - 1) - i
                val record = getMessageAtPosition(index) ?: continue
                if (record.isOutgoing || record.timestamp <= lastSeenTimestamp) {
                    return min(itemCount - 1, index + 1)
                }
            }
        }
        return null
    }

    fun onSearchQueryUpdated(query: String?) {
        this.searchQuery = query
        notifyDataSetChanged()
    }

    fun getTimestampForItemAt(firstVisiblePosition: Int): Long? {
        val record = getMessageAtPosition(firstVisiblePosition) ?: return null
        return record.timestamp
    }
}