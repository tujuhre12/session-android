package org.session.libsession.utilities.serializable

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Serializes and deserializes [ZonedDateTime] as a long representing milliseconds since epoch in UTC.
 */
class ZonedDateTimeMillsSerializer : KSerializer<ZonedDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("org.session.ZonedDateTimeLong", PrimitiveKind.LONG)

    override fun serialize(
        encoder: Encoder,
        value: ZonedDateTime
    ) {
        encoder.encodeLong(value.toInstant().toEpochMilli())
    }

    override fun deserialize(decoder: Decoder): ZonedDateTime {
        val mills = decoder.decodeLong()
        return ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(mills),
            ZoneId.of("UTC")
        )
    }
}