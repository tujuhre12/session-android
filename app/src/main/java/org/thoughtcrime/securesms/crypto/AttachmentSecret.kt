package org.thoughtcrime.securesms.crypto

import kotlinx.serialization.Serializable
import org.thoughtcrime.securesms.util.serialization.Base64Serializer


@Serializable
class AttachmentSecret(
    @Serializable(with = Base64Serializer::class)
    val classicCipherKey: ByteArray?,

    @Serializable(with = Base64Serializer::class)
    val classicMacKey: ByteArray?,

    @Serializable(with = Base64Serializer::class)
    val modernKey: ByteArray?
)
