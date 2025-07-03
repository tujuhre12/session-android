package org.thoughtcrime.securesms.messagerequests

import android.content.Context
import androidx.loader.content.AsyncTaskLoader
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord

class MessageRequestsLoader(
    private val threadDatabase: ThreadDatabase,
    context: Context
) : AsyncTaskLoader<List<ThreadRecord>>(context) {


    override fun loadInBackground(): List<ThreadRecord> {
        val list = threadDatabase.unapprovedConversationList
        list.sortWith(UNAPPROVED_THREAD_COMPARATOR)
        return list
    }

    companion object {
        private val UNAPPROVED_THREAD_COMPARATOR = compareByDescending<ThreadRecord> { it.lastMessage?.timestamp ?: 0 }
            .thenByDescending { it.date }
            .thenBy { it.recipient.displayName }
    }
}