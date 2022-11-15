package org.session.libsession.messaging.messages

import org.session.libsignal.protos.SignalServiceProtos.Content.ExpirationType

class ExpirationSettingsConfiguration(
    val threadId: Long = -1,
    val isEnabled: Boolean = false,
    val durationSeconds: Int = 0,
    val expirationType: ExpirationType? = null,
    val lastChangeTimestampMs: Long = 0
)