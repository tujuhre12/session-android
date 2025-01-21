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

    companion object {
        // The duration in milliseconds of the latest voice message. This is set from ConversationActivityV2.sendVoiceMessage
        // so that we can display the voice message duration while it uploads (we do not seem to have this information until
        // upload completes otherwise).
        var latestVoiceMessageDurationMS = -1L
        var formattedLatestVoiceMessageDuration = ""
    }

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

    private var finalFormattedVoiceMessageDuration: String? = null

    // region Lifecycle
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // Note: onFinishInflate occurs before `bind`
    override fun onFinishInflate() {
        super.onFinishInflate()
        val durationString = String.format("%01d:%02d", TimeUnit.MILLISECONDS.toMinutes(latestVoiceMessageDurationMS), TimeUnit.MILLISECONDS.toSeconds(latestVoiceMessageDurationMS) % 60)
        binding.voiceMessageViewDurationTextView.text = durationString
    }

    // endregion

    // region Updating
    fun bind(message: MmsMessageRecord, isStartOfMessageCluster: Boolean, isEndOfMessageCluster: Boolean) {
        val audio = message.slideDeck.audioSlide!!
        binding.voiceMessageViewLoader.isVisible = audio.isDownloadInProgress
        val cornerRadii = MessageBubbleUtilities.calculateRadii(context, isStartOfMessageCluster, isEndOfMessageCluster, message.isOutgoing)
        cornerMask.setTopLeftRadius(cornerRadii[0])
        cornerMask.setTopRightRadius(cornerRadii[1])
        cornerMask.setBottomRightRadius(cornerRadii[2])
        cornerMask.setBottomLeftRadius(cornerRadii[3])

        // Only process audio if downloaded
        if (audio.isPendingDownload || audio.isDownloadInProgress) {
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

                    val formattedVoiceMessageDuration = String.format("%01d:%02d", TimeUnit.MILLISECONDS.toMinutes(audioExtras.durationMs), TimeUnit.MILLISECONDS.toSeconds(audioExtras.durationMs) % 60)
                    binding.voiceMessageViewDurationTextView.text = formattedVoiceMessageDuration
                    finalFormattedVoiceMessageDuration = formattedVoiceMessageDuration
                } else {
                    Log.w("AudioMessageView", "For some reason audioExtras.durationMs was NOT greater than zero!")
                    binding.voiceMessageViewDurationTextView.text = context.getString(R.string.unknown)
                }
            }
        }
    }

    override fun onPlayerStart(player: AudioSlidePlayer) { isPlaying = true  }
    override fun onPlayerStop(player: AudioSlidePlayer)  { isPlaying = false }

    override fun onPlayerProgress(player: AudioSlidePlayer, progress: Double, unused: Long) {
        // If the voice message has reached the end then stop it and reset the progress back to the start..
        if (progress == 1.0) {
            togglePlayback()
            handleProgressChanged(0.0)
            delegate?.playVoiceMessageAtIndexIfPossible(indexInAdapter + 1)

        } else {
            // ..otherwise continue playing the voice message.
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
        if (this.player == null) {
            Log.w("VoiceMessageView", "Could not get player to adjust voice message playback speed.")
            return
        }
        this.player?.playbackSpeed = if (this.player?.playbackSpeed == 1f) 1.5f else 1f
    }
    // endregion
}
