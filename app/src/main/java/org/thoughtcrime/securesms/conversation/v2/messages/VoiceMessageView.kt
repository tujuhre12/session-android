package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewVoiceMessageBinding
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.audio.AudioSlidePlayer
import org.thoughtcrime.securesms.components.CornerMask
import org.thoughtcrime.securesms.conversation.v2.utilities.MessageBubbleUtilities
import org.thoughtcrime.securesms.database.AttachmentDatabase
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@AndroidEntryPoint
class VoiceMessageView : RelativeLayout, AudioSlidePlayer.Listener {

    @Inject lateinit var attachmentDb: AttachmentDatabase

    private val binding: ViewVoiceMessageBinding by lazy { ViewVoiceMessageBinding.bind(this) }
    private val cornerMask by lazy { CornerMask(this) }
    private var isPlaying = false
    set(value) {
        field = value
        renderIcon()
    }
    private var progress = 0.0
    private var durationMS = 0L
    private var player: AudioSlidePlayer? = null
    var delegate: VisibleMessageViewDelegate? = null
    var indexInAdapter = -1

    private var onFinishVoiceMessageDuration: String? = null

    // region Lifecycle
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)



    override fun onFinishInflate() {
        super.onFinishInflate()

        val formattedVoiceMessageDuration = String.format("%01d:%02d", TimeUnit.MILLISECONDS.toMinutes(0), TimeUnit.MILLISECONDS.toSeconds(0))
        Log.i("ACL", "Hit VoiceMessageView.onFinishInflate with formatted duration: " + formattedVoiceMessageDuration)

        Log.i("ACL", "In onFinishInflate, duration is: " + durationMS + " and progress is: " + progress)

        Log.i("ACL", "Player is: " + player)
        if (player != null) {
            Log.i("ACL", "Player duration: " + player?.duration + ", player progress: " + player?.progress)
        }

        val goingWithDuration = onFinishVoiceMessageDuration ?: formattedVoiceMessageDuration

        binding.voiceMessageViewDurationTextView.text = "fooo"
    }

    // endregion

    // region Updating
    fun bind(message: MmsMessageRecord, isStartOfMessageCluster: Boolean, isEndOfMessageCluster: Boolean) {
        val audio = message.slideDeck.audioSlide!!
        binding.voiceMessageViewLoader.isVisible = audio.isInProgress
        val cornerRadii = MessageBubbleUtilities.calculateRadii(context, isStartOfMessageCluster, isEndOfMessageCluster, message.isOutgoing)
        cornerMask.setTopLeftRadius(cornerRadii[0])
        cornerMask.setTopRightRadius(cornerRadii[1])
        cornerMask.setBottomRightRadius(cornerRadii[2])
        cornerMask.setBottomLeftRadius(cornerRadii[3])

        // only process audio if downloaded
        if (audio.isPendingDownload || audio.isInProgress) {
            this.player = null
            return
        }

        val player = AudioSlidePlayer.createFor(context.applicationContext, audio, this)
        this.player = player

        (audio.asAttachment() as? DatabaseAttachment)?.let { attachment ->
            attachmentDb.getAttachmentAudioExtras(attachment.attachmentId)?.let { audioExtras ->
                if (audioExtras.durationMs > 0) {
                    durationMS = audioExtras.durationMs
                    binding.voiceMessageViewDurationTextView.visibility = VISIBLE

                    val anotherFormattedDuration = String.format("%01d:%02d", TimeUnit.MILLISECONDS.toMinutes(audioExtras.durationMs), TimeUnit.MILLISECONDS.toSeconds(audioExtras.durationMs) % 60)
                    Log.i("ACL", "Another formatted duration is: " + anotherFormattedDuration)

                    binding.voiceMessageViewDurationTextView.text = anotherFormattedDuration

                    onFinishVoiceMessageDuration = anotherFormattedDuration
                } else {
                    Log.i("ACL", "For some reason audioExtras.durationMs was NOT greater than zero!")
                }
            }
        }
    }

    override fun onPlayerStart(player: AudioSlidePlayer) {
        isPlaying = true
    }

    override fun onPlayerProgress(player: AudioSlidePlayer, progress: Double, unused: Long) {
        if (progress == 1.0) {
            togglePlayback()
            handleProgressChanged(0.0)
            delegate?.playVoiceMessageAtIndexIfPossible(indexInAdapter + 1)
        } else {
            handleProgressChanged(progress)
        }
    }

    private fun handleProgressChanged(progress: Double) {
        this.progress = progress
        binding.voiceMessageViewDurationTextView.text = String.format("%01d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(durationMS - (progress * durationMS.toDouble()).roundToLong()),
            TimeUnit.MILLISECONDS.toSeconds(durationMS - (progress * durationMS.toDouble()).roundToLong()) % 60)
        val layoutParams = binding.progressView.layoutParams as RelativeLayout.LayoutParams
        layoutParams.width = (width.toFloat() * progress.toFloat()).roundToInt()
        binding.progressView.layoutParams = layoutParams
    }

    override fun onPlayerStop(player: AudioSlidePlayer) {
        isPlaying = false
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        cornerMask.mask(canvas)
    }

    private fun renderIcon() {
        val iconID = if (isPlaying) R.drawable.exo_icon_pause else R.drawable.exo_icon_play
        binding.voiceMessagePlaybackImageView.setImageResource(iconID)
    }

    // endregion

    // region Interaction
    fun togglePlayback() {
        val player = this.player ?: return
        isPlaying = !isPlaying
        if (isPlaying) {
            player.play(progress)
        } else {
            player.stop()
        }
    }

    fun handleDoubleTap() {
        val player = this.player ?: return
        player.playbackSpeed = if (player.playbackSpeed == 1.0f) 1.5f else 1.0f
    }
    // endregion
}
