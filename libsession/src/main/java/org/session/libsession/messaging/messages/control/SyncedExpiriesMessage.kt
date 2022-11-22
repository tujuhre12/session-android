package org.session.libsession.messaging.messages.control

import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.protos.SignalServiceProtos.SyncedExpiries.SyncedConversationExpiries
import org.session.libsignal.utilities.Log

class SyncedExpiriesMessage(): ControlMessage() {
    var conversationExpiries: Map<String, List<SyncedExpiry>> = emptyMap()

    override val isSelfSendValid: Boolean = true

    // region Validation
    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        return conversationExpiries.isNotEmpty()
    }
    // endregion

    companion object {
        const val TAG = "SyncedExpiriesMessage"

        fun fromProto(proto: SignalServiceProtos.Content): SyncedExpiriesMessage? {
            val syncedExpiriesProto = if (proto.hasSyncedExpiries()) proto.syncedExpiries else return null
            val conversationExpiries = syncedExpiriesProto.conversationExpiriesList.associate {
                it.syncTarget to it.expiriesList.map { syncedExpiry -> SyncedExpiry.fromProto(syncedExpiry) }
            }
            return SyncedExpiriesMessage(conversationExpiries)
        }
    }

    constructor(conversationExpiries: Map<String, List<SyncedExpiry>>) : this() {
        this.conversationExpiries = conversationExpiries
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val conversationExpiries = conversationExpiries
        if (conversationExpiries.isEmpty()) {
            Log.w(TAG, "Couldn't construct synced expiries proto from: $this")
            return null
        }
        val conversationExpiriesProto = conversationExpiries.map { (syncTarget, syncedExpiries) ->
            val expiriesProto = syncedExpiries.map(SyncedExpiry::toProto)
            val syncedConversationExpiriesProto = SyncedConversationExpiries.newBuilder()
            syncedConversationExpiriesProto.syncTarget = syncTarget
            syncedConversationExpiriesProto.addAllExpiries(expiriesProto)
            syncedConversationExpiriesProto.build()
        }
        val syncedExpiriesProto = SignalServiceProtos.SyncedExpiries.newBuilder()
        syncedExpiriesProto.addAllConversationExpiries(conversationExpiriesProto)
        val contentProto = SignalServiceProtos.Content.newBuilder()
        return try {
            contentProto.syncedExpiries = syncedExpiriesProto.build()
            setExpirationConfigurationIfNeeded(contentProto)
            contentProto.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct synced expiries proto from: $this")
            null
        }
    }

}