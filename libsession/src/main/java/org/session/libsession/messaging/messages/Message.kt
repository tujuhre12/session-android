package org.session.libsession.messaging.messages

import org.session.libsignal.protos.SignalServiceProtos

abstract class Message {
    var id: Long? = null
    var threadID: Long? = null
    var sentTimestamp: Long? = null
    var receivedTimestamp: Long? = null
    var recipient: String? = null
    var sender: String? = null
    var groupPublicKey: String? = null
    var openGroupServerMessageID: Long? = null
    var serverHash: String? = null

    open val ttl: Long = 14 * 24 * 60 * 60 * 1000
    open val isSelfSendValid: Boolean = false

    open fun isValid(): Boolean {
        val sentTimestamp = sentTimestamp
        if (sentTimestamp != null && sentTimestamp <= 0) { return false }
        val receivedTimestamp = receivedTimestamp
        if (receivedTimestamp != null && receivedTimestamp <= 0) { return false }
        return sender != null && recipient != null
    }

    abstract fun toProto(): SignalServiceProtos.Content?

}