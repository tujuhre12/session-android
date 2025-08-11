package org.thoughtcrime.securesms.attachments

import android.app.Application
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import network.loki.messenger.libsession_util.encrypt.DecryptionStream
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import org.thoughtcrime.securesms.glide.EncryptedFileCodec
import org.thoughtcrime.securesms.glide.EncryptedFileMeta
import java.io.File
import java.io.InputStream

/**
 * A class to handle reading the locally encrypted file content and metadata.
 *
 * It uses [org.thoughtcrime.securesms.glide.EncryptedFileCodec] to decode the file to grab the metadata, and uses
 * [network.loki.messenger.libsession_util.encrypt.DecryptionStream] to decrypt the file content.
 *
 * If there's problem reading the file, it will throw an exception on construction.
 *
 * This class is paired with [LocalEncryptedFileOutputStream] for writing encrypted files.
 */
class LocalEncryptedFileInputStream @AssistedInject constructor(
    @Assisted file: File,
    codec: EncryptedFileCodec,
    application: Application
): InputStream() {
    private val decoded = codec.decodeStream(file)

    val meta: EncryptedFileMeta get() = decoded.first

    private val inputStream: InputStream by lazy {
        DecryptionStream(
            inStream = decoded.second,
            key = AttachmentSecretProvider.getInstance(application).orCreateAttachmentSecret.modernKey,
            autoClose = false
        )
    }

    override fun read(): Int {
        return inputStream.read()
    }

    override fun read(b: ByteArray?): Int {
        return inputStream.read(b)
    }

    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        return inputStream.read(b, off, len)
    }

    override fun skip(n: Long): Long {
        return inputStream.skip(n)
    }

    override fun available(): Int {
        return inputStream.available()
    }

    override fun close() {
        super.close()
        decoded.second.close()
    }

    override fun reset() {
        inputStream.reset()
    }

    override fun mark(readlimit: Int) {
        inputStream.mark(readlimit)
    }

    override fun markSupported(): Boolean {
        return inputStream.markSupported()
    }

    @AssistedFactory
    interface Factory {
        fun create(file: File): LocalEncryptedFileInputStream
    }
}