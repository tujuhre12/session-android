package org.session.libsession.messaging.messages

import org.session.libsignal.protos.SignalServiceProtos.Content.ExpirationType

class ExpirationConfiguration(
    val threadId: Long = -1,
    val durationSeconds: Int = 0,
    val expirationType: ExpirationType? = null,
    val updatedTimestampMs: Long = 0
) {
    val isEnabled = durationSeconds > 0
}