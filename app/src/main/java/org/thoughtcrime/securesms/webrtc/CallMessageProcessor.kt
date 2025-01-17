package org.thoughtcrime.securesms.webrtc

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.calls.CallMessageType
import org.session.libsession.messaging.messages.control.CallMessage
import org.session.libsession.messaging.utilities.WebRtcUtils
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.protos.SignalServiceProtos.CallMessage.Type.ANSWER
import org.session.libsignal.protos.SignalServiceProtos.CallMessage.Type.END_CALL
import org.session.libsignal.protos.SignalServiceProtos.CallMessage.Type.ICE_CANDIDATES
import org.session.libsignal.protos.SignalServiceProtos.CallMessage.Type.OFFER
import org.session.libsignal.protos.SignalServiceProtos.CallMessage.Type.PRE_OFFER
import org.session.libsignal.protos.SignalServiceProtos.CallMessage.Type.PROVISIONAL_ANSWER
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.service.WebRtcCallService
import org.thoughtcrime.securesms.util.CallNotificationBuilder
import org.thoughtcrime.securesms.util.CallNotificationBuilder.Companion.TYPE_INCOMING_PRE_OFFER
import org.thoughtcrime.securesms.util.CallNotificationBuilder.Companion.WEBRTC_NOTIFICATION
import org.webrtc.IceCandidate
import java.util.UUID

class CallMessageProcessor(private val context: Context, private val textSecurePreferences: TextSecurePreferences, lifecycle: Lifecycle, private val storage: StorageProtocol) {

    companion object {
        private const val TAG = "CallMessageProcessor"
        private const val VERY_EXPIRED_TIME = 15 * 60 * 1000L

        /**
         * This property is set to hold call data when the foreground service fails to start due to the app being in the the bg or killed
         * The presence of this property indicates that the call process was started by an independent fullscreen notification
         * as opposed to via the service itself (which, again, couldn't be started first due to the reason listed above).
         * This property will allow us to start a fullscreen notification which in turn will start the service where the usual processes can continue
         */
        var incomingCallData: IncomingCallMetadata? = null

        fun safeStartForegroundService(context: Context, intent: Intent, metadata: IncomingCallMetadata? = null) {
            Log.d("", "Safe start fs in callMessageProcessor: ${intent.action}")

            // Attempt to start the call service
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                // If the app was background or killed by the system, it won't be possible to start the service,
                // as the system forbids services being started from the background
                // In order to still react to the incoming call we can instead manually construct the fullscreen notification
                // which is presented in an activity, which in turn will start the service (on which it depends)

                Log.d("", "*** Failed to start foreground service. Is there metadata to start the fullscreen webRTC activity?: $metadata")

                // if we can send notification AND we have some set metadata
                // then send a fullscreen one that will notify the user of the call
                // We need the  metadata as it is the indicator that the specific reason for wanting to start the service above
                // was due to an incoming call.
                if (
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED &&
                    metadata != null
                ) {
                    incomingCallData = metadata
                    NotificationManagerCompat.from(context).notify(
                        WEBRTC_NOTIFICATION,
                        CallNotificationBuilder.getCallInProgressNotification(
                            context,
                            TYPE_INCOMING_PRE_OFFER,
                            Recipient.from(context, metadata.recipientAddress, true)
                        )
                    )
                }
            }
        }
    }

    init {
        lifecycle.coroutineScope.launch(IO) {
            while (isActive) {
                val nextMessage = WebRtcUtils.SIGNAL_QUEUE.receive()
                Log.d("Loki", nextMessage.type?.name ?: "CALL MESSAGE RECEIVED")
                val sender = nextMessage.sender ?: continue
                val approvedContact = Recipient.from(context, Address.fromSerialized(sender), false).isApproved
                Log.i("Loki", "Contact is approved?: $approvedContact")
                if (!approvedContact && storage.getUserPublicKey() != sender) continue

                // If the user has not enabled voice/video calls or if the user has not granted audio/microphone permissions
                if (
                    !textSecurePreferences.isCallNotificationsEnabled() ||
                        !Permissions.hasAll(context, Manifest.permission.RECORD_AUDIO)
                    ) {
                    Log.d("Loki","Dropping call message if call notifications disabled")
                    if (nextMessage.type != PRE_OFFER) continue
                    val sentTimestamp = nextMessage.sentTimestamp ?: continue
                    insertMissedCall(sender, sentTimestamp)
                    continue
                }

                val isVeryExpired = (nextMessage.sentTimestamp?:0) + VERY_EXPIRED_TIME < SnodeAPI.nowWithOffset
                if (isVeryExpired) {
                    Log.e("Loki", "Dropping very expired call message")
                    continue
                }

                when (nextMessage.type) {
                    OFFER -> incomingCall(nextMessage)
                    ANSWER -> incomingAnswer(nextMessage)
                    END_CALL -> incomingHangup(nextMessage)
                    ICE_CANDIDATES -> handleIceCandidates(nextMessage)
                    PRE_OFFER -> incomingPreOffer(nextMessage)
                    PROVISIONAL_ANSWER, null -> {} // TODO: if necessary
                }
            }
        }
    }

    private fun insertMissedCall(sender: String, sentTimestamp: Long) {
        val currentUserPublicKey = storage.getUserPublicKey()
        if (sender == currentUserPublicKey) return // don't insert a "missed" due to call notifications disabled if it's our own sender
        storage.insertCallMessage(sender, CallMessageType.CALL_MISSED, sentTimestamp)
    }

    private fun incomingHangup(callMessage: CallMessage) {
        Log.d("", "*** CallMessageProcessor: incomingHangup")
        val callId = callMessage.callId ?: return
        val hangupIntent = WebRtcCallService.remoteHangupIntent(context, callId)
        safeStartForegroundService(context, hangupIntent)
    }

    private fun incomingAnswer(callMessage: CallMessage) {
        Log.d("", "*** CallMessageProcessor: incomingAnswer")
        val recipientAddress = callMessage.sender ?: return Log.w(TAG, "Cannot answer incoming call without sender")
        val callId = callMessage.callId ?: return Log.w(TAG, "Cannot answer incoming call without callId" )
        val sdp = callMessage.sdps.firstOrNull() ?: return Log.w(TAG, "Cannot answer incoming call without sdp")
        val answerIntent = WebRtcCallService.incomingAnswer(
                context = context,
                address = Address.fromSerialized(recipientAddress),
                sdp = sdp,
                callId = callId
        )
        safeStartForegroundService(context, answerIntent)
    }

    private fun handleIceCandidates(callMessage: CallMessage) {
        Log.d("", "*** CallMessageProcessor: handleIceCandidates")
        val callId = callMessage.callId ?: return
        val sender = callMessage.sender ?: return

        val iceCandidates = callMessage.iceCandidates()
        if (iceCandidates.isEmpty()) return

        val iceIntent = WebRtcCallService.iceCandidates(
                context = context,
                iceCandidates = iceCandidates,
                callId = callId,
                address = Address.fromSerialized(sender)
        )
        safeStartForegroundService(context, iceIntent)
    }

    private fun incomingPreOffer(callMessage: CallMessage) {
        // handle notification state
        Log.d("", "*** CallMessageProcessor: incomingPreOffer")
        val recipientAddress = callMessage.sender ?: return
        val callId = callMessage.callId ?: return
        val incomingIntent = WebRtcCallService.preOffer(
                context = context,
                address = Address.fromSerialized(recipientAddress),
                callId = callId,
                callTime = callMessage.sentTimestamp!!
        )
        safeStartForegroundService(context, incomingIntent, IncomingCallMetadata(
            recipientAddress = Address.fromSerialized(recipientAddress),
            callId = callId,
            callTime = callMessage.sentTimestamp!!
        ))
    }

    private fun incomingCall(callMessage: CallMessage) {
        Log.d("", "*** CallMessageProcessor: incomingCall")
        val recipientAddress = callMessage.sender ?: return
        val callId = callMessage.callId ?: return
        val sdp = callMessage.sdps.firstOrNull() ?: return
        val incomingIntent = WebRtcCallService.incomingCall(
                context = context,
                address = Address.fromSerialized(recipientAddress),
                sdp = sdp,
                callId = callId,
                callTime = callMessage.sentTimestamp!!
        )
        safeStartForegroundService(context, incomingIntent)
    }

    data class IncomingCallMetadata(
        val recipientAddress: Address,
        val callId: UUID,
        val callTime: Long
    )

    private fun CallMessage.iceCandidates(): List<IceCandidate> {
        if (sdpMids.size != sdpMLineIndexes.size || sdpMLineIndexes.size != sdps.size) {
            return listOf() // uneven sdp numbers
        }
        val candidateSize = sdpMids.size
        return (0 until candidateSize).map { i ->
            IceCandidate(sdpMids[i], sdpMLineIndexes[i], sdps[i])
        }
    }

}