package org.thoughtcrime.securesms.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.session.libsession.messaging.calls.CallMessageType
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.FutureTaskListener
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.calls.WebRtcCallActivity
import org.thoughtcrime.securesms.notifications.BackgroundPollWorker
import org.thoughtcrime.securesms.util.CallNotificationBuilder
import org.thoughtcrime.securesms.util.CallNotificationBuilder.Companion.TYPE_ESTABLISHED
import org.thoughtcrime.securesms.util.CallNotificationBuilder.Companion.TYPE_INCOMING_CONNECTING
import org.thoughtcrime.securesms.util.CallNotificationBuilder.Companion.TYPE_INCOMING_PRE_OFFER
import org.thoughtcrime.securesms.util.CallNotificationBuilder.Companion.TYPE_OUTGOING_RINGING
import org.thoughtcrime.securesms.util.CallNotificationBuilder.Companion.WEBRTC_NOTIFICATION
import org.thoughtcrime.securesms.webrtc.AudioManagerCommand
import org.thoughtcrime.securesms.webrtc.CallManager
import org.thoughtcrime.securesms.webrtc.CallViewModel
import org.thoughtcrime.securesms.webrtc.NetworkChangeReceiver
import org.thoughtcrime.securesms.webrtc.PeerConnectionException
import org.thoughtcrime.securesms.webrtc.PowerButtonReceiver
import org.thoughtcrime.securesms.webrtc.ProximityLockRelease
import org.thoughtcrime.securesms.webrtc.UncaughtExceptionHandlerManager
import org.thoughtcrime.securesms.webrtc.WiredHeadsetStateReceiver
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

@Singleton
class WebRtcCallService @Inject constructor(
    @ApplicationContext val context: Context,
    val callManager: CallManager
): CallManager.WebRtcListener  {

    companion object {

        private val TAG = Log.tag(WebRtcCallService::class.java)

        const val ACTION_INCOMING_RING = "RING_INCOMING"
        const val ACTION_OUTGOING_CALL = "CALL_OUTGOING"
        const val ACTION_ANSWER_CALL = "ANSWER_CALL"
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
        const val ACTION_WANTS_TO_ANSWER = "WANTS_TO_ANSWER"

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
        const val EXTRA_WANTS_TO_ANSWER = "wants_to_answer"

        const val INVALID_NOTIFICATION_ID = -1
        private const val TIMEOUT_SECONDS = 30L
        private const val RECONNECT_SECONDS = 5L
        private const val MAX_RECONNECTS = 5

        fun cameraEnabled(context: Context, enabled: Boolean) =
            Intent(context, WebRtcCallService::class.java)
                .setAction(ACTION_SET_MUTE_VIDEO)
                .putExtra(EXTRA_MUTE, !enabled)

        fun flipCamera(context: Context) = Intent(context, WebRtcCallService::class.java)
            .setAction(ACTION_FLIP_CAMERA)

        fun acceptCallIntent(context: Context) = Intent(context, WebRtcCallService::class.java)
            .setAction(ACTION_ANSWER_CALL)

        fun microphoneIntent(context: Context, enabled: Boolean) =
            Intent(context, WebRtcCallService::class.java)
                .setAction(ACTION_SET_MUTE_AUDIO)
                .putExtra(EXTRA_MUTE, !enabled)

        fun createCall(context: Context, address: Address) =
            Intent(context, WebRtcCallService::class.java)
                .setAction(ACTION_OUTGOING_CALL)
                .putExtra(EXTRA_RECIPIENT_ADDRESS, address)

        fun incomingCall(
            context: Context,
            address: Address,
            sdp: String,
            callId: UUID,
            callTime: Long
        ) =
            Intent(context, WebRtcCallService::class.java)
                .setAction(ACTION_INCOMING_RING)
                .putExtra(EXTRA_RECIPIENT_ADDRESS, address)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_REMOTE_DESCRIPTION, sdp)
                .putExtra(EXTRA_TIMESTAMP, callTime)

        fun incomingAnswer(context: Context, address: Address, sdp: String, callId: UUID) =
            Intent(context, WebRtcCallService::class.java)
                .setAction(ACTION_ANSWER_INCOMING)
                .putExtra(EXTRA_RECIPIENT_ADDRESS, address)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_REMOTE_DESCRIPTION, sdp)

        fun preOffer(context: Context, address: Address, callId: UUID, callTime: Long) =
            Intent(context, WebRtcCallService::class.java)
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
            Intent(context, WebRtcCallService::class.java)
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
            Intent(context, WebRtcCallService::class.java).setAction(ACTION_DENY_CALL)

        fun remoteHangupIntent(context: Context, callId: UUID) =
            Intent(context, WebRtcCallService::class.java)
                .setAction(ACTION_REMOTE_HANGUP)
                .putExtra(EXTRA_CALL_ID, callId)

        fun hangupIntent(context: Context) =
            Intent(context, WebRtcCallService::class.java).setAction(ACTION_LOCAL_HANGUP)

        fun audioManagerCommandIntent(context: Context, command: AudioManagerCommand) =
            Intent(context, WebRtcCallService::class.java)
                .setAction(ACTION_UPDATE_AUDIO)
                .putExtra(EXTRA_AUDIO_COMMAND, command)

        fun broadcastWantsToAnswer(context: Context, wantsToAnswer: Boolean) {
            val intent = Intent(ACTION_WANTS_TO_ANSWER).putExtra(EXTRA_WANTS_TO_ANSWER, wantsToAnswer)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }
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
    private var wantsToAnswerReceiver: BroadcastReceiver? = null
    private var wiredHeadsetStateReceiver: WiredHeadsetStateReceiver? = null
    private var uncaughtExceptionHandlerManager: UncaughtExceptionHandlerManager? = null
    private var powerButtonReceiver: PowerButtonReceiver? = null

    init {
        callManager.registerListener(this)
        wantsToAnswer = false
        isNetworkAvailable = true
        registerWiredHeadsetStateReceiver()
        registerWantsToAnswerReceiver()
        registerUncaughtExceptionHandler()
        networkChangedReceiver = NetworkChangeReceiver(::networkChange)
        networkChangedReceiver!!.register(context)
    }


    @Synchronized
    private fun terminate() {
        Log.d(TAG, "*** Terminating rtc service")
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
        //todo PHONE I got a 'missed call' notification after swiping off notification
        //todo PHONE I got a 'missed call' notification when picking up the phone too...
        //todo PHONE [xxx Called you], which is a control message for a SUCCESSFUL call, should appear as unread, since you already know about the call - make it unread by default
        //todo PHONE have a fallback way to get back to calls if the call activity is gone. Sticky notification? A banner in the app? - earlier version can't swipe the notification off while more recent can.. can this be changed?
        //todo PHONE It seems we can't call if the phone has been in sleep for a while. The call (sending) doesn't seem to do anything (not receiving anything)
        //todo PHONE test other receivers (proximity, headset, etc... )
        //todo PHONE often get in a state where the phone gets stuck after accepting the call
        //todo PHONE sometimes the notification doesn't immediately disappear when hitting 'accept' - probably the state hasn't yet updated, maybe we could enforce the behaviour upon tapping the button
        //todo PHONE it seems the proximity stuff still goes on after a call
        //todo PHONE ice candidate should happen separately from answer (before?)
        //todo PHONE GETTING A LOT OF RECONNECTING causing missed call during a call
        //todo PHONE hanging up from iOS: call is not terminated  on android side
        //todo PHONE when ending a call with user A I get a notification regarding missing a call from user B that happened before (but message is unseen)
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
            terminate()
        }
    }

    fun onStartCommand(intent: Intent?) {
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
                    isPreOffer() -> handleIncomingRing(intent)
                }
                ACTION_OUTGOING_CALL -> if (isIdle()) handleOutgoingCall(intent)
                ACTION_ANSWER_CALL -> handleAnswerCall(intent)
                ACTION_DENY_CALL -> handleDenyCall()
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

    private fun registerWantsToAnswerReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                wantsToAnswer = intent?.getBooleanExtra(EXTRA_WANTS_TO_ANSWER, false) ?: false
            }
        }
        wantsToAnswerReceiver = receiver
        LocalBroadcastManager.getInstance(context)
            .registerReceiver(receiver, IntentFilter(ACTION_WANTS_TO_ANSWER))
    }

    private fun registerWiredHeadsetStateReceiver() {
        wiredHeadsetStateReceiver = WiredHeadsetStateReceiver(::onStartCommand)
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
        val offer = intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION) ?: return
        val callId = getCallId(intent)
        val recipient = getRemoteRecipient(intent)
        callManager.onNewOffer(offer, callId, recipient).fail {
            Log.e("Loki", "Error handling new offer", it)
            callManager.postConnectionError()
            terminate()
        }
    }

    private fun handlePreOffer(intent: Intent) {
        if (!callManager.isIdle()) {
            Log.w(TAG, "Handling pre-offer from non-idle state")
            return
        }
        val callId = getCallId(intent)
        val recipient = getRemoteRecipient(intent)

        if (isIncomingMessageExpired(intent)) {
            insertMissedCall(recipient, true)
            terminate()
            return
        }

        callManager.onPreOffer(callId, recipient) {
            setCallInProgressNotification(TYPE_INCOMING_PRE_OFFER, recipient)
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

    private fun handleIncomingRing(intent: Intent) {
        val callId = getCallId(intent)
        val recipient = getRemoteRecipient(intent)
        val preOffer = callManager.preOfferCallData

        if (callManager.isPreOffer() && (preOffer == null || preOffer.callId != callId || preOffer.recipient != recipient)) {
            Log.d(TAG, "Incoming ring from non-matching pre-offer")
            return
        }

        val offer = intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION) ?: return
        val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, -1)

        callManager.onIncomingRing(offer, callId, recipient, timestamp) {
            if (wantsToAnswer) {
                setCallInProgressNotification(TYPE_INCOMING_CONNECTING, recipient)
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
            setCallInProgressNotification(TYPE_OUTGOING_RINGING, callManager.recipient)
            callManager.insertCallMessage(
                recipient.address.serialize(),
                CallMessageType.CALL_OUTGOING
            )
            scheduledTimeout = timeoutExecutor.schedule(
                TimeoutRunnable(callId, context, ::onStartCommand),
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
                        terminate()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, e)
                callManager.postConnectionError()
                terminate()
            }
        }
    }

    private fun handleAnswerCall(intent: Intent) {
        val recipient = callManager.recipient    ?: return Log.e(TAG, "No recipient to answer in handleAnswerCall")
        val pending   = callManager.pendingOffer ?: return Log.e(TAG, "No pending offer in handleAnswerCall")
        val callId    = callManager.callId       ?: return Log.e(TAG, "No callId in handleAnswerCall")
        val timestamp = callManager.pendingOfferTime

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
                terminate()
            }
            if (didHangup) { return }
        }

        callManager.postConnectionEvent(Event.SendAnswer) {
            setCallInProgressNotification(TYPE_INCOMING_CONNECTING, recipient)

            callManager.silenceIncomingRinger()

            callManager.postViewModelState(CallViewModel.State.CALL_ANSWER_INCOMING)

            scheduledTimeout = timeoutExecutor.schedule(
                TimeoutRunnable(callId, context, ::onStartCommand),
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
                        terminate()
                    }
                }
                lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING)
                callManager.setAudioEnabled(true)
            } catch (e: Exception) {
                Log.e(TAG, e)
                callManager.postConnectionError()
                terminate()
            }
        }
    }

    private fun handleDenyCall() {
        callManager.handleDenyCall()
        terminate()
    }

    private fun handleLocalHangup(intent: Intent) {
        val intentRecipient = getOptionalRemoteRecipient(intent)
        callManager.handleLocalHangup(intentRecipient)
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
        val connected = callManager.postConnectionEvent(Event.Connect) {
            callManager.postViewModelState(CallViewModel.State.CALL_CONNECTED)
            setCallInProgressNotification(TYPE_ESTABLISHED, recipient)
            callManager.startCommunication(lockManager)
        }
        if (!connected) {
            Log.e("Loki", "Error handling ice connected state transition")
            callManager.postConnectionError()
            terminate()
        }
    }

    private fun registerPowerButtonReceiver() {
        if (powerButtonReceiver == null) {
            powerButtonReceiver = PowerButtonReceiver(::onStartCommand)
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
                TimeoutRunnable(callId, context, ::onStartCommand),
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )
        } else if (numTimeouts < MAX_RECONNECTS) {
            Log.i(
                "Loki",
                "Network isn't available, timeouts == $numTimeouts out of $MAX_RECONNECTS"
            )
            scheduledReconnect = timeoutExecutor.schedule(
                CheckReconnectedRunnable(callId, context, ::onStartCommand),
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



    // Over the course of setting up a phone call this method is called multiple times with `types`
    // of PRE_OFFER -> RING_INCOMING -> ICE_MESSAGE
    private fun setCallInProgressNotification(type: Int, recipient: Recipient?) {
        // send appropriate notification if we have permission
        if (
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(
                WEBRTC_NOTIFICATION,
                CallNotificationBuilder.getCallInProgressNotification(context, type, recipient)
            )
        } // otherwise if we do not have permission and we have a pre offer, try to open the activity directly (this won't work if the app is backgrounded/killed)
        else if(type == TYPE_INCOMING_PRE_OFFER) {
            // Start an intent for the fullscreen call activity
            val foregroundIntent = Intent(context, WebRtcCallActivity::class.java)
                .setFlags(FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                .setAction(WebRtcCallActivity.ACTION_FULL_SCREEN_INTENT)
            context.startActivity(foregroundIntent)
        }

    }

    private fun getOptionalRemoteRecipient(intent: Intent): Recipient? =
        intent.takeIf { it.hasExtra(EXTRA_RECIPIENT_ADDRESS) }?.let(::getRemoteRecipient)

    private fun getRemoteRecipient(intent: Intent): Recipient {
        val remoteAddress = intent.getParcelableExtra<Address>(EXTRA_RECIPIENT_ADDRESS)
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
        wantsToAnswerReceiver?.let { receiver ->
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        }
        callManager.shutDownAudioManager()
        powerButtonReceiver = null
        wiredHeadsetStateReceiver = null
        networkChangedReceiver = null
        wantsToAnswerReceiver = null
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
            val intent = Intent(context, WebRtcCallService::class.java)
                .setAction(ACTION_CHECK_RECONNECT)
                .putExtra(EXTRA_CALL_ID, callId)
            sendCommand(intent)
        }
    }

    private class TimeoutRunnable(
        private val callId: UUID, private val context: Context, val sendCommand: (Intent)->Unit
    ) : Runnable {
        override fun run() {
            val intent = Intent(context, WebRtcCallService::class.java)
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

                val intent = Intent(context, WebRtcCallService::class.java)
                    .setAction(ACTION_ICE_CONNECTED)
                onStartCommand(intent)
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
                                CheckReconnectedRunnable(callId, context, ::onStartCommand),
                                RECONNECT_SECONDS,
                                TimeUnit.SECONDS
                            )
                        } else {
                            Log.i("Loki", "Starting timeout, awaiting new reconnect")
                            callManager.postConnectionEvent(Event.PrepareForNewOffer) {
                                scheduledTimeout = timeoutExecutor.schedule(
                                    TimeoutRunnable(callId, context, ::onStartCommand),
                                    TIMEOUT_SECONDS,
                                    TimeUnit.SECONDS
                                )
                            }
                        }
                    }
                } ?: run {
                    val intent = hangupIntent(context)
                    onStartCommand(intent)
                }
            }
            Log.i("Loki", "onIceConnectionChange: $newState")
        }
    }

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