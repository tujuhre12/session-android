package org.thoughtcrime.securesms.attachments

import android.app.Application
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import network.loki.messenger.libsession_util.encrypt.EncryptionStream
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import org.thoughtcrime.securesms.glide.EncryptedFileCodec
import org.thoughtcrime.securesms.glide.EncryptedFileMeta
import java.io.File
import java.io.OutputStream

/**
 * A class to handle writing encrypted file content and metadata to a local file.
 *
 * It uses [EncryptedFileCodec] to encode the metadata and file content, and
 * [EncryptionStream] to encrypt the file content.
 *
 * This class is paired with [LocalEncryptedFileInputStream] for reading encrypted files.
 */
class LocalEncryptedFileOutputStream @AssistedInject constructor(
    @Assisted file: File,
    @Assisted meta: EncryptedFileMeta,
    codec: EncryptedFileCodec,
    application: Application
): OutputStream() {
    private val outputStream: OutputStream by lazy {
        EncryptionStream(
            out = codec.encodeStream(outFile = file, meta = meta),
            key = AttachmentSecretProvider.getInstance(application).orCreateAttachmentSecret.modernKey,
        )
    }

    override fun write(b: Int) {
        outputStream.write(b)
    }

    override fun write(b: ByteArray) {
        outputStream.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        outputStream.write(b, off, len)
    }

    override fun flush() {
        super.flush()
        outputStream.flush()
    }

    override fun close() {
        super.close()
        outputStream.close()
    }

    @AssistedFactory
    interface Factory {
        fun create(file: File, meta: EncryptedFileMeta): LocalEncryptedFileOutputStream
    }
}