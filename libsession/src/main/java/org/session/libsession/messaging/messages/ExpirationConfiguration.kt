package org.session.libsession.messaging.messages

import org.session.libsignal.protos.SignalServiceProtos.Content.ExpirationType

class ExpirationConfiguration(
    val threadId: Long = -1,
    val durationSeconds: Int = 0,
    val expirationTypeValue: Int = -1,
    val updatedTimestampMs: Long = 0
) {
    val expirationType: ExpirationType? = ExpirationType.valueOf(expirationTypeValue)
    val isEnabled = durationSeconds > 0 && expirationType != null

    companion object {
        val isNewConfigEnabled = false /* TODO: System.currentTimeMillis() > 1_676_851_200_000 // 13/02/2023 */
        const val LAST_READ_TEST = 1673587663000L
    }
}