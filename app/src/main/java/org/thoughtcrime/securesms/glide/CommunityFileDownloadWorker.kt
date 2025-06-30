package org.thoughtcrime.securesms.glide

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Operation
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.snode.utilities.await
import org.session.libsession.utilities.AESGCM
import org.session.libsession.utilities.recipients.RemoteFile
import org.session.libsignal.utilities.ByteArraySlice
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import java.io.File
import java.security.MessageDigest

@HiltWorker
class CommunityFileDownloadWorker @AssistedInject constructor(
    @Assisted val context: Context,
    @Assisted val params: WorkerParameters
) : RemoteFileDownloadWorker(context, params) {
    private val communityServer: String
        get() = requireNotNull(
            inputData.getString(ARG_COMMUNITY_SERVER)
        ) {
            "CommunityFileDownloadWorker requires a community server URL"
        }

    private val roomId: String
        get() = requireNotNull(inputData.getString(ARG_ROOM_ID)) {
            "CommunityFileDownloadWorker requires a room ID"
        }

    private val fileId: String
        get() = requireNotNull(inputData.getString(ARG_FILE_ID)) {
            "CommunityFileDownloadWorker requires a file ID"
        }

    override suspend fun downloadFile(): ByteArraySlice {
        return OpenGroupApi.download(fileId = fileId, room = roomId, server = communityServer).await()
    }

    override fun getFilesFromInputData(): DownloadedFiles {
        return getFileForUrl(
            context,
            RemoteFile.Community(
                communityServerBaseUrl = communityServer,
                roomId = roomId,
                fileId = fileId
            )
        )
    }

    override fun saveDownloadedFile(from: ByteArraySlice, out: File) {
        val encrypted = AESGCM.encrypt(
            plaintext = from,
            symmetricKey = AttachmentSecretProvider.getInstance(context)
                .orCreateAttachmentSecret.modernKey
        )

        // Write the encrypted bytes to a temporary file then move it to the final location.
        val tmpOut = File.createTempFile("download-community-", null, context.cacheDir)
        tmpOut.writeBytes(encrypted)
        require(tmpOut.renameTo(out)) {
            "Failed to rename temporary file ${tmpOut.absolutePath} to ${out.absolutePath}"
        }
    }

    override val debugName: String
        get() = "CommunityFile(server=${communityServer.take(8)}, roomId=${roomId.take(3)}, fileId=$fileId)"

    companion object {
        private const val ARG_COMMUNITY_SERVER = "community_server"
        private const val ARG_ROOM_ID = "room_id"
        private const val ARG_FILE_ID = "file_id"

        fun getFileForUrl(
            context: Context,
            avatar: RemoteFile.Community,
        ): DownloadedFiles {
            val digest = MessageDigest.getInstance("SHA-256")

            digest.update(avatar.communityServerBaseUrl.lowercase().toByteArray())
            digest.update(avatar.roomId.lowercase().toByteArray())
            digest.update(avatar.fileId.lowercase().toByteArray())

            val hashed = digest.digest().toHexString()

            return DownloadedFiles(
                completedFile = File(context.cacheDir, "community_files/$hashed"),
            )
        }

        fun enqueue(
            context: Context,
            file: RemoteFile.Community
        ): Operation {
            TODO("Implement enqueue logic for community server download worker")
        }

        fun cancel(context: Context, avatar: RemoteFile.Community): Operation? {
            TODO("Implement cancel logic for community server download worker")
        }
    }
}