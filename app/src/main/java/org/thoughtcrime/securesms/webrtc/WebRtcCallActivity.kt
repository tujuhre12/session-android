package org.thoughtcrime.securesms.webrtc

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.graphics.Outline
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityWebrtcBinding
import org.apache.commons.lang3.time.DurationFormatUtils
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.truncateIdForDisplay
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ScreenLockActionBarActivity
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.webrtc.CallViewModel.State.*
import org.thoughtcrime.securesms.webrtc.WebRtcCallBridge.Companion
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager.AudioDevice.EARPIECE
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager.AudioDevice.SPEAKER_PHONE
import javax.inject.Inject

@AndroidEntryPoint
class WebRtcCallActivity : ScreenLockActionBarActivity() {

    companion object {
        const val ACTION_FULL_SCREEN_INTENT = "fullscreen-intent"
        const val ACTION_ANSWER = "answer"
        const val ACTION_END = "end-call"
        const val ACTION_START_CALL = "start-call"

        const val EXTRA_RECIPIENT_ADDRESS = "RECIPIENT_ID"

        private const val CALL_DURATION_FORMAT = "HH:mm:ss"

        fun getCallActivityIntent(context: Context): Intent{
            return Intent(context, WebRtcCallActivity::class.java)
                .setFlags(FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
    }

    private val viewModel by viewModels<CallViewModel>()
    private lateinit var binding: ActivityWebrtcBinding
    private var uiJob: Job? = null
    private var hangupReceiver: BroadcastReceiver? = null

    //todo PHONE TEMP STRINGS THAT WILL NEED TO BE REPLACED WITH CS STRINGS - putting them all here to easily discard them later
    val TEMP_SEND_PRE_OFFER = "Creating Call"
    val TEMP_RECEIVE_PRE_OFFER = "Receiving Pre Offer"
    val TEMP_SENDING_OFFER = "Sending Call Offer"
    val TEMP_RECEIVING_OFFER = "Receiving Call Offer"
    val TEMP_SENDING_CANDIDATES = "Sending Connection Candidates"
    val TEMP_RECEIVED_ANSWER = "Received Answer"
    val TEMP_HANDLING_CANDIDATES = "Handling Connection Candidates"

    /**
     * We need to track the device's orientation so we can calculate whether or not to rotate the video streams
     * This works a lot better than using `OrientationEventListener > onOrientationChanged'
     * which gives us a rotation angle that doesn't take into account pitch vs roll, so tipping the device from front to back would
     * trigger the video rotation logic, while we really only want it when the device is in portrait or landscape.
     */
    private var orientationManager = OrientationManager(this)

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)

        Log.d("", "*** CALL ACTIVITY CREATE: ${intent.action}")

        binding = ActivityWebrtcBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        binding.floatingRendererContainer.setOnClickListener {
            viewModel.swapVideos()
        }

        binding.microphoneButton.setOnClickListener {
            val audioEnabledIntent =
                WebRtcCallBridge.microphoneIntent(this, !viewModel.microphoneEnabled)
            viewModel.sendCommand(audioEnabledIntent)
        }

        binding.speakerPhoneButton.setOnClickListener {
            // we default to EARPIECE if not SPEAKER but the audio manager will know to actually use a headset if any is connected
            val command =
                AudioManagerCommand.SetUserDevice(if (viewModel.isSpeaker) EARPIECE else SPEAKER_PHONE)
            viewModel.sendCommand(
                WebRtcCallBridge.audioManagerCommandIntent(this, command)
            )
        }

        binding.acceptCallButton.setOnClickListener {
            answerCall()
        }

        binding.declineCallButton.setOnClickListener {
            denyCall()
        }

        hangupReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("", "*** Received hangup broadcast in webrtc activity - finishing.")
                finish()
            }
        }

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(hangupReceiver!!, IntentFilter(ACTION_END))

        binding.enableCameraButton.setOnClickListener {
            Permissions.with(this)
                .request(Manifest.permission.CAMERA)
                .onAllGranted {
                    val intent = WebRtcCallBridge.cameraEnabled(this, !viewModel.videoState.value.userVideoEnabled)
                    viewModel.sendCommand(intent)
                }
                .execute()
        }

        binding.switchCameraButton.setOnClickListener {
            viewModel.sendCommand(WebRtcCallBridge.flipCamera(this))
        }

        binding.endCallButton.setOnClickListener {
            hangUp()
        }
        binding.backArrow.setOnClickListener {
            onBackPressed()
        }

        lifecycleScope.launch {
            orientationManager.orientation.collect { orientation ->
                viewModel.deviceOrientation = orientation
                updateControlsRotation()
            }
        }

        clipFloatingInsets()

        // set up the user avatar
        TextSecurePreferences.getLocalNumber(this)?.let{
            val username = TextSecurePreferences.getProfileName(this) ?: truncateIdForDisplay(it)
            binding.userAvatar.apply {
                publicKey = it
                displayName = username
                update()
            }
        }

        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        Log.d("", "*** ^^^ Activity handle intent ${intent.action}")
        if (intent.action == ACTION_START_CALL && intent.hasExtra(EXTRA_RECIPIENT_ADDRESS)) {
            viewModel.sendCommand(
                WebRtcCallBridge.createCall(this,IntentCompat.getParcelableExtra(intent, EXTRA_RECIPIENT_ADDRESS, Address::class.java)!!)
            )
        }
        if (intent.action == ACTION_ANSWER) {
            Log.d("", "*** ^^^ Activity handle intent >> ANSWER~")
            answerCall()
        }

        if (intent.action == ACTION_FULL_SCREEN_INTENT) {
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
        }
    }

    /**
     * Makes sure the floating video inset has clipped rounded corners, included with the video stream itself
     */
    private fun clipFloatingInsets() {
        // clip the video inset with rounded corners
        val videoInsetProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                // all corners
                outline.setRoundRect(
                    0, 0, view.width, view.height,
                    resources.getDimensionPixelSize(R.dimen.video_inset_radius).toFloat()
                )
            }
        }

        binding.floatingRendererContainer.outlineProvider = videoInsetProvider
        binding.floatingRendererContainer.clipToOutline = true
    }

    override fun onResume() {
        super.onResume()
        orientationManager.startOrientationListener()

    }

    override fun onPause() {
        super.onPause()
        orientationManager.stopOrientationListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        hangupReceiver?.let { receiver ->
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        }

        orientationManager.destroy()
    }

    private fun answerCall() {
        val answerIntent = WebRtcCallBridge.acceptCallIntent(this)
        answerIntent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        viewModel.sendCommand(answerIntent)
    }

    private fun denyCall(){
        val declineIntent = WebRtcCallBridge.denyCallIntent(this)
        viewModel.sendCommand(declineIntent)
    }

    private fun hangUp(){
        viewModel.sendCommand(WebRtcCallBridge.hangupIntent(this))
    }

    private fun updateControlsRotation() {
        with (binding) {
            val rotation = when(viewModel.deviceOrientation){
                Orientation.LANDSCAPE -> -90f
                Orientation.REVERSED_LANDSCAPE -> 90f
                else -> 0f
            }

            userAvatar.animate().cancel()
            userAvatar.animate().rotation(rotation).start()
            contactAvatar.animate().cancel()
            contactAvatar.animate().rotation(rotation).start()

            speakerPhoneButton.animate().cancel()
            speakerPhoneButton.animate().rotation(rotation).start()

            microphoneButton.animate().cancel()
            microphoneButton.animate().rotation(rotation).start()

            enableCameraButton.animate().cancel()
            enableCameraButton.animate().rotation(rotation).start()

            switchCameraButton.animate().cancel()
            switchCameraButton.animate().rotation(rotation).start()

            endCallButton.animate().cancel()
            endCallButton.animate().rotation(rotation).start()
        }
    }

    private fun updateControls(state: CallViewModel.State, hasAcceptedCall: Boolean) {
        with(binding) {
            // set up title and subtitle
            callTitle.text = when (state) {
                CALL_PRE_OFFER_OUTGOING, CALL_PRE_OFFER_INCOMING,
                CALL_OFFER_OUTGOING, CALL_OFFER_INCOMING,
                    -> getString(R.string.callsRinging)

                CALL_ANSWER_INCOMING,
                CALL_ANSWER_OUTGOING,
                    -> getString(R.string.callsConnecting)

                CALL_CONNECTED -> ""

                CALL_RECONNECTING -> getString(R.string.callsReconnecting)
                RECIPIENT_UNAVAILABLE,
                CALL_DISCONNECTED -> getString(R.string.callsEnded)

                NETWORK_FAILURE -> getString(R.string.callsErrorStart)

                else -> callTitle.text
            }

            callSubtitle.text = when (state) {
                CALL_PRE_OFFER_OUTGOING -> TEMP_SEND_PRE_OFFER
                CALL_PRE_OFFER_INCOMING -> TEMP_RECEIVE_PRE_OFFER

                CALL_OFFER_OUTGOING -> TEMP_SENDING_OFFER
                CALL_OFFER_INCOMING -> TEMP_RECEIVING_OFFER

                CALL_ANSWER_OUTGOING, CALL_ANSWER_INCOMING -> TEMP_RECEIVED_ANSWER

                CALL_SENDING_ICE -> TEMP_SENDING_CANDIDATES
                CALL_HANDLING_ICE -> TEMP_HANDLING_CANDIDATES

                else -> ""
            }
            callSubtitle.isVisible = callSubtitle.text.isNotEmpty()

            // buttons visibility
Log.d("", "*** ^^^ STATE: $state")
            val showCallControls = state in listOf(
                CALL_CONNECTED,
                CALL_PRE_OFFER_OUTGOING,
                CALL_OFFER_OUTGOING,
                CALL_ANSWER_OUTGOING,
                CALL_ANSWER_INCOMING,
            ) || (state in listOf(
                CALL_PRE_OFFER_INCOMING,
                CALL_OFFER_INCOMING,
                CALL_HANDLING_ICE,
                CALL_SENDING_ICE
            ) && hasAcceptedCall)
            controlGroup.isVisible = showCallControls

            endCallButton.isVisible = showCallControls || state == CALL_RECONNECTING

            incomingControlGroup.isVisible =
                state in listOf(
                    CALL_PRE_OFFER_INCOMING,
                    CALL_OFFER_INCOMING,
                    CALL_HANDLING_ICE,
                    CALL_SENDING_ICE
                ) && !hasAcceptedCall
        }
    }

    override fun onStart() {
        super.onStart()

        uiJob = lifecycleScope.launch {

            launch {
                viewModel.audioDeviceState.collect { state ->
                    val speakerEnabled = state.selectedDevice == SPEAKER_PHONE
                    // change drawable background to enabled or not
                    binding.speakerPhoneButton.isSelected = speakerEnabled
                }
            }

            launch {
                viewModel.callState.collect { data ->
                    updateControls(data.first, data.second)
                }
            }

            launch {
                viewModel.recipient.collect { latestRecipient ->
                    binding.contactAvatar.recycle()

                    if (latestRecipient.recipient != null) {
                        val contactPublicKey = latestRecipient.recipient.address.serialize()
                        val contactDisplayName = getUserDisplayName(contactPublicKey)
                        supportActionBar?.title = contactDisplayName
                        binding.remoteRecipientName.text = contactDisplayName

                        // sort out the contact's avatar
                        binding.contactAvatar.apply {
                            publicKey = contactPublicKey
                            displayName = contactDisplayName
                            update()
                        }
                    }
                }
            }

            launch {
                while (isActive) {
                    val startTime = viewModel.callStartTime
                    if (startTime != -1L) {
                        if(viewModel.currentCallState == CALL_CONNECTED) {
                            binding.callTitle.text = DurationFormatUtils.formatDuration(
                                System.currentTimeMillis() - startTime,
                                CALL_DURATION_FORMAT
                            )
                        }
                    }

                    delay(1_000)
                }
            }

            launch {
                viewModel.localAudioEnabledState.collect { isEnabled ->
                    // change drawable background to enabled or not
                    binding.microphoneButton.isSelected = !isEnabled
                }
            }

            // handle video state
            launch {
                viewModel.videoState.collect { state ->
                    binding.floatingRenderer.removeAllViews()
                    binding.fullscreenRenderer.removeAllViews()

                    // handle fullscreen video window
                    if(state.showFullscreenVideo()){
                        viewModel.fullscreenRenderer?.let { surfaceView ->
                            binding.fullscreenRenderer.addView(surfaceView)
                            binding.fullscreenRenderer.isVisible = true
                            hideAvatar()
                        }
                    } else {
                        binding.fullscreenRenderer.isVisible = false
                        showAvatar(state.swapped)
                    }

                    // handle floating video window
                    if(state.showFloatingVideo()){
                        viewModel.floatingRenderer?.let { surfaceView ->
                            binding.floatingRenderer.addView(surfaceView)
                            binding.floatingRenderer.isVisible = true
                            binding.swapViewIcon.bringToFront()
                        }
                    } else {
                        binding.floatingRenderer.isVisible = false
                    }

                    // the floating video inset (empty or not) should be shown
                    // the moment we have either of the video streams
                    val showFloatingContainer = state.userVideoEnabled || state.remoteVideoEnabled
                    binding.floatingRendererContainer.isVisible = showFloatingContainer
                    binding.swapViewIcon.isVisible = showFloatingContainer

                    // make sure to default to the contact's avatar if the floating container is not visible
                    if (!showFloatingContainer) showAvatar(false)

                    // handle buttons
                    binding.enableCameraButton.isSelected = state.userVideoEnabled
                }
            }
        }
    }

    /**
     * Shows the avatar image.
     * If @showUserAvatar is true, the user's avatar is shown, otherwise the contact's avatar is shown.
     */
    private fun showAvatar(showUserAvatar: Boolean) {
        binding.userAvatar.isVisible = showUserAvatar
        binding.contactAvatar.isVisible = !showUserAvatar
    }

    private fun hideAvatar() {
        binding.userAvatar.isVisible = false
        binding.contactAvatar.isVisible = false
    }

    private fun getUserDisplayName(publicKey: String): String {
        val contact =
            DatabaseComponent.get(this).sessionContactDatabase().getContactWithAccountID(publicKey)
        return contact?.displayName(Contact.ContactContext.REGULAR) ?: publicKey
    }

    override fun onStop() {
        super.onStop()
        uiJob?.cancel()
        binding.fullscreenRenderer.removeAllViews()
        binding.floatingRenderer.removeAllViews()
    }
}