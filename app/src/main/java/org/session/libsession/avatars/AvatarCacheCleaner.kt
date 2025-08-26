package org.session.libsession.avatars

import android.app.Application
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.session.libsession.utilities.recipients.RemoteFile
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.attachments.RemoteFileDownloadWorker
import org.thoughtcrime.securesms.dependencies.ManagerScope
import org.thoughtcrime.securesms.glide.RecipientAvatarDownloadManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvatarCacheCleaner @Inject constructor(
    private val application: Application,
    private val avatarDownloadManager: RecipientAvatarDownloadManager, // we can reuse some logic here
    @param:ManagerScope private val coroutineScope: CoroutineScope
) {

    companion object {
        const val TAG = "AvatarCacheCleaner"
    }

    /**
     * Deletes avatar files under cache/remote_files that are no longer referenced
     * in the current config. Returns number of files deleted.
     */
    suspend fun cleanUpAvatars(): Int = withContext(Dispatchers.IO) {
        // 1) Build the set of still-wanted RemoteFiles (contacts + groups)
        val wantedRemotes: Set<RemoteFile> = avatarDownloadManager.getAllAvatars()

        // 2) Map to actual files (same hashing/location as downloader)
        val wantedFiles: Set<File> = wantedRemotes
            .map { RemoteFileDownloadWorker.computeFileName(application, it) }
            .toSet()

        // 3) Delete everything not wanted in cache/remote_files
        val dir = File(application.cacheDir, "remote_files")
        val files = dir.listFiles().orEmpty()
        var deleted = 0
        for (f in files) {
            if (f !in wantedFiles && f.delete()) deleted++
        }

        // 4) Clear Glide cache. Might need this now but we should remove after we fully migrate to Coil
        // Note to keep this off the main thread
        Glide.get(application).clearDiskCache()

        deleted
    }

    fun launchAvatarCleanup(cancelInFlight: Boolean = false) {
        coroutineScope.launch(Dispatchers.IO) {
            if (cancelInFlight) {
                RemoteFileDownloadWorker.cancelAll(application)
            }
            val deleted = cleanUpAvatars()
            Log.d(TAG, "Avatar cache removed: $deleted files")
        }
    }
}