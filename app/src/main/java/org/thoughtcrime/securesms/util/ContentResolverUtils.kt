package org.thoughtcrime.securesms.util

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.CheckResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Observe changes to a content URI. This function will emit the URI whenever the content or
 * its descendants change, according to the parameter [notifyForDescendants].
 */
@CheckResult
fun ContentResolver.observeChanges(uri: Uri, notifyForDescendants: Boolean = false): Flow<Uri> {
    return callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(uri)
            }
        }

        registerContentObserver(uri, notifyForDescendants, observer)
        awaitClose {
            unregisterContentObserver(observer)
        }
    }
}
