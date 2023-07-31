package org.session.libsession.utilities

import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsignal.protos.SignalServiceProtos
import kotlin.reflect.KClass

fun ExpiryMode?.typeRadioIndex(): Int {
    return when (this) {
        is ExpiryMode.AfterRead -> SignalServiceProtos.Content.ExpirationType.DELETE_AFTER_READ_VALUE
        is ExpiryMode.AfterSend -> SignalServiceProtos.Content.ExpirationType.DELETE_AFTER_SEND_VALUE
        else -> -1
    }
}

fun SignalServiceProtos.Content.ExpirationType?.expiryMode(durationSeconds: Long): ExpiryMode? = when (this) {
    null -> null
    SignalServiceProtos.Content.ExpirationType.DELETE_AFTER_READ -> ExpiryMode.AfterRead(durationSeconds)
    SignalServiceProtos.Content.ExpirationType.DELETE_AFTER_SEND -> ExpiryMode.AfterSend(durationSeconds)
    SignalServiceProtos.Content.ExpirationType.UNKNOWN -> null
}

fun Int.expiryType(): KClass<out ExpiryMode>? {
    if (this == -1) return null
    return when (this) {
        SignalServiceProtos.Content.ExpirationType.DELETE_AFTER_READ_VALUE -> ExpiryMode.AfterSend::class
        SignalServiceProtos.Content.ExpirationType.DELETE_AFTER_SEND_VALUE -> ExpiryMode.AfterRead::class
        else -> ExpiryMode.NONE::class
    }
}