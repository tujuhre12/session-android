package org.thoughtcrime.securesms.glide

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Operation
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.session.libsession.utilities.recipients.RemoteFile

@HiltWorker
class CommunityFileDownloadWorker @AssistedInject constructor(
    @Assisted val context: Context,
    @Assisted val params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        TODO("Not yet implemented")
    }

    companion object {
        fun getFileForUrl(
            context: Context,
            folderName: String,
            avatar: RemoteFile.Community,
        ): EncryptedFileDownloadWorker.DownloadedFiles {
            TODO("Implement logic to get file for URL")
        }

        fun enqueueIfNeeded(context: Context,
                            avatar: RemoteFile.Community): Operation? {
            TODO("Implement enqueue logic for community server download worker")
        }

        fun cancel(context: Context, avatar: RemoteFile.Community): Operation? {
            TODO("Implement cancel logic for community server download worker")
        }
    }
}