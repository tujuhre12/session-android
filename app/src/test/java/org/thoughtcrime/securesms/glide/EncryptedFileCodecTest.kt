package org.thoughtcrime.securesms.glide

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EncryptedFileCodecTest {

    @Test
    fun `encode and decode works`() {
        val codec = EncryptedFileCodec(Json {})

        // Create a sample metadata object
        val expectMeta = EncryptedFileMeta(
            expiryTimeEpochSeconds = System.currentTimeMillis(),
        )

        val expectContent = "Hello, World!".toByteArray()

        // Encode the metadata to a temporary file
        val tempFile = File.createTempFile("encrypted-file", ".tmp")
        codec.encodeStream(expectMeta, tempFile).use {
            it.write(expectContent)
        }

        // Decode the metadata from the temporary file
        val (actualMeta, fis) = codec.decodeStream(tempFile)
        val actualByte = fis.use { it.readAllBytes() }

        // Verify the metadata and content
        assertEquals(expectMeta, actualMeta)
        assertArrayEquals(expectContent, actualByte)

        tempFile.delete()
    }
}