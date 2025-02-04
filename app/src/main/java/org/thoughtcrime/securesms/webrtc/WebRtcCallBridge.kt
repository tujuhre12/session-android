package org.thoughtcrime.securesms.webrtc

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import network.loki.messenger.BuildConfig
import network.loki.messenger.R
import org.session.libsession.messaging.calls.CallMessageType
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.FutureTaskListener
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.notifications.BackgroundPollWorker
import org.thoughtcrime.securesms.service.CallForegroundService
import org.thoughtcrime.securesms.util.InternetConnectivity
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.Companion.TYPE_ESTABLISHED
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.Companion.TYPE_INCOMING_CONNECTING
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.Companion.TYPE_INCOMING_PRE_OFFER
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.Companion.TYPE_OUTGOING_RINGING
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.Companion.WEBRTC_NOTIFICATION
import org.thoughtcrime.securesms.webrtc.audio.OutgoingRinger
import org.thoughtcrime.securesms.webrtc.data.Event
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
    @ApplicationContext private val context: Context,
    private val callManager: CallManager,
    private val internetConnectivity: InternetConnectivity
): CallManager.WebRtcListener  {

    companion object {

        private val TAG = Log.tag(WebRtcCallBridge::class.java)

        const val ACTION_INCOMING_RING = "RING_INCOMING"
        const val ACTION_IGNORE_CALL = "IGNORE_CALL" // like when swiping off a notification. Ends the call without notifying the caller
        const val ACTION_DENY_CALL = "DENY_CALL"
        const val ACTION_LOCAL_HANGUP = "LOCAL_HANGUP"
        const val ACTION_WIRED_HEADSET_CHANGE = "WIRED_HEADSET_CHANGE"
        const val ACTION_SCREEN_OFF = "SCREEN_OFF"
        const val ACTION_CHECK_TIMEOUT = "CHECK_TIMEOUT"
        const val ACTION_CHECK_RECONNECT = "CHECK_RECONNECT"

        const val ACTION_PRE_OFFER = "PRE_OFFER"
        const val ACTION_ANSWER_INCOMING = "ANSWER_INCOMING"
        const val ACTION_ICE_MESSAGE = "ICE_MESSAGE"
        const val ACTION_REMOTE_HANGUP = "REMOTE_HANGUP"
        const val ACTION_ICE_CONNECTED = "ICE_CONNECTED"

        const val EXTRA_RECIPIENT_ADDRESS = "RECIPIENT_ID"
        const val EXTRA_AVAILABLE = "enabled_value"
        const val EXTRA_REMOTE_DESCRIPTION = "remote_description"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_ICE_SDP = "ice_sdp"
        const val EXTRA_ICE_SDP_MID = "ice_sdp_mid"
        const val EXTRA_ICE_SDP_LINE_INDEX = "ice_sdp_line_index"

        private const val TIMEOUT_SECONDS = 30L
        private const val RECONNECT_SECONDS = 5L
        private const val MAX_RECONNECTS = 5

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
    }

    private var _hasAcceptedCall: MutableStateFlow<Boolean> = MutableStateFlow(false) // always true for outgoing call and true once the user accepts the call for incoming calls
    val hasAcceptedCall: StateFlow<Boolean> = _hasAcceptedCall

    private var currentTimeouts = 0
    private var isNetworkAvailable = true
    private var scheduledTimeout: ScheduledFuture<*>? = null
    private var scheduledReconnect: ScheduledFuture<*>? = null

    private val serviceExecutor = Executors.newSingleThreadExecutor()
    private val timeoutExecutor = Executors.newScheduledThreadPool(1)

    private var wiredHeadsetStateReceiver: WiredHeadsetStateReceiver? = null
    private var powerButtonReceiver: PowerButtonReceiver? = null

    init {
        callManager.registerListener(this)
        _hasAcceptedCall.value = false
        isNetworkAvailable = true
        registerWiredHeadsetStateReceiver()

        GlobalScope.launch {
            internetConnectivity.networkAvailable.collectLatest(::networkChange)
        }
    }


    @Synchronized
    private fun terminate() {
        Log.d(TAG, "Terminating rtc service")
        context.stopService(Intent(context, CallForegroundService::class.java))
        NotificationManagerCompat.from(context).cancel(WEBRTC_NOTIFICATION)
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(WebRtcCallActivity.ACTION_END))
        callManager.stop()
        _hasAcceptedCall.value = false
        currentTimeouts = 0
        isNetworkAvailable = true
        scheduledTimeout?.cancel(false)
        scheduledReconnect?.cancel(false)
        scheduledTimeout = null
        scheduledReconnect = null
        callManager.postViewModelState(CallViewModel.State.CALL_INITIALIZING) // reset to default state


        //todo PHONE GETTING missed call notifications during all parts of a call: when picking up, hanging up, sometimes while swiping off a notification ( from older notifications as they are still unseen ? )

        //todo PHONE It seems we can't call if the phone has been in sleep for a while. The call (sending) doesn't seem to do anything (not receiving anything) - stuck on "Creating call" - also same when receiving a call, it starts ok but gets stuck
        //todo PHONE should we refactor ice candidates to be sent prior to answering the call?

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
                ACTION_DENY_CALL -> handleDenyCall()
                ACTION_IGNORE_CALL -> handleIgnoreCall()
                ACTION_LOCAL_HANGUP -> handleLocalHangup(intent)
                ACTION_REMOTE_HANGUP -> handleRemoteHangup(intent)
                ACTION_WIRED_HEADSET_CHANGE -> handleWiredHeadsetChanged(intent)
                ACTION_SCREEN_OFF -> handleScreenOffChange(intent)
                ACTION_ANSWER_INCOMING -> handleAnswerIncoming(intent)
                ACTION_ICE_MESSAGE -> handleRemoteIceCandidate(intent)
                ACTION_ICE_CONNECTED -> handleIceConnected(intent)
                ACTION_CHECK_TIMEOUT -> handleCheckTimeout(intent)
                ACTION_CHECK_RECONNECT -> handleCheckReconnect(intent)
            }
        }
    }

    private fun registerWiredHeadsetStateReceiver() {
        wiredHeadsetStateReceiver = WiredHeadsetStateReceiver(::sendCommand)
        context.registerReceiver(wiredHeadsetStateReceiver, IntentFilter(AudioManager.ACTION_HEADSET_PLUG))
    }

    private fun handleBusyCall(intent: Intent) {
        val recipient = getRemoteRecipient(intent)
        insertMissedCall(recipient, false)
    }

    private fun handleNewOffer(intent: Intent) {
        Log.d(TAG, "Handle new offer")
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
        Log.d(TAG, "Handle pre offer")
        if (!callManager.isIdle()) {
            Log.w(TAG, "Handling pre-offer from non-idle state")
            return
        }

        val callId = getCallId(intent)
        val recipient = getRemoteRecipient(intent)

        if (isIncomingMessageExpired(intent.getLongExtra(EXTRA_TIMESTAMP, -1))) {
            debugToast("Pre offer expired - message timestamp was deemed expired: ${System.currentTimeMillis() - intent.getLongExtra(EXTRA_TIMESTAMP, -1)}s")
            insertMissedCall(recipient, true)
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

    fun debugToast(message: String) {
        if (BuildConfig.BUILD_TYPE != "release") {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleIncomingPreOffer(intent: Intent) {
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
            if (_hasAcceptedCall.value) {
                setCallNotification(TYPE_INCOMING_CONNECTING, recipient)
            } else {
                //No need to do anything here as this case is already taken care of from the pre offer that came before
            }
            callManager.clearPendingIceUpdates()
            callManager.postViewModelState(CallViewModel.State.CALL_OFFER_INCOMING)
            registerPowerButtonReceiver()

            // if the user has already accepted the incoming call, try to answer again
            // (they would have tried to answer when they first accepted
            // but it would have silently failed due to the pre offer having not been set yet
            if(_hasAcceptedCall.value) handleAnswerCall()
        }
    }

    fun handleOutgoingCall(recipient: Recipient) {
        if (!isIdle())  return

        _hasAcceptedCall.value = true // outgoing calls are automatically set to 'accepted'
        callManager.postConnectionEvent(Event.SendPreOffer) {
            callManager.recipient = recipient
            val callId = UUID.randomUUID()
            callManager.callId = callId

            callManager.initializeVideo(context)

            callManager.postViewModelState(CallViewModel.State.CALL_PRE_OFFER_OUTGOING)
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

    fun handleAnswerCall() {
        Log.d(TAG, "Handle answer call")
        _hasAcceptedCall.value = true

        val recipient = callManager.recipient ?: return Log.e(TAG, "No recipient to answer in handleAnswerCall")
        setCallNotification(TYPE_INCOMING_CONNECTING, recipient)

        if(callManager.pendingOffer == null) {
            return Log.e(TAG, "No pending offer in handleAnswerCall")
        }

        val callId = callManager.callId ?: return Log.e(TAG, "No callId in handleAnswerCall")

        val timestamp = callManager.pendingOfferTime

        if (callManager.currentConnectionState != CallState.RemoteRing) {
            Log.e(TAG, "Can only answer from ringing!")
            return
        }

        if (isIncomingMessageExpired(timestamp)) {
            val didHangup = callManager.postConnectionEvent(Event.TimeOut) {
                debugToast("Answer expired - message timestamp was deemed expired: ${System.currentTimeMillis() - timestamp}s")
                insertMissedCall(recipient, true) //todo PHONE do we want a missed call in this case? Or just [xxx] called you ?
                terminate()
            }
            if (didHangup) { return }
        }

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
                        insertMissedCall(recipient, true) //todo PHONE do we want a missed call in this case? Or just [xxx] called you ?
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

    fun handleDenyCall() {
        callManager.handleDenyCall()
        terminate()
    }

    private fun handleIgnoreCall(){
        callManager.handleIgnoreCall()
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
        Log.d(TAG, "Handle remote ice")
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
        Log.d(TAG, "Handle ice connected")

        val connected = callManager.postConnectionEvent(Event.Connect) {
            callManager.postViewModelState(CallViewModel.State.CALL_CONNECTED)
            setCallNotification(TYPE_ESTABLISHED, recipient)
            callManager.startCommunication()
        }
        if (!connected) {
            Log.e("Loki", "Error handling ice connected state transition")
            callManager.postConnectionError()
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

    private fun isIncomingMessageExpired(timestamp: Long) =
        (System.currentTimeMillis() - timestamp) > TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)

    private fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        callManager.unregisterListener(this)
        wiredHeadsetStateReceiver?.let(context::unregisterReceiver)
        powerButtonReceiver?.let(context::unregisterReceiver)
        callManager.shutDownAudioManager()
        powerButtonReceiver = null
        wiredHeadsetStateReceiver = null
        _hasAcceptedCall.value = false
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