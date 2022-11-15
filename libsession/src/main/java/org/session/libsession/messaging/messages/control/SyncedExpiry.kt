package org.session.libsession.messaging.messages.control

import org.session.libsignal.protos.SignalServiceProtos.SyncedExpiries
import org.session.libsignal.utilities.Log

class SyncedExpiry(
    var serverHash: String? = null,
    var expirationTimestamp: Long? = null
) {

    fun toProto(): SyncedExpiries.SyncedConversationExpiries.SyncedExpiry? {
         val syncedExpiryProto = SyncedExpiries.SyncedConversationExpiries.SyncedExpiry.newBuilder()
         serverHash?.let { syncedExpiryProto.serverHash = it }
         expirationTimestamp?.let { syncedExpiryProto.expirationTimestamp = it }
         return try {
             syncedExpiryProto.build()
         } catch (e: Exception) {
             Log.w(TAG, "Couldn't construct synced expiry proto from: $this")
             null
         }
     }

     companion object {
         const val TAG = "SyncedExpiry"

        @JvmStatic
         fun fromProto(proto: SyncedExpiries.SyncedConversationExpiries.SyncedExpiry): SyncedExpiry {
             val result = SyncedExpiry()
             result.serverHash = if (proto.hasServerHash()) proto.serverHash else null
             result.expirationTimestamp = if (proto.hasServerHash()) proto.expirationTimestamp else null
             return SyncedExpiry()
         }
     }

 }