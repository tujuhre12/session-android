package org.thoughtcrime.securesms.webrtc

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callManager: CallManager,
    private val rtcCallBridge: WebRtcCallBridge
): ViewModel() {

    //todo PHONE Can we eventually remove this state and instead use the StateMachine.kt State?
    enum class State {
        CALL_INITIALIZING, // default starting state before any rtc state kicks in

        CALL_PRE_OFFER_INCOMING,
        CALL_PRE_OFFER_OUTGOING,
        CALL_OFFER_INCOMING,
        CALL_OFFER_OUTGOING,
        CALL_ANSWER_INCOMING,
        CALL_ANSWER_OUTGOING,
        CALL_HANDLING_ICE,
        CALL_SENDING_ICE,

        CALL_CONNECTED,
        CALL_DISCONNECTED,
        CALL_RECONNECTING,

        NETWORK_FAILURE,
        RECIPIENT_UNAVAILABLE,
    }

    val floatingRenderer: SurfaceViewRenderer?
        get() = callManager.floatingRenderer

    val fullscreenRenderer: SurfaceViewRenderer?
        get() = callManager.fullscreenRenderer

    val audioDeviceState
        get() = callManager.audioDeviceEvents

    val localAudioEnabledState
        get() = callManager.audioEvents.map { it.isEnabled }

    val videoState: StateFlow<VideoState>
        get() = callManager.videoState

    var deviceOrientation: Orientation = Orientation.UNKNOWN
        set(value) {
            field = value
            callManager.setDeviceOrientation(value)
        }

    val currentCallState get() = callManager.currentCallState
    val callState: StateFlow<Pair<State, Boolean>> = callManager.callStateEvents.combine(rtcCallBridge.hasAcceptedCall){
        state, accepted -> Pair(state, accepted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), Pair(State.CALL_INITIALIZING, false))

    val recipient get() = callManager.recipientEvents
    val callStartTime: Long get() = callManager.callStartTime

    fun swapVideos() {
       callManager.swapVideos()
    }

    fun toggleMute(){
        callManager.toggleMuteAudio()
    }

    fun toggleSpeakerphone(){
        callManager.toggleSpeakerphone()
    }

    fun toggleVideo(){
        callManager.toggleVideo()
    }

    fun flipCamera(){
        callManager.flipCamera()
    }

    fun answerCall(){
        rtcCallBridge.handleAnswerCall()
    }

    fun denyCall(){
        rtcCallBridge.handleDenyCall()
    }

    fun createCall(recipientAddress: Address) {
        rtcCallBridge.handleOutgoingCall(Recipient.from(context, recipientAddress, true))
    }

    fun hangUp(){
        rtcCallBridge.sendCommand(WebRtcCallBridge.hangupIntent(context))
    }
}