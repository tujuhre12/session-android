package org.thoughtcrime.securesms.webrtc

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.session.libsession.messaging.calls.CallMessageType
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.FutureTaskListener
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.notifications.BackgroundPollWorker
import org.thoughtcrime.securesms.service.CallForegroundService
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.Companion.TYPE_ESTABLISHED
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.Companion.TYPE_INCOMING_CONNECTING
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.Companion.TYPE_INCOMING_PRE_OFFER
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.Companion.TYPE_OUTGOING_RINGING
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.Companion.WEBRTC_NOTIFICATION
import org.thoughtcrime.securesms.webrtc.audio.OutgoingRinger
import org.thoughtcrime.securesms.webrtc.data.Event
import org.thoughtcrime.securesms.webrtc.locks.LockManager
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceConnectionState.CONNECTED
import org.webrtc.PeerConnection.IceConnectionState.DISCONNECTED
import org.webrtc.PeerConnection.IceConnectionState.FAILED
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import org.thoughtcrime.securesms.webrtc.data.State as CallState

//todo PHONE We want to eventually remove this bridging class and move the logic here to a better place, probably in the callManager
/**
 * A class that used to be an Android system in the old codebase and was replaced by a temporary bridging class ro simplify the transition away from
 * system services that handle the call logic. We had to avoid system services in order to circumvent the restrictions around starting a service when
 * the app is in the background or killed.
 * The idea is to eventually remove this class entirely and move its code in a better place (likely directly in the CallManager)
 */
@Singleton
class WebRtcCallBridge @Inject constructor(
    @ApplicationContext val context: Context,
    val callManager: CallManager
): CallManager.WebRtcListener  {

    companion object {

        private val TAG = Log.tag(WebRtcCallBridge::class.java)

        const val ACTION_INCOMING_RING = "RING_INCOMING"
        const val ACTION_OUTGOING_CALL = "CALL_OUTGOING"
        const val ACTION_ANSWER_CALL = "ANSWER_CALL"
        const val ACTION_IGNORE_CALL = "IGNORE_CALL" // like when swiping off a notification. Ends the call without notifying the caller
        const val ACTION_DENY_CALL = "DENY_CALL"
        const val ACTION_LOCAL_HANGUP = "LOCAL_HANGUP"
        const val ACTION_SET_MUTE_AUDIO = "SET_MUTE_AUDIO"
        const val ACTION_SET_MUTE_VIDEO = "SET_MUTE_VIDEO"
        const val ACTION_FLIP_CAMERA = "FLIP_CAMERA"
        const val ACTION_UPDATE_AUDIO = "UPDATE_AUDIO"
        const val ACTION_WIRED_HEADSET_CHANGE = "WIRED_HEADSET_CHANGE"
        const val ACTION_SCREEN_OFF = "SCREEN_OFF"
        const val ACTION_CHECK_TIMEOUT = "CHECK_TIMEOUT"
        const val ACTION_CHECK_RECONNECT = "CHECK_RECONNECT"
        const val ACTION_CHECK_RECONNECT_TIMEOUT = "CHECK_RECONNECT_TIMEOUT"
        const val ACTION_IS_IN_CALL_QUERY = "IS_IN_CALL"

        const val ACTION_PRE_OFFER = "PRE_OFFER"
        const val ACTION_ANSWER_INCOMING = "ANSWER_INCOMING"
        const val ACTION_ICE_MESSAGE = "ICE_MESSAGE"
        const val ACTION_REMOTE_HANGUP = "REMOTE_HANGUP"
        const val ACTION_ICE_CONNECTED = "ICE_CONNECTED"

        const val EXTRA_RECIPIENT_ADDRESS = "RECIPIENT_ID"
        const val EXTRA_ENABLED = "ENABLED"
        const val EXTRA_AUDIO_COMMAND = "AUDIO_COMMAND"
        const val EXTRA_SWAPPED = "is_video_swapped"
        const val EXTRA_MUTE = "mute_value"
        const val EXTRA_AVAILABLE = "enabled_value"
        const val EXTRA_REMOTE_DESCRIPTION = "remote_description"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_ICE_SDP = "ice_sdp"
        const val EXTRA_ICE_SDP_MID = "ice_sdp_mid"
        const val EXTRA_ICE_SDP_LINE_INDEX = "ice_sdp_line_index"
        const val EXTRA_RESULT_RECEIVER = "result_receiver"

        const val INVALID_NOTIFICATION_ID = -1
        private const val TIMEOUT_SECONDS = 30L
        private const val RECONNECT_SECONDS = 5L
        private const val MAX_RECONNECTS = 5

        fun cameraEnabled(context: Context, enabled: Boolean) =
            Intent(context, WebRtcCallBridge::class.java)
                .setAction(ACTION_SET_MUTE_VIDEO)
                .putExtra(EXTRA_MUTE, !enabled)

        fun flipCamera(context: Context) = Intent(context, WebRtcCallBridge::class.java)
            .setAction(ACTION_FLIP_CAMERA)

        fun acceptCallIntent(context: Context) = Intent(context, WebRtcCallBridge::class.java)
            .setAction(ACTION_ANSWER_CALL)

        fun microphoneIntent(context: Context, enabled: Boolean) =
            Intent(context, WebRtcCallBridge::class.java)
                .setAction(ACTION_SET_MUTE_AUDIO)
                .putExtra(EXTRA_MUTE, !enabled)

        fun createCall(context: Context, address: Address) =
            Intent(context, WebRtcCallBridge::class.java)
                .setAction(ACTION_OUTGOING_CALL)
                .putExtra(EXTRA_RECIPIENT_ADDRESS, address)

        fun incomingCall(
            context: Context,
            address: Address,
            sdp: String,
            callId: UUID,
            callTime: Long
        ) =
            Intent(context, WebRtcCallBridge::class.java)
                .setAction(ACTION_INCOMING_RING)
                .putExtra(EXTRA_RECIPIENT_ADDRESS, address)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_REMOTE_DESCRIPTION, sdp)
                .putExtra(EXTRA_TIMESTAMP, callTime)

        fun incomingAnswer(context: Context, address: Address, sdp: String, callId: UUID) =
            Intent(context, WebRtcCallBridge::class.java)
                .setAction(ACTION_ANSWER_INCOMING)
                .putExtra(EXTRA_RECIPIENT_ADDRESS, address)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_REMOTE_DESCRIPTION, sdp)

        fun preOffer(context: Context, address: Address, callId: UUID, callTime: Long) =
            Intent(context, WebRtcCallBridge::class.java)
                .setAction(ACTION_PRE_OFFER)
                .putExtra(EXTRA_RECIPIENT_ADDRESS, address)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_TIMESTAMP, callTime)

        fun iceCandidates(
            context: Context,
            address: Address,
            iceCandidates: List<IceCandidate>,
            callId: UUID
        ) =
            Intent(context, WebRtcCallBridge::class.java)
                .setAction(ACTION_ICE_MESSAGE)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_ICE_SDP, iceCandidates.map(IceCandidate::sdp).toTypedArray())
                .putExtra(
                    EXTRA_ICE_SDP_LINE_INDEX,
                    iceCandidates.map(IceCandidate::sdpMLineIndex).toIntArray()
                )
                .putExtra(EXTRA_ICE_SDP_MID, iceCandidates.map(IceCandidate::sdpMid).toTypedArray())
                .putExtra(EXTRA_RECIPIENT_ADDRESS, address)

        fun denyCallIntent(context: Context) =
            Intent(context, WebRtcCallBridge::class.java).setAction(ACTION_DENY_CALL)

        fun ignoreCallIntent(context: Context) =
            Intent(context, WebRtcCallBridge::class.java).setAction(ACTION_IGNORE_CALL)

        fun remoteHangupIntent(context: Context, callId: UUID) =
            Intent(context, WebRtcCallBridge::class.java)
                .setAction(ACTION_REMOTE_HANGUP)
                .putExtra(EXTRA_CALL_ID, callId)

        fun hangupIntent(context: Context) =
            Intent(context, WebRtcCallBridge::class.java).setAction(ACTION_LOCAL_HANGUP)

        fun audioManagerCommandIntent(context: Context, command: AudioManagerCommand) =
            Intent(context, WebRtcCallBridge::class.java)
                .setAction(ACTION_UPDATE_AUDIO)
                .putExtra(EXTRA_AUDIO_COMMAND, command)
    }

    private var wantsToAnswer = false
    private var currentTimeouts = 0
    private var isNetworkAvailable = true
    private var scheduledTimeout: ScheduledFuture<*>? = null
    private var scheduledReconnect: ScheduledFuture<*>? = null

    private val lockManager by lazy { LockManager(context) }
    private val serviceExecutor = Executors.newSingleThreadExecutor()
    private val timeoutExecutor = Executors.newScheduledThreadPool(1)

    private var networkChangedReceiver: NetworkChangeReceiver? = null
    private var wiredHeadsetStateReceiver: WiredHeadsetStateReceiver? = null
    private var uncaughtExceptionHandlerManager: UncaughtExceptionHandlerManager? = null
    private var powerButtonReceiver: PowerButtonReceiver? = null

    init {
        callManager.registerListener(this)
        wantsToAnswer = false
        isNetworkAvailable = true
        registerWiredHeadsetStateReceiver()
        registerUncaughtExceptionHandler()
        networkChangedReceiver = NetworkChangeReceiver(::networkChange)
        networkChangedReceiver!!.register(context)
    }


    @Synchronized
    private fun terminate() {
        Log.d(TAG, "*** Terminating rtc service")
        context.stopService(Intent(context, CallForegroundService::class.java))
        NotificationManagerCompat.from(context).cancel(WEBRTC_NOTIFICATION)
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(WebRtcCallActivity.ACTION_END))
        lockManager.updatePhoneState(LockManager.PhoneState.IDLE)
        callManager.stop()
        wantsToAnswer = false
        currentTimeouts = 0
        isNetworkAvailable = true
        scheduledTimeout?.cancel(false)
        scheduledReconnect?.cancel(false)
        scheduledTimeout = null
        scheduledReconnect = null
        callManager.postViewModelState(CallViewModel.State.CALL_INITIALIZING) // reset to default state


        //todo PHONE I got a 'missed call' notification after I declined a call. Is that right?
        //todo PHONE GETTING missed call notifications during all parts of a call: when picking up, hanging up, sometimes while swipping off a notificaiton ( from older notifications as they are still unseen ? )

        //todo PHONE [xxx Called you], which is a control message for a SUCCESSFUL call, should appear as unread, since you already know about the call - make it unread by default
        //todo PHONE It seems we can't call if the phone has been in sleep for a while. The call (sending) doesn't seem to do anything (not receiving anything) - stuck on "Creating call" - also same when receiving a call, it starts ok but gets stuck
        //todo PHONE test other receivers (proximity, headset, etc... )
        //todo PHONE should we refactor ice candidates to be
        //todo PHONE if I kill the activity the video freezes and when I tap on the notification to get back in the activity is broken - also hanging up form the other phone at that point doesn't seem to stop the call as the notification remains
        //todo PHONE I sometimes get stuck in a state where I accepted the call, it brings up the activity, but then it doesn't actually accept the call and I need to accept a second time


    }

    private fun isSameCall(intent: Intent): Boolean {
        val expectedCallId = getCallId(intent)
        return callManager.callId == expectedCallId
    }

    private fun isPreOffer() = callManager.isPreOffer()

    private fun isBusy(intent: Intent) = callManager.isBusy(context, getCallId(intent))

    private fun isIdle() = callManager.isIdle()

    override fun onHangup() {
        serviceExecutor.execute {
            callManager.handleRemoteHangup()

            if (callManager.currentConnectionState in CallState.CAN_DECLINE_STATES) {
                callManager.recipient?.let { recipient ->
                    insertMissedCall(recipient, true)
                }
            }
            Log.d("", "*** ^^^ terminate in rtc service > onHangUp")
            terminate()
        }
    }

    fun sendCommand(intent: Intent?) {
        if (intent == null || intent.action == null) return
        serviceExecutor.execute {
            val action = intent.action
            val callId = ((intent.getSerializableExtra(EXTRA_CALL_ID) as? UUID)?.toString() ?: "No callId")
            Log.i("Loki", "Handling ${intent.action} for call: ${callId}")
            when (action) {
                ACTION_PRE_OFFER -> if (isIdle()) handlePreOffer(intent)
                ACTION_INCOMING_RING -> when {
                    isSameCall(intent) && callManager.currentConnectionState == CallState.Reconnecting -> {
                        handleNewOffer(intent)
                    }
                    isBusy(intent) -> handleBusyCall(intent)
                    isPreOffer() -> handleIncomingPreOffer(intent)
                }
                ACTION_OUTGOING_CALL -> if (isIdle()) handleOutgoingCall(intent)
                ACTION_ANSWER_CALL -> handleAnswerCall(intent)
                ACTION_DENY_CALL -> handleDenyCall()
                ACTION_IGNORE_CALL -> handleIgnoreCall()
                ACTION_LOCAL_HANGUP -> handleLocalHangup(intent)
                ACTION_REMOTE_HANGUP -> handleRemoteHangup(intent)
                ACTION_SET_MUTE_AUDIO -> handleSetMuteAudio(intent)
                ACTION_SET_MUTE_VIDEO -> handleSetMuteVideo(intent)
                ACTION_FLIP_CAMERA -> handleSetCameraFlip(intent)
                ACTION_WIRED_HEADSET_CHANGE -> handleWiredHeadsetChanged(intent)
                ACTION_SCREEN_OFF -> handleScreenOffChange(intent)
                ACTION_ANSWER_INCOMING -> handleAnswerIncoming(intent)
                ACTION_ICE_MESSAGE -> handleRemoteIceCandidate(intent)
                ACTION_ICE_CONNECTED -> handleIceConnected(intent)
                ACTION_CHECK_TIMEOUT -> handleCheckTimeout(intent)
                ACTION_CHECK_RECONNECT -> handleCheckReconnect(intent)
                ACTION_UPDATE_AUDIO -> handleUpdateAudio(intent)
            }
        }
    }

    private fun registerUncaughtExceptionHandler() {
        uncaughtExceptionHandlerManager = UncaughtExceptionHandlerManager().apply {
            registerHandler(ProximityLockRelease(lockManager))
        }
    }

    private fun registerWiredHeadsetStateReceiver() {
        wiredHeadsetStateReceiver = WiredHeadsetStateReceiver(::sendCommand)
        context.registerReceiver(wiredHeadsetStateReceiver, IntentFilter(AudioManager.ACTION_HEADSET_PLUG))
    }

    private fun handleBusyCall(intent: Intent) {
        val recipient = getRemoteRecipient(intent)
Log.d("", "*** --- BUSY CALL - insert missed call")
        insertMissedCall(recipient, false)
    }

    private fun handleUpdateAudio(intent: Intent) {
        val audioCommand = intent.getParcelableExtra<AudioManagerCommand>(EXTRA_AUDIO_COMMAND)!!
        if (callManager.currentConnectionState !in arrayOf(
                CallState.Connected,
                *CallState.PENDING_CONNECTION_STATES
            )
        ) {
            Log.w(TAG, "handling audio command not in call")
            return
        }
        callManager.handleAudioCommand(audioCommand)
    }

    private fun handleNewOffer(intent: Intent) {
        Log.d(TAG, "*** ^^^ Handle new offer")
        val offer = intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION) ?: return
        val callId = getCallId(intent)
        val recipient = getRemoteRecipient(intent)
        callManager.onNewOffer(offer, callId, recipient).fail {
            Log.e("Loki", "Error handling new offer", it)
            callManager.postConnectionError()
            Log.d("", "*** ^^^ terminate in rtc service > handleNewOffer ")
            terminate()
        }
    }

    private fun handlePreOffer(intent: Intent) {
        Log.d(TAG, "*** ^^^ Handle pre offer pt1")
        if (!callManager.isIdle()) {
            Log.w(TAG, "Handling pre-offer from non-idle state")
            return
        }
        Log.d(TAG, "*** ^^^ Handle pre offer pt2")
        val callId = getCallId(intent)
        val recipient = getRemoteRecipient(intent)

        if (isIncomingMessageExpired(intent)) {
            insertMissedCall(recipient, true)
            Log.d("", "*** ^^^ terminate in rtc service > handlePreOffer")
            terminate()
            return
        }

        callManager.onPreOffer(callId, recipient) {
            setCallNotification(TYPE_INCOMING_PRE_OFFER, recipient)
            callManager.postViewModelState(CallViewModel.State.CALL_PRE_OFFER_INCOMING)
            callManager.initializeAudioForCall()
            callManager.startIncomingRinger()
            callManager.setAudioEnabled(true)

            BackgroundPollWorker.scheduleOnce(
                context,
                arrayOf(BackgroundPollWorker.Targets.DMS)
            )
        }
    }

    private fun handleIncomingPreOffer(intent: Intent) {
        val callId = getCallId(intent)
        val recipient = getRemoteRecipient(intent)
        val preOffer = callManager.preOfferCallData
        Log.d(TAG, "*** ^^^ Handle inc pre offer")
        if (callManager.isPreOffer() && (preOffer == null || preOffer.callId != callId || preOffer.recipient != recipient)) {
            Log.d(TAG, "Incoming ring from non-matching pre-offer")
            return
        }

        val offer = intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION) ?: return
        val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, -1)

        callManager.onIncomingRing(offer, callId, recipient, timestamp) {
            if (wantsToAnswer) {
                setCallNotification(TYPE_INCOMING_CONNECTING, recipient)
            } else {
                //No need to do anything here as this case is already taken care of from the pre offer that came before
            }
            callManager.clearPendingIceUpdates()
            callManager.postViewModelState(CallViewModel.State.CALL_OFFER_INCOMING)
            registerPowerButtonReceiver()
        }
    }

    private fun handleOutgoingCall(intent: Intent) {
        callManager.postConnectionEvent(Event.SendPreOffer) {
            val recipient = getRemoteRecipient(intent)
            callManager.recipient = recipient
            val callId = UUID.randomUUID()
            callManager.callId = callId

            callManager.initializeVideo(context)

            callManager.postViewModelState(CallViewModel.State.CALL_PRE_OFFER_OUTGOING)
            lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL)
            callManager.initializeAudioForCall()
            callManager.startOutgoingRinger(OutgoingRinger.Type.RINGING)
            setCallNotification(TYPE_OUTGOING_RINGING, callManager.recipient)
            callManager.insertCallMessage(
                recipient.address.serialize(),
                CallMessageType.CALL_OUTGOING
            )
            scheduledTimeout = timeoutExecutor.schedule(
                TimeoutRunnable(callId, context, ::sendCommand),
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )
            callManager.setAudioEnabled(true)

            val expectedState = callManager.currentConnectionState
            val expectedCallId = callManager.callId

            try {
                val offerFuture = callManager.onOutgoingCall(context)
                offerFuture.fail { e ->
                    if (isConsistentState(
                            expectedState,
                            expectedCallId,
                            callManager.currentConnectionState,
                            callManager.callId
                        )
                    ) {
                        Log.e(TAG, e)
                        callManager.postViewModelState(CallViewModel.State.NETWORK_FAILURE)
                        callManager.postConnectionError()
                        Log.d("", "*** ^^^ terminate in rtc service > handleOutgoingCall - offerFuture fail - Error: $e ")
                        terminate()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, e)
                callManager.postConnectionError()
                Log.d("", "*** ^^^ terminate in rtc service > handleOutgoingCall - CATCH - Error: $e ")
                terminate()
            }
        }
    }

    private fun handleAnswerCall(intent: Intent) {
        Log.d(TAG, "*** ^^^ Handle answer call")
        val recipient = callManager.recipient    ?: return Log.e(TAG, "*** No recipient to answer in handleAnswerCall")
        setCallNotification(TYPE_INCOMING_CONNECTING, recipient)

        val pending   = callManager.pendingOffer ?: return Log.e(TAG, "*** No pending offer in handleAnswerCall")
        val callId    = callManager.callId       ?: return Log.e(TAG, "*** No callId in handleAnswerCall")
        val timestamp = callManager.pendingOfferTime

        Log.d(TAG, "*** ^^^ Handle answer call pt2")

        if (callManager.currentConnectionState != CallState.RemoteRing) {
            Log.e(TAG, "Can only answer from ringing!")
            return
        }

        intent.putExtra(EXTRA_CALL_ID, callId)
        intent.putExtra(EXTRA_RECIPIENT_ADDRESS, recipient.address)
        intent.putExtra(EXTRA_REMOTE_DESCRIPTION, pending)
        intent.putExtra(EXTRA_TIMESTAMP, timestamp)

        if (isIncomingMessageExpired(intent)) {
            val didHangup = callManager.postConnectionEvent(Event.TimeOut) {
                insertMissedCall(recipient, true)
                Log.d("", "*** ^^^ terminate from isIncomingMessageExpired in rtc service > handleAnswerCall ")
                terminate()
            }
            if (didHangup) { return }
        }

        wantsToAnswer = true

        callManager.postConnectionEvent(Event.SendAnswer) {
            callManager.silenceIncomingRinger()

            callManager.postViewModelState(CallViewModel.State.CALL_ANSWER_INCOMING)

            scheduledTimeout = timeoutExecutor.schedule(
                TimeoutRunnable(callId, context, ::sendCommand),
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )

            callManager.initializeAudioForCall()
            callManager.initializeVideo(context)

            val expectedState = callManager.currentConnectionState
            val expectedCallId = callManager.callId

            try {
                val answerFuture = callManager.onIncomingCall(context)
                answerFuture.fail { e ->
                    if (isConsistentState(
                            expectedState,
                            expectedCallId,
                            callManager.currentConnectionState,
                            callManager.callId
                        )
                    ) {
                        Log.e(TAG, "incoming call error: $e")
                        insertMissedCall(recipient, true)
                        callManager.postConnectionError()
                        Log.d("", "*** ^^^ terminate from answer future fail (callManager.onIncomingCall) in rtc service > handleAnswerCall Error: $e")
                        terminate()
                    }
                }
                lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING)
                callManager.setAudioEnabled(true)
            } catch (e: Exception) {
                Log.e(TAG, e)
                callManager.postConnectionError()
                Log.d("", "*** ^^^ terminate fromcatch in rtc service > handleAnswerCall Error: $e")
                terminate()
            }
        }
    }

    private fun handleDenyCall() {
        callManager.handleDenyCall()
        Log.d("", "*** ^^^ terminate in rtc service > handleDenyCall ")
        terminate()
    }

    private fun handleIgnoreCall(){
        callManager.handleIgnoreCall()
        terminate()
    }

    private fun handleLocalHangup(intent: Intent) {
        val intentRecipient = getOptionalRemoteRecipient(intent)
        callManager.handleLocalHangup(intentRecipient)
        Log.d("", "*** ^^^ terminate in rtc service > handleLocalHangup ")
        terminate()
    }

    private fun handleRemoteHangup(intent: Intent) {
        if (callManager.callId != getCallId(intent)) {
            Log.e(TAG, "Hangup for non-active call...")
            return
        }

        onHangup()
    }

    private fun handleSetMuteAudio(intent: Intent) {
        val muted = intent.getBooleanExtra(EXTRA_MUTE, false)
        callManager.handleSetMuteAudio(muted)
    }

    private fun handleSetMuteVideo(intent: Intent) {
        val muted = intent.getBooleanExtra(EXTRA_MUTE, false)
        callManager.handleSetMuteVideo(muted, lockManager)
    }

    private fun handleSetCameraFlip(intent: Intent) {
        callManager.handleSetCameraFlip()
    }

    private fun handleWiredHeadsetChanged(intent: Intent) {
        callManager.handleWiredHeadsetChanged(intent.getBooleanExtra(EXTRA_AVAILABLE, false))
    }

    private fun handleScreenOffChange(intent: Intent) {
        callManager.handleScreenOffChange()
    }

    private fun handleAnswerIncoming(intent: Intent) {
        try {
            val recipient = getRemoteRecipient(intent)
            if (callManager.isCurrentUser(recipient) && callManager.currentConnectionState in CallState.CAN_DECLINE_STATES) {
                handleLocalHangup(intent)
                return
            }

            callManager.postViewModelState(CallViewModel.State.CALL_ANSWER_OUTGOING)

            val callId = getCallId(intent)
            val description = intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION)
            callManager.handleResponseMessage(
                recipient,
                callId,
                SessionDescription(SessionDescription.Type.ANSWER, description)
            )
        } catch (e: PeerConnectionException) {
            Log.d("", "*** ^^^ terminate from catch in rtc service > handleAnswerIncoming. Error:$e ")
            terminate()
        }
    }

    /**
     * Handles remote ICE candidates received from a signaling server.
     *
     * This function is called when a new ICE candidate is received for a specific call.
     * It extracts the candidate information from the intent, creates IceCandidate objects,
     * and passes them to the CallManager to be added to the PeerConnection.
     *
     * @param intent The intent containing the remote ICE candidate information.
     *               The intent should contain the following extras:
     *               - EXTRA_CALL_ID: The ID of the call.
     *               - EXTRA_ICE_SDP_MID: An array of SDP media stream identification strings.
     *               - EXTRA_ICE_SDP_LINE_INDEX: An array of SDP media line indexes.
     *               - EXTRA_ICE_SDP: An array of SDP candidate strings.
     */
    private fun handleRemoteIceCandidate(intent: Intent) {
        Log.d(TAG, "*** ^^^ Handle remote ice")
        val callId = getCallId(intent)
        val sdpMids = intent.getStringArrayExtra(EXTRA_ICE_SDP_MID) ?: return
        val sdpLineIndexes = intent.getIntArrayExtra(EXTRA_ICE_SDP_LINE_INDEX) ?: return
        val sdps = intent.getStringArrayExtra(EXTRA_ICE_SDP) ?: return
        if (sdpMids.size != sdpLineIndexes.size || sdpLineIndexes.size != sdps.size) {
            Log.w(TAG, "sdp info not of equal length")
            return
        }
        val iceCandidates = sdpMids.indices.map { index ->
            IceCandidate(
                sdpMids[index],
                sdpLineIndexes[index],
                sdps[index]
            )
        }

        callManager.handleRemoteIceCandidate(iceCandidates, callId)
    }

    private fun handleIceConnected(intent: Intent) {
        val recipient = callManager.recipient ?: return
        if(callManager.currentCallState == CallViewModel.State.CALL_CONNECTED) return
        Log.d(TAG, "*** ^^^ Handle ice connected")

        val connected = callManager.postConnectionEvent(Event.Connect) {
            callManager.postViewModelState(CallViewModel.State.CALL_CONNECTED)
            setCallNotification(TYPE_ESTABLISHED, recipient)
            callManager.startCommunication(lockManager)
        }
        if (!connected) {
            Log.e("Loki", "Error handling ice connected state transition")
            callManager.postConnectionError()
            Log.d("", "*** ^^^ terminate in rtc service > handleIceConnected ")
            terminate()
        }
    }

    private fun registerPowerButtonReceiver() {
        if (powerButtonReceiver == null) {
            powerButtonReceiver = PowerButtonReceiver(::sendCommand)
            context.registerReceiver(powerButtonReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
    }

    private fun handleCheckReconnect(intent: Intent) {
        val callId = callManager.callId ?: return
        val numTimeouts = ++currentTimeouts

        if (callId == getCallId(intent) && isNetworkAvailable && numTimeouts <= MAX_RECONNECTS) {
            Log.i("Loki", "Trying to re-connect")
            callManager.networkReestablished()
            scheduledTimeout = timeoutExecutor.schedule(
                TimeoutRunnable(callId, context, ::sendCommand),
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )
        } else if (numTimeouts < MAX_RECONNECTS) {
            Log.i(
                "Loki",
                "Network isn't available, timeouts == $numTimeouts out of $MAX_RECONNECTS"
            )
            scheduledReconnect = timeoutExecutor.schedule(
                CheckReconnectedRunnable(callId, context, ::sendCommand),
                RECONNECT_SECONDS,
                TimeUnit.SECONDS
            )
        } else {
            Log.i("Loki", "Network isn't available, timing out")
            handleLocalHangup(intent)
        }
    }

    private fun handleCheckTimeout(intent: Intent) {
        val callId = callManager.callId ?: return
        val callState = callManager.currentConnectionState

        if (callId == getCallId(intent) && (callState !in arrayOf(
                CallState.Connected,
                CallState.Connecting
            ))
        ) {
            Log.w(TAG, "Timing out call: $callId")
            handleLocalHangup(intent)
        }
    }

    /**
     * This method handles displaying notifications relating to the various call states.
     * Those notifications can be shown in two ways:
     * - Directly sent by the notification manager
     * - Displayed as part of a foreground Service
     */
    private fun setCallNotification(type: Int, recipient: Recipient?) {
        // send appropriate notification if we have permission
        if (
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            when (type) {
                // show a notification directly for this case
                TYPE_INCOMING_PRE_OFFER -> {
                    sendNotification(type, recipient)
                }
                // attempt to show the notification via a service
                else -> {
                    startServiceOrShowNotification(type, recipient)
                }
            }

        } // otherwise if we do not have permission and we have a pre offer, try to open the activity directly (this won't work if the app is backgrounded/killed)
        else if(type == TYPE_INCOMING_PRE_OFFER) {
            // Start an intent for the fullscreen call activity
            val foregroundIntent = WebRtcCallActivity.getCallActivityIntent(context)
                .setAction(WebRtcCallActivity.ACTION_FULL_SCREEN_INTENT)
            context.startActivity(foregroundIntent)
        }

    }

    @SuppressLint("MissingPermission")
    private fun sendNotification(type: Int, recipient: Recipient?){
        NotificationManagerCompat.from(context).notify(
            WEBRTC_NOTIFICATION,
            CallNotificationBuilder.getCallInProgressNotification(context, type, recipient)
        )
    }

    /**
     * This will attempt to start a service with an attached notification,
     * if the service fails to start a manual notification will be sent
     */
    private fun startServiceOrShowNotification(type: Int, recipient: Recipient?){
        try {
            ContextCompat.startForegroundService(context, CallForegroundService.startIntent(context, type, recipient))
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start Call Service intent: $e")
            sendNotification(type, recipient)
        }
    }

    private fun getOptionalRemoteRecipient(intent: Intent): Recipient? =
        intent.takeIf { it.hasExtra(EXTRA_RECIPIENT_ADDRESS) }?.let(::getRemoteRecipient)

    private fun getRemoteRecipient(intent: Intent): Recipient {
        val remoteAddress = IntentCompat.getParcelableExtra(intent, EXTRA_RECIPIENT_ADDRESS, Address::class.java)
            ?: throw AssertionError("No recipient in intent!")

        return Recipient.from(context, remoteAddress, true)
    }

    private fun getCallId(intent: Intent): UUID =
        intent.getSerializableExtra(EXTRA_CALL_ID) as? UUID
            ?: throw AssertionError("No callId in intent!")

    private fun insertMissedCall(recipient: Recipient, signal: Boolean) {
        callManager.insertCallMessage(
            threadPublicKey = recipient.address.serialize(),
            callMessageType = CallMessageType.CALL_MISSED,
            signal = signal
        )
    }

    private fun isIncomingMessageExpired(intent: Intent) =
        System.currentTimeMillis() - intent.getLongExtra(
            EXTRA_TIMESTAMP,
            -1
        ) > TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)

    private fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        callManager.unregisterListener(this)
        wiredHeadsetStateReceiver?.let(context::unregisterReceiver)
        powerButtonReceiver?.let(context::unregisterReceiver)
        networkChangedReceiver?.unregister(context)
        callManager.shutDownAudioManager()
        powerButtonReceiver = null
        wiredHeadsetStateReceiver = null
        networkChangedReceiver = null
        uncaughtExceptionHandlerManager?.unregister()
        wantsToAnswer = false
        currentTimeouts = 0
        isNetworkAvailable = false
    }

    private fun networkChange(networkAvailable: Boolean) {
        Log.d("Loki", "flipping network available to $networkAvailable")
        isNetworkAvailable = networkAvailable
        if (networkAvailable && callManager.currentConnectionState == CallState.Connected) {
            Log.d("Loki", "Should reconnected")
        }
    }

    private class CheckReconnectedRunnable(
        private val callId: UUID, private val context: Context, val sendCommand: (Intent)->Unit
    ) : Runnable {
        override fun run() {
            val intent = Intent(context, WebRtcCallBridge::class.java)
                .setAction(ACTION_CHECK_RECONNECT)
                .putExtra(EXTRA_CALL_ID, callId)
            sendCommand(intent)
        }
    }

    private class TimeoutRunnable(
        private val callId: UUID, private val context: Context, val sendCommand: (Intent)->Unit
    ) : Runnable {
        override fun run() {
            val intent = Intent(context, WebRtcCallBridge::class.java)
                .setAction(ACTION_CHECK_TIMEOUT)
                .putExtra(EXTRA_CALL_ID, callId)
            sendCommand(intent)
        }
    }

    private abstract class FailureListener<V>(
        expectedState: CallState,
        expectedCallId: UUID?,
        getState: () -> Pair<CallState, UUID?>
    ) : StateAwareListener<V>(expectedState, expectedCallId, getState) {
        override fun onSuccessContinue(result: V) {}
    }

    private abstract class SuccessOnlyListener<V>(
        expectedState: CallState,
        expectedCallId: UUID?,
        getState: () -> Pair<CallState, UUID>
    ) : StateAwareListener<V>(expectedState, expectedCallId, getState) {
        override fun onFailureContinue(throwable: Throwable?) {
            Log.e(TAG, throwable)
            throw AssertionError(throwable)
        }
    }

    private abstract class StateAwareListener<V>(
        private val expectedState: CallState,
        private val expectedCallId: UUID?,
        private val getState: () -> Pair<CallState, UUID?>
    ) : FutureTaskListener<V> {

        companion object {
            private val TAG = Log.tag(StateAwareListener::class.java)
        }

        override fun onSuccess(result: V) {
            if (!isConsistentState()) {
                Log.w(TAG, "State has changed since request, aborting success callback...")
            } else {
                onSuccessContinue(result)
            }
        }

        override fun onFailure(exception: ExecutionException?) {
            if (!isConsistentState()) {
                Log.w(TAG, exception)
                Log.w(TAG, "State has changed since request, aborting failure callback...")
            } else {
                exception?.let {
                    onFailureContinue(it.cause)
                }
            }
        }

        private fun isConsistentState(): Boolean {
            val (currentState, currentCallId) = getState()
            return expectedState == currentState && expectedCallId == currentCallId
        }

        abstract fun onSuccessContinue(result: V)
        abstract fun onFailureContinue(throwable: Throwable?)

    }

    private fun isConsistentState(
        expectedState: CallState,
        expectedCallId: UUID?,
        currentState: CallState,
        currentCallId: UUID?
    ): Boolean {
        return expectedState == currentState && expectedCallId == currentCallId
    }

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}

    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
        newState?.let { state -> processIceConnectionChange(state) }
    }

    private fun processIceConnectionChange(newState: PeerConnection.IceConnectionState) {
        serviceExecutor.execute {
            if (newState == CONNECTED) {
                scheduledTimeout?.cancel(false)
                scheduledReconnect?.cancel(false)
                scheduledTimeout = null
                scheduledReconnect = null

                val intent = Intent(context, WebRtcCallBridge::class.java)
                    .setAction(ACTION_ICE_CONNECTED)
                sendCommand(intent)
            } else if (newState in arrayOf(
                    FAILED,
                    DISCONNECTED
                ) && (scheduledReconnect == null && scheduledTimeout == null)
            ) {
                callManager.callId?.let { callId ->
                    callManager.postConnectionEvent(Event.IceDisconnect) {
                        callManager.postViewModelState(CallViewModel.State.CALL_RECONNECTING)
                        if (callManager.isInitiator()) {
                            Log.i("Loki", "Starting reconnect timer")
                            scheduledReconnect = timeoutExecutor.schedule(
                                CheckReconnectedRunnable(callId, context, ::sendCommand),
                                RECONNECT_SECONDS,
                                TimeUnit.SECONDS
                            )
                        } else {
                            Log.i("Loki", "Starting timeout, awaiting new reconnect")
                            callManager.postConnectionEvent(Event.PrepareForNewOffer) {
                                scheduledTimeout = timeoutExecutor.schedule(
                                    TimeoutRunnable(callId, context, ::sendCommand),
                                    TIMEOUT_SECONDS,
                                    TimeUnit.SECONDS
                                )
                            }
                        }
                    }
                } ?: run {
                    val intent = hangupIntent(context)
                    sendCommand(intent)
                }
            }
            Log.i("Loki", "onIceConnectionChange: $newState")
        }
    }

    fun getWantsToAnswer() = wantsToAnswer

    override fun onIceConnectionReceivingChange(p0: Boolean) {}

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

    override fun onIceCandidate(p0: IceCandidate?) {}

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}

    override fun onAddStream(p0: MediaStream?) {}

    override fun onRemoveStream(p0: MediaStream?) {}

    override fun onDataChannel(p0: DataChannel?) {}

    override fun onRenegotiationNeeded() {
        Log.w(TAG, "onRenegotiationNeeded was called!")
    }

    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
}