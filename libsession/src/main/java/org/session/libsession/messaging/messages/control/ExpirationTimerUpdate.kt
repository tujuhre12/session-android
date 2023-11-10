package org.session.libsession.messaging.messages.control

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Log

/** In the case of a sync message, the public key of the person the message was targeted at.
 *
 * **Note:** `nil` if this isn't a sync message.
 */
data class ExpirationTimerUpdate(var duration: Int? = 0, var syncTarget: String? = null) : ControlMessage() {

    override val isSelfSendValid: Boolean = true

    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        return duration != null || ExpirationConfiguration.isNewConfigEnabled
    }

    companion object {
        const val TAG = "ExpirationTimerUpdate"

        fun fromProto(proto: SignalServiceProtos.Content): ExpirationTimerUpdate? {
            val dataMessageProto = if (proto.hasDataMessage()) proto.dataMessage else return null
            val isExpirationTimerUpdate = dataMessageProto.flags.and(
                SignalServiceProtos.DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE
            ) != 0
            if (!isExpirationTimerUpdate) return null
            val syncTarget = dataMessageProto.syncTarget
            val duration = if (proto.hasExpirationTimer()) proto.expirationTimer else dataMessageProto.expireTimer
            return ExpirationTimerUpdate(duration, syncTarget)
        }
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val dataMessageProto = SignalServiceProtos.DataMessage.newBuilder()
        dataMessageProto.flags = SignalServiceProtos.DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE
        duration?.let { dataMessageProto.expireTimer = it }
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