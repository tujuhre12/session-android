package org.session.libsession.avatars

import android.app.Application
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.RemoteFile
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.attachments.RemoteFileDownloadWorker
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.RecipientSettingsDatabase
import org.thoughtcrime.securesms.dependencies.ManagerScope
import org.thoughtcrime.securesms.glide.RecipientAvatarDownloadManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvatarCacheCleaner @Inject constructor(
    private val application: Application,
    private val recipientAvatarDownloadManager: RecipientAvatarDownloadManager,
    private val recipientSettingsDatabase: RecipientSettingsDatabase,
    private val mmsSmsDatabase: MmsSmsDatabase,
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
        // 1) Build the set of still-wanted Avatars from:
        // -> Config
        // -> RecipientSettingsDatabase

        // config
        val avatarsFromConfig: Set<RemoteFile> = recipientAvatarDownloadManager.getAllAvatars()
        // recipient_settings
        val recipientAvatars : Map<Address, RemoteFile> = recipientSettingsDatabase.getAllReferencedAvatarFiles()
        // mms and sms used to check for active references
        val localActiveAddresses : Set<Address> = mmsSmsDatabase.getAllReferencedAddresses()
        .mapTo(mutableSetOf()) { Address.fromSerialized(it) }

        // 2) Keep only those recipient avatars whose address exists in a community/group
        // Filter here since we don't delete rows from recipient_settings
        val keepFromRecipients: Set<RemoteFile> =
            recipientAvatars
                .asSequence()
                .filter { (address, _) -> address in localActiveAddresses }
                .map { it.value }
                .toSet()

        // 3) Union of everything we want to keep
        val filesToKeep: Set<RemoteFile> =
            (avatarsFromConfig + keepFromRecipients).toSet()

        // 4) Map to actual files (same hashing/location as downloader)
        val wantedFiles: Set<File> = filesToKeep
            .map { RemoteFileDownloadWorker.computeFileName(application, it) }
            .toSet()

        // 5) Delete everything not wanted in cache/remote_files
        val dir = File(application.cacheDir, "remote_files")
        val files = dir.listFiles().orEmpty()
        var deleted = 0
        for (file in files) {
            if (file !in wantedFiles && file.delete()) deleted++
        }

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