package org.thoughtcrime.securesms.calls

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityWebrtcBinding
import org.apache.commons.lang3.time.DurationFormatUtils
import org.session.libsession.avatars.ProfileContactPhoto
import org.session.libsession.messaging.contacts.Contact
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.service.WebRtcCallService
import org.thoughtcrime.securesms.util.AvatarPlaceholderGenerator
import org.thoughtcrime.securesms.webrtc.AudioManagerCommand
import org.thoughtcrime.securesms.webrtc.CallViewModel
import org.thoughtcrime.securesms.webrtc.CallViewModel.State.CALL_CONNECTED
import org.thoughtcrime.securesms.webrtc.CallViewModel.State.CALL_INCOMING
import org.thoughtcrime.securesms.webrtc.CallViewModel.State.CALL_OUTGOING
import org.thoughtcrime.securesms.webrtc.CallViewModel.State.CALL_PRE_INIT
import org.thoughtcrime.securesms.webrtc.CallViewModel.State.CALL_RECONNECTING
import org.thoughtcrime.securesms.webrtc.CallViewModel.State.CALL_RINGING
import org.thoughtcrime.securesms.webrtc.Orientation
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager.AudioDevice.EARPIECE
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager.AudioDevice.SPEAKER_PHONE
import kotlin.math.asin

@AndroidEntryPoint
class WebRtcCallActivity : PassphraseRequiredActionBarActivity(), SensorEventListener {

    companion object {
        const val ACTION_PRE_OFFER = "pre-offer"
        const val ACTION_FULL_SCREEN_INTENT = "fullscreen-intent"
        const val ACTION_ANSWER = "answer"
        const val ACTION_END = "end-call"

        const val BUSY_SIGNAL_DELAY_FINISH = 5500L

        private const val CALL_DURATION_FORMAT = "HH:mm:ss"
    }

    private val viewModel by viewModels<CallViewModel>()
    private val glide by lazy { GlideApp.with(this) }
    private lateinit var binding: ActivityWebrtcBinding
    private var uiJob: Job? = null
    private var wantsToAnswer = false
        set(value) {
            field = value
            WebRtcCallService.broadcastWantsToAnswer(this, value)
        }
    private var hangupReceiver: BroadcastReceiver? = null

    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var lastOrientation = Orientation.UNKNOWN

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == ACTION_ANSWER) {
            val answerIntent = WebRtcCallService.acceptCallIntent(this)
            answerIntent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            ContextCompat.startForegroundService(this, answerIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)

        // Only enable auto-rotate if system auto-rotate is enabled
        if (isAutoRotateOn()) {
            // Initialize the SensorManager
            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

            // Initialize the sensors
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        }

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

        if (intent.action == ACTION_ANSWER) {
            answerCall()
        }
        if (intent.action == ACTION_PRE_OFFER) {
            wantsToAnswer = true
            answerCall() // this will do nothing, except update notification state
        }
        if (intent.action == ACTION_FULL_SCREEN_INTENT) {
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
        }

        binding.floatingRendererContainer.setOnClickListener {
            val swapVideoViewIntent =
                WebRtcCallService.swapVideoViews(this, viewModel.toggleVideoSwap())
            startService(swapVideoViewIntent)
        }

        binding.microphoneButton.setOnClickListener {
            val audioEnabledIntent =
                WebRtcCallService.microphoneIntent(this, !viewModel.microphoneEnabled)
            startService(audioEnabledIntent)
        }

        binding.speakerPhoneButton.setOnClickListener {
            val command =
                AudioManagerCommand.SetUserDevice(if (viewModel.isSpeaker) EARPIECE else SPEAKER_PHONE)
            WebRtcCallService.sendAudioManagerCommand(this, command)
        }

        binding.acceptCallButton.setOnClickListener {
            if (viewModel.currentCallState == CALL_PRE_INIT) {
                wantsToAnswer = true
                updateControls()
            }
            answerCall()
        }

        binding.declineCallButton.setOnClickListener {
            val declineIntent = WebRtcCallService.denyCallIntent(this)
            startService(declineIntent)
        }

        hangupReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                finish()
            }
        }

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(hangupReceiver!!, IntentFilter(ACTION_END))

        binding.enableCameraButton.setOnClickListener {
            Permissions.with(this)
                .request(Manifest.permission.CAMERA)
                .onAllGranted {
                    val intent = WebRtcCallService.cameraEnabled(this, !viewModel.videoEnabled)
                    startService(intent)
                }
                .execute()
        }

        binding.switchCameraButton.setOnClickListener {
            startService(WebRtcCallService.flipCamera(this))
        }

        binding.endCallButton.setOnClickListener {
            startService(WebRtcCallService.hangupIntent(this))
        }
        binding.backArrow.setOnClickListener {
            onBackPressed()
        }

    }

    //Function to check if Android System Auto-rotate is on or off
    private fun isAutoRotateOn(): Boolean {
        return Settings.System.getInt(
            contentResolver,
            Settings.System.ACCELEROMETER_ROTATION, 0
        ) == 1
    }

    override fun onResume() {
        super.onResume()
        rotationVectorSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            // Get the quaternion from the rotation vector sensor
            val quaternion = FloatArray(4)
            SensorManager.getQuaternionFromVector(quaternion, event.values)

            // Calculate Euler angles from the quaternion
            val pitch = asin(2.0 * (quaternion[0] * quaternion[2] - quaternion[3] * quaternion[1]))

            // Convert radians to degrees
            val pitchDegrees = Math.toDegrees(pitch).toFloat()

            // Determine the device's orientation based on the pitch and roll values
            val currentOrientation = when {
                pitchDegrees > 45  -> Orientation.LANDSCAPE
                pitchDegrees < -45 -> Orientation.REVERSED_LANDSCAPE
                else -> Orientation.PORTRAIT
            }

            if (currentOrientation != lastOrientation) {
                lastOrientation = currentOrientation
                Log.d("", "*********** orientation: $currentOrientation")
                viewModel.deviceOrientation = currentOrientation
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        hangupReceiver?.let { receiver ->
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        }

        rotationVectorSensor = null
    }

    private fun answerCall() {
        val answerIntent = WebRtcCallService.acceptCallIntent(this)
        ContextCompat.startForegroundService(this, answerIntent)
    }

    private fun updateControlsRotation(newRotation: Int) {
        with (binding) {
            val rotation = newRotation.toFloat()
            remoteRecipient.rotation = rotation
            speakerPhoneButton.rotation = rotation
            microphoneButton.rotation = rotation
            enableCameraButton.rotation = rotation
            switchCameraButton.rotation = rotation
            endCallButton.rotation = rotation
        }
    }

    private fun updateControls(state: CallViewModel.State? = null) {
        with(binding) {
            if (state == null) {
                if (wantsToAnswer) {
                    controlGroup.isVisible = true
                    remoteLoadingView.isVisible = true
                    incomingControlGroup.isVisible = false
                }
            } else {
                controlGroup.isVisible = state in listOf(
                    CALL_CONNECTED,
                    CALL_OUTGOING,
                    CALL_INCOMING
                ) || (state == CALL_PRE_INIT && wantsToAnswer)
                remoteLoadingView.isVisible =
                    state !in listOf(CALL_CONNECTED, CALL_RINGING, CALL_PRE_INIT) || wantsToAnswer
                incomingControlGroup.isVisible =
                    state in listOf(CALL_RINGING, CALL_PRE_INIT) && !wantsToAnswer
                reconnectingText.isVisible = state == CALL_RECONNECTING
                endCallButton.isVisible = endCallButton.isVisible || state == CALL_RECONNECTING
            }
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
                viewModel.callState.collect { state ->
                    Log.d("Loki", "Consuming view model state $state")
                    when (state) {
                        CALL_RINGING -> if (wantsToAnswer) {
                            answerCall()
                            wantsToAnswer = false
                        }
                        CALL_CONNECTED -> wantsToAnswer = false
                        else -> {}
                    }
                    updateControls(state)
                }
            }

            launch {
                viewModel.recipient.collect { latestRecipient ->
                    if (latestRecipient.recipient != null) {
                        val publicKey = latestRecipient.recipient.address.serialize()
                        val displayName = getUserDisplayName(publicKey)
                        supportActionBar?.title = displayName
                        val signalProfilePicture = latestRecipient.recipient.contactPhoto
                        val avatar = (signalProfilePicture as? ProfileContactPhoto)?.avatarObject
                        val sizeInPX =
                            resources.getDimensionPixelSize(R.dimen.extra_large_profile_picture_size)
                        binding.remoteRecipientName.text = displayName
                        if (signalProfilePicture != null && avatar != "0" && avatar != "") {
                            glide.clear(binding.remoteRecipient)
                            glide.load(signalProfilePicture)
                                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                                .circleCrop()
                                .error(
                                    AvatarPlaceholderGenerator.generate(
                                        this@WebRtcCallActivity,
                                        sizeInPX,
                                        publicKey,
                                        displayName
                                    )
                                )
                                .into(binding.remoteRecipient)
                        } else {
                            glide.clear(binding.remoteRecipient)
                            glide.load(
                                AvatarPlaceholderGenerator.generate(
                                    this@WebRtcCallActivity,
                                    sizeInPX,
                                    publicKey,
                                    displayName
                                )
                            )
                                .diskCacheStrategy(DiskCacheStrategy.ALL).circleCrop()
                                .into(binding.remoteRecipient)
                        }
                    } else {
                        glide.clear(binding.remoteRecipient)
                    }
                }
            }

            launch {
                while (isActive) {
                    val startTime = viewModel.callStartTime
                    if (startTime == -1L) {
                        binding.callTime.isVisible = false
                    } else {
                        binding.callTime.isVisible = true
                        binding.callTime.text = DurationFormatUtils.formatDuration(
                            System.currentTimeMillis() - startTime,
                            CALL_DURATION_FORMAT
                        )
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

            launch {
                viewModel.localVideoEnabledState.collect { isEnabled ->
                    binding.floatingRenderer.removeAllViews()
                    if (isEnabled) {
                        viewModel.floatingRenderer?.let { surfaceView ->
                            surfaceView.setZOrderOnTop(true)
                            binding.floatingRenderer.addView(surfaceView)
                        }
                    }

                    binding.floatingRenderer.isVisible = isEnabled
                    binding.enableCameraButton.isSelected = isEnabled
                    //binding.swapViewIcon.bringToFront()
                }
            }

            launch {
                viewModel.remoteVideoEnabledState.collect { isEnabled ->
                    binding.fullscreenRenderer.removeAllViews()
                    if (isEnabled) {
                        viewModel.fullscreenRenderer?.let { surfaceView ->
                            binding.fullscreenRenderer.addView(surfaceView)
                        }
                    }
                    binding.fullscreenRenderer.isVisible = isEnabled
                    binding.remoteRecipient.isVisible = !isEnabled
                    //binding.swapViewIcon.bringToFront()
                }
            }
        }
    }

    private fun getUserDisplayName(publicKey: String): String {
        val contact =
            DatabaseComponent.get(this).sessionContactDatabase().getContactWithSessionID(publicKey)
        return contact?.displayName(Contact.ContactContext.REGULAR) ?: publicKey
    }

    override fun onStop() {
        super.onStop()
        uiJob?.cancel()
        binding.fullscreenRenderer.removeAllViews()
        binding.floatingRenderer.removeAllViews()
    }
}