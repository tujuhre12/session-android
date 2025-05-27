package org.thoughtcrime.securesms.util.serialization

import android.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class Base64Serializer : KSerializer<ByteArray> {
    private val base64Options = Base64.NO_WRAP or Base64.NO_PADDING

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        serialName = "org.session.base64",
        kind = PrimitiveKind.STRING
    )

    override fun deserialize(decoder: Decoder): ByteArray {
        return Base64.decode(decoder.decodeString(), base64Options)
    }

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(Base64.encodeToString(value, base64Options))
    }
}
