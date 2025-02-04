package org.thoughtcrime.securesms.webrtc

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager.AudioDevice.EARPIECE
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager.AudioDevice.SPEAKER_PHONE
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
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

    fun sendCommand(intent: Intent){
        rtcCallBridge.sendCommand(intent)
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
}