package org.session.libsession.utilities.recipients

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.thoughtcrime.securesms.database.model.NotifyType
import java.time.ZonedDateTime

/**
 * Returns the effective notify type for the recipient, taking account of the time the recipient
 * should be muted for.
 *
 * @param now The current time, defaults to the current system time.
 */
fun Recipient.effectiveNotifyType(now: ZonedDateTime = ZonedDateTime.now()): NotifyType {
    if (mutedUntil != null && now.isBefore(mutedUntil)) {
        return NotifyType.NONE
    }

    return notifyType
}

/**
 * Observes the effective notify type for the recipient, taking account of the time the recipient
 * should be muted for.
 *
 * @param now The current time, defaults to the current system time.
 */
fun Recipient.observeEffectiveNotifyType(now: ZonedDateTime = ZonedDateTime.now()): Flow<NotifyType> {
    return if (mutedUntil != null && now.isBefore(mutedUntil)) {
        flow {
            emit(NotifyType.NONE)
            val expirationTime = mutedUntil.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()
            delay(expirationTime)
            emit(notifyType)
        }
    } else {
        flowOf(notifyType)
    }
}