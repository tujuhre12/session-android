package org.session.libsession.utilities.recipients

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.jetbrains.annotations.Contract
import org.session.libsession.utilities.serializable.ZonedDateTimeMillsSerializer
import java.time.ZonedDateTime

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("status")
sealed interface ProStatus {
    @Serializable
    data class Pro(
        /**
         * Whether the Pro badge should be visible or not.
         */
        val visible: Boolean = true,

        /**
         * The validity of the Pro status, if null, it means the Pro status is permanent.
         */
        @Serializable(with = ZonedDateTimeMillsSerializer::class)
        val validUntil: ZonedDateTime? = null,
    ) : ProStatus

    @Serializable
    data object None : ProStatus
}

fun ProStatus.isPro(now: ZonedDateTime = ZonedDateTime.now()): Boolean {
    return this is ProStatus.Pro && (validUntil == null || validUntil.isAfter(now))
}

fun ProStatus.shouldShowProBadge(now: ZonedDateTime = ZonedDateTime.now()): Boolean {
    return isPro(now) && (this as ProStatus.Pro).visible
}
