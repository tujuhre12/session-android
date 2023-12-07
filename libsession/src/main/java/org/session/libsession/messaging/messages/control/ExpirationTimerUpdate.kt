package org.session.libsession.messaging.messages.control

import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Log

/** In the case of a sync message, the public key of the person the message was targeted at.
 *
 * **Note:** `nil` if this isn't a sync message.
 */
data class ExpirationTimerUpdate(var expiryMode: ExpiryMode, var syncTarget: String? = null) : ControlMessage() {

    override val isSelfSendValid: Boolean = true

    companion object {
        const val TAG = "ExpirationTimerUpdate"

        fun fromProto(proto: SignalServiceProtos.Content): ExpirationTimerUpdate? {
            val dataMessageProto = if (proto.hasDataMessage()) proto.dataMessage else return null
            val isExpirationTimerUpdate = dataMessageProto.flags.and(
                SignalServiceProtos.DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE
            ) != 0
            if (!isExpirationTimerUpdate) return null
            val syncTarget = dataMessageProto.syncTarget
            val duration: Int = if (proto.hasExpirationTimer()) proto.expirationTimer else dataMessageProto.expireTimer
            val type = proto.expirationType.takeIf { duration > 0 }
            val expiryMode = when (type) {
                SignalServiceProtos.Content.ExpirationType.DELETE_AFTER_SEND -> ExpiryMode.AfterSend(duration.toLong())
                SignalServiceProtos.Content.ExpirationType.DELETE_AFTER_READ -> ExpiryMode.AfterRead(duration.toLong())
                else -> duration.takeIf { it > 0 }?.toLong()?.let(ExpiryMode::AfterSend) ?: ExpiryMode.NONE
            }

            return ExpirationTimerUpdate(expiryMode, syncTarget)
        }
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val dataMessageProto = SignalServiceProtos.DataMessage.newBuilder()
        dataMessageProto.flags = SignalServiceProtos.DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE
        dataMessageProto.expireTimer = expiryMode.expirySeconds.toInt()
        // Sync target
        if (syncTarget != null) {
            dataMessageProto.syncTarget = syncTarget
        }
        // Group context
        if (MessagingModuleConfiguration.shared.storage.isClosedGroup(recipient!!)) {
            try {
                setGroupContext(dataMessageProto)
            } catch(e: Exception) {
                Log.w(VisibleMessage.TAG, "Couldn't construct visible message proto from: $this")
                return null
            }
        }
        return try {
            SignalServiceProtos.Content.newBuilder().apply {
                dataMessage = dataMessageProto.build()
                setExpirationConfigurationIfNeeded(threadID)
            }.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct expiration timer update proto from: $this")
            null
        }
    }
}