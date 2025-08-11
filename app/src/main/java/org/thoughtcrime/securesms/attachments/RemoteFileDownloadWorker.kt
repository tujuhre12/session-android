package org.thoughtcrime.securesms.attachments

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.hasKeyWithValueOfType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import network.loki.messenger.libsession_util.encrypt.EncryptionStream
import network.loki.messenger.libsession_util.util.Bytes
import org.session.libsession.messaging.file_server.FileServerApi
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.utilities.await
import org.session.libsession.utilities.AESGCM
import org.session.libsession.utilities.recipients.RemoteFile
import org.session.libsignal.exceptions.NonRetryableException
import org.session.libsignal.utilities.ByteArraySlice
import org.session.libsignal.utilities.ByteArraySlice.Companion.view
import org.session.libsignal.utilities.ByteArraySlice.Companion.write
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import org.thoughtcrime.securesms.glide.EncryptedFileCodec
import org.thoughtcrime.securesms.glide.EncryptedFileMeta
import org.thoughtcrime.securesms.util.getRootCause
import java.io.File
import java.security.MessageDigest
import java.time.Duration
import javax.inject.Provider

@HiltWorker
class RemoteFileDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val fileCodec: Provider<EncryptedFileCodec>,
) : CoroutineWorker(context, params) {
    private val file: RemoteFile by lazy {
        when {
            inputData.hasKeyWithValueOfType<String>(ARG_ENCRYPTED_URL) -> RemoteFile.Encrypted(
                url = inputData.getString(ARG_ENCRYPTED_URL)!!,
                key = Bytes(requireNotNull(inputData.getByteArray(ARG_ENCRYPTED_KEY)))
            )

            else -> RemoteFile.Community(
                communityServerBaseUrl = requireNotNull(inputData.getString(ARG_COMMUNITY_URL)) {
                    "RemoteFileDownloadWorker requires a community URL"
                },
                roomId = requireNotNull(inputData.getString(ARG_COMMUNITY_ROOM_ID)) {
                    "RemoteFileDownloadWorker requires a community room ID"
                },
                fileId = requireNotNull(inputData.getString(ARG_COMMUNITY_FILE_ID)) {
                    "RemoteFileDownloadWorker requires a community file ID"
                }
            )
        }
    }

    override suspend fun doWork(): Result {
        val files = computeFileNames(context, file)

        if (files.completedFile.exists()) {
            Log.i(TAG, "File already downloaded: $file")
            return Result.success()
        }

        if (files.permanentErrorMarkerFile.exists()) {
            Log.w(TAG, "Skipping downloading $file due to it being marked as a permanent error")
            return Result.failure()
        }

        Log.d(TAG, "Start downloading file from $file onto ${files.completedFile}")

        // A temp file to clear, if it exists
        var tmpFileToClean: File? = null

        try {
            val (bytes, meta) = downloadAndDecryptFile()
            Log.d(TAG, "Downloaded file from file server: $file")

            // Re-encrypt the file with our streaming cipher, and encode it with the metadata,
            // and doing it to a temporary file first.
            files.completedFile.parentFile!!.mkdirs()
            val tmpFile = File.createTempFile("downloaded-", null, files.completedFile.parentFile)
                .also { tmpFileToClean = it }

            EncryptionStream(
                out = fileCodec.get().encodeStream(
                    meta = meta,
                    outFile = tmpFile
                ),
                key = AttachmentSecretProvider.getInstance(context).orCreateAttachmentSecret.modernKey
            ).use { fos ->
                fos.write(bytes)
            }

            // Once done, rename the temporary file to the final file name.
            check(tmpFile.renameTo(files.completedFile)) {
                "Failed to rename temporary file ${tmpFile.absolutePath} to ${files.completedFile.absolutePath}"
            }

            // Since we successfully moved the file, we don't need to delete the temporary file anymore.
            tmpFileToClean = null
            return Result.success()
        } catch (e: CancellationException) {
            Log.i(TAG, "Download cancelled for file $file")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download file $file", e)
            return if (e is NonRetryableException || (e.getRootCause<OnionRequestAPI.HTTPRequestFailedAtDestinationException>())?.statusCode == 404) {
                files.permanentErrorMarkerFile.parentFile?.mkdirs()
                if (!files.permanentErrorMarkerFile.createNewFile()) {
                    Log.w(
                        TAG,
                        "Failed to create permanent error marker file: ${files.permanentErrorMarkerFile}"
                    )
                }

                Result.failure()
            } else {
                Result.retry()
            }
        } finally {
            tmpFileToClean?.delete()
        }
    }

    private suspend fun downloadAndDecryptFile(): Pair<ByteArraySlice, EncryptedFileMeta> {
        return when (val file = file) {
            is RemoteFile.Encrypted -> {
                val fileId = requireNotNull(FileServerApi.getFileIdFromUrl(file.url)) {
                    "RemoteFileDownloadWorker currently only supports downloading files from Session's file server"
                }

                val response = FileServerApi.download(fileId).await()
                Log.d(TAG, "Downloaded file from file server: $file")

                // Decrypt data
                val decrypted = AESGCM.decrypt(
                    ivAndCiphertext = response.body.data,
                    offset = response.body.offset,
                    len = response.body.len,
                    symmetricKey = file.key.data
                )

                decrypted.view() to
                        EncryptedFileMeta(expiryTimeEpochSeconds = response.expires?.toEpochSecond() ?: 0L)

            }

            is RemoteFile.Community -> {
                val data = OpenGroupApi.download(
                    fileId = file.fileId,
                    room = file.roomId,
                    server = file.communityServerBaseUrl
                ).await()

                data to EncryptedFileMeta()
            }
        }
    }


    data class DownloadedFiles(
        val completedFile: File,
        val permanentErrorMarkerFile: File = File(
            completedFile.parentFile,
            "${completedFile.name}.error"
        )
    )

    companion object {
        const val TAG = "RemoteFileDownloadWorker"

        private const val ARG_ENCRYPTED_URL = "encrypted_url"
        private const val ARG_ENCRYPTED_KEY = "encrypted_key"

        private const val ARG_COMMUNITY_URL = "community_url"
        private const val ARG_COMMUNITY_ROOM_ID = "community_room_id"
        private const val ARG_COMMUNITY_FILE_ID = "community_file_id"

        private fun RemoteFile.sha256Hash(): String {
            val hash = MessageDigest.getInstance("SHA-256")
            when (this) {
                is RemoteFile.Encrypted -> {
                    hash.update(url.lowercase().trim().toByteArray())
                    hash.update(key.data)
                }

                is RemoteFile.Community -> {
                    hash.update(communityServerBaseUrl.lowercase().trim().toByteArray())
                    hash.update(roomId.lowercase().trim().toByteArray())
                    hash.update(fileId.lowercase().trim().toByteArray())
                }
            }
            return hash.digest().toHexString()
        }

        // Deterministically get the file path for the given remote file.
        fun computeFileNames(context: Context, remote: RemoteFile): DownloadedFiles {
            return DownloadedFiles(
                completedFile = File(context.cacheDir, "remote_files/${remote.sha256Hash()}")
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }

        private fun uniqueWorkName(remote: RemoteFile): String {
            return "download-remote-file-${remote.sha256Hash()}"
        }

        fun cancel(context: Context, file: RemoteFile): Operation {
            return WorkManager.getInstance(context).cancelUniqueWork(
                uniqueWorkName(file)
            )
        }

        fun enqueue(context: Context, file: RemoteFile): Flow<WorkInfo?> {
            val input = when (file) {
                is RemoteFile.Encrypted -> Data.Builder()
                    .putString(ARG_ENCRYPTED_URL, file.url)
                    .putByteArray(ARG_ENCRYPTED_KEY, file.key.data)
                    .build()

                is RemoteFile.Community -> Data.Builder()
                    .putString(ARG_COMMUNITY_URL, file.communityServerBaseUrl)
                    .putString(ARG_COMMUNITY_ROOM_ID, file.roomId)
                    .putString(ARG_COMMUNITY_FILE_ID, file.fileId)
                    .build()
            }

            val request = OneTimeWorkRequestBuilder<RemoteFileDownloadWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(5))
                .addTag(TAG)
                .setInputData(input)
                .build()

            val workName = uniqueWorkName(file)
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    workName,
                    ExistingWorkPolicy.KEEP,
                    request
                )

            return WorkManager.getInstance(context)
                .getWorkInfoByIdFlow(request.id)
        }
    }
}