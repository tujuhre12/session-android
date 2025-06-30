package org.thoughtcrime.securesms.glide

import android.content.Context
import androidx.work.await
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.session.libsession.messaging.file_server.FileServerApi
import org.session.libsession.utilities.AESGCM
import org.session.libsession.utilities.recipients.RemoteFile
import org.session.libsignal.exceptions.NonRetryableException
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import java.security.MessageDigest

class RemoteFileLoader(
    private val context: Context,
) : ModelLoader<RemoteFile, ByteArray> {
    override fun buildLoadData(
        model: RemoteFile,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<ByteArray> {
        return ModelLoader.LoadData(
            RemoteFileKey(model),
            RemoteFileDataFetcher(model)
        )
    }

    private inner class RemoteFileDataFetcher(private val file: RemoteFile) : DataFetcher<ByteArray> {
        private var job: Job? = null

        override fun loadData(
            priority: Priority,
            callback: DataFetcher.DataCallback<in ByteArray>
        ) {
            job = GlobalScope.launch {
                try {
                    val files: RemoteFileDownloadWorker.DownloadedFiles
                    val encryptionKey: ByteArray

                    when (file) {
                        is RemoteFile.Encrypted -> {
                            val fileId = requireNotNull(FileServerApi.getFileIdFromUrl(file.url)) {
                                "Target URL is not supported, must be a session file server url but got: ${file.url}"
                            }

                            files = EncryptedFileDownloadWorker.getFileForUrl(
                                context,
                                RecipientAvatarDownloadManager.CACHE_FOLDER_NAME,
                                fileId
                            )

                            if (!files.permanentErrorMarkerFile.exists() && files.completedFile.exists()) {
                                // Files not exists, enqueue a download
                                EncryptedFileDownloadWorker.enqueue(
                                    context = context,
                                    fileId = fileId,
                                    cacheFolderName = RecipientAvatarDownloadManager.CACHE_FOLDER_NAME
                                ).await()
                            }

                            encryptionKey = file.key.data
                        }

                        is RemoteFile.Community -> {
                            files = CommunityFileDownloadWorker.getFileForUrl(context, file)

                            if (!files.permanentErrorMarkerFile.exists() && files.completedFile.exists()) {
                                // Files not exists, enqueue a download
                                CommunityFileDownloadWorker.enqueue(context, file).await()
                            }

                            encryptionKey = AttachmentSecretProvider.getInstance(context)
                                .orCreateAttachmentSecret.modernKey
                        }
                    }


                    if (files.permanentErrorMarkerFile.exists()) {
                        throw NonRetryableException("Requested file is marked as a permanent error:")
                    }

                    check(files.completedFile.exists()) {
                        "File not downloaded but no reason is given. Most likely a bug in the download worker."
                    }

                    callback.onDataReady(AESGCM.decrypt(files.completedFile.readBytes(), symmetricKey = encryptionKey))

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    callback.onLoadFailed(e)
                }
            }
        }

        override fun cleanup() {
            job?.cancel()
            job = null
        }

        override fun cancel() {
            cleanup()
        }

        override fun getDataClass(): Class<ByteArray> = ByteArray::class.java
        override fun getDataSource(): DataSource = DataSource.REMOTE
    }

    private data class RemoteFileKey(val file: RemoteFile) : Key {
        override fun updateDiskCacheKey(messageDigest: MessageDigest) {
            when (file) {
                is RemoteFile.Community -> {
                    messageDigest.update(file.communityServerBaseUrl.toByteArray())
                    messageDigest.update(file.roomId.toByteArray())
                    messageDigest.update(file.fileId.toByteArray())
                }

                is RemoteFile.Encrypted -> {
                    messageDigest.update(file.url.toByteArray())
                    messageDigest.update(file.key.data)
                }
            }
        }
    }

    override fun handles(model: RemoteFile): Boolean = true

    class Factory(private val context: Context) : ModelLoaderFactory<RemoteFile, ByteArray> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<RemoteFile, ByteArray> {
            return RemoteFileLoader(context)
        }

        override fun teardown() {}
    }
}