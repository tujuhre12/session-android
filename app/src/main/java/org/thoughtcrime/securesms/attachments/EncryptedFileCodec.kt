package org.thoughtcrime.securesms.glide

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * Codec for reading and writing encrypted file metadata and content on disk.
 *
 * Normally when we download an encrypted file from Session's file server, we also want to store
 * additional metadata about the file, such as its expiry time. The best way to do this is to
 * save it along side the file content in a single file. This codec handles that.
 *
 * You can safely extend the `EncryptedFileMeta` class to add more metadata fields in the future,
 * as long as you ensure that the metadata is serialized in a old version compatible way (such as
 * making them optional properties by default).
 */
class EncryptedFileCodec @Inject constructor(
    private val json: Json
) {
    @OptIn(ExperimentalSerializationApi::class)
    fun decodeStream(file: File): Pair<EncryptedFileMeta, InputStream> {
        val ins = FileInputStream(file)

        try {
            // First 4 bytes are the length of the metadata
            val lengthBytes = ByteArray(4)
            check(ins.read(lengthBytes) == lengthBytes.size) {
                "Failed to read the length of the metadata from the input stream."
            }

            // Convert the length bytes to an integer as BE
            val length: Int = ByteBuffer.wrap(lengthBytes).int

            // Deserialize the metadata
            val metaBytes = ByteArray(length)
            check(ins.read(metaBytes) == metaBytes.size) {
                "Failed to read the metadata from the input stream. Expected $length bytes, but got less."
            }

            val meta = json.decodeFromStream<EncryptedFileMeta>(ByteArrayInputStream(metaBytes))

            return meta to ins
        } catch (e: Exception) {
            ins.close()
            throw e
        }
    }

    fun encodeStream(meta: EncryptedFileMeta, outFile: File): OutputStream {
        val fos = FileOutputStream(outFile, false)

        try {
            // Encode the metadata as JSON
            val metaJsonBytes = json.encodeToString(meta).toByteArray(Charsets.UTF_8)
            val lengthBytes = ByteBuffer.allocate(4).putInt(metaJsonBytes.size).array()

            // Write the length of the metadata
            fos.write(lengthBytes)
            // Write the metadata JSON
            fos.write(metaJsonBytes)

            return fos
        } catch (e: Exception) {
            fos.close()
            throw e
        }
    }
}


/**
 * Metadata for an encrypted file.
 *
 * Note: you should not change this class without considering backward compatibility,
 * especially if you add non-nullable properties, the old metadata won't be able to be deserialized.
 *
 * As a rule of thumb, you can add new optional properties, but avoid changing existing ones(even renaming).
 */
@Serializable
data class EncryptedFileMeta(
    val expiryTimeEpochSeconds: Long = 0L,
)