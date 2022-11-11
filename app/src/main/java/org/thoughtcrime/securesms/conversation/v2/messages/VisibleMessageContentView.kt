package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.text.getSpans
import androidx.core.text.toSpannable
import androidx.core.view.isVisible
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewVisibleMessageContentBinding
import okhttp3.HttpUrl
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentTransferProgress
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.utilities.getColorFromAttr
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.conversation.v2.ModalUrlBottomSheet
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionUtilities
import org.thoughtcrime.securesms.conversation.v2.utilities.ModalURLSpan
import org.thoughtcrime.securesms.conversation.v2.utilities.TextUtilities.getIntersectedModalSpans
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.SearchUtil
import org.thoughtcrime.securesms.util.getAccentColor
import java.util.Locale
import kotlin.math.roundToInt

class VisibleMessageContentView : LinearLayout {
    private lateinit var binding:  ViewVisibleMessageContentBinding
    var onContentClick: MutableList<((event: MotionEvent) -> Unit)> = mutableListOf()
    var onContentDoubleTap: (() -> Unit)? = null
    var delegate: VisibleMessageViewDelegate? = null
    var indexInAdapter: Int = -1

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        binding = ViewVisibleMessageContentBinding.inflate(LayoutInflater.from(context), this, true)
    }
    // endregion

    // region Updating
    fun bind(message: MessageRecord, isStartOfMessageCluster: Boolean, isEndOfMessageCluster: Boolean,
             glide: GlideRequests, thread: Recipient, searchQuery: String?) {
        // Background
        val background = getBackground(message.isOutgoing)
        val color = if (message.isOutgoing) context.getAccentColor()
        else context.getColorFromAttr(R.attr.message_received_background_color)
        val filter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(color, BlendModeCompat.SRC_IN)
        background.colorFilter = filter
        binding.contentParent.background = background

        val mediaDownloaded = message is MmsMessageRecord && message.slideDeck.asAttachments().all { it.transferState == AttachmentTransferProgress.TRANSFER_PROGRESS_DONE }
        val mediaInProgress = message is MmsMessageRecord && message.slideDeck.asAttachments().any { it.isInProgress }
        val mediaThumbnailMessage = message is MmsMessageRecord && message.slideDeck.thumbnailSlide != null

        // reset visibilities / containers
        onContentClick.clear()
        binding.albumThumbnailView.clearViews()
        onContentDoubleTap = null

        if (message.isDeleted) {
            binding.deletedMessageView.root.isVisible = true
            binding.deletedMessageView.root.bind(message, getTextColor(context, message))
            return
        } else {
            binding.deletedMessageView.root.isVisible = false
        }
        // clear the body
        binding.bodyTextView.text = null


        binding.quoteView.root.isVisible = message is MmsMessageRecord && message.quote != null

        binding.linkPreviewView.isVisible = message is MmsMessageRecord && message.linkPreviews.isNotEmpty()

        binding.pendingAttachmentView.root.isVisible = !mediaDownloaded && !mediaInProgress && message is MmsMessageRecord && message.quote == null && message.linkPreviews.isEmpty()
        binding.voiceMessageView.root.isVisible = (mediaDownloaded || mediaInProgress) && message is MmsMessageRecord && message.slideDeck.audioSlide != null
        binding.documentView.root.isVisible = (mediaDownloaded || mediaInProgress) && message is MmsMessageRecord && message.slideDeck.documentSlide != null
        binding.albumThumbnailView.isVisible = mediaThumbnailMessage
        binding.openGroupInvitationView.root.isVisible = message.isOpenGroupInvitation

        var hideBody = false

        if (message is MmsMessageRecord && message.quote != null) {
            binding.quoteView.root.isVisible = true
            val quote = message.quote!!
            val quoteText = if (quote.isOriginalMissing) {
                context.getString(R.string.QuoteView_original_missing)
            } else {
                quote.text
            }
            binding.quoteView.root.bind(quote.author.toString(), quoteText, quote.attachment, thread,
                message.isOutgoing, message.isOpenGroupInvitation, message.threadId,
                quote.isOriginalMissing, glide)
            onContentClick.add { event ->
                val r = Rect()
                binding.quoteView.root.getGlobalVisibleRect(r)
                if (r.contains(event.rawX.roundToInt(), event.rawY.roundToInt())) {
                    delegate?.scrollToMessageIfPossible(quote.id)
                }
            }
        }

        when {
            // LINK PREVIEW
            message is MmsMessageRecord && message.linkPreviews.isNotEmpty() -> {
                binding.linkPreviewView.bind(message, glide, isStartOfMessageCluster, isEndOfMessageCluster)
                onContentClick.add { event -> binding.linkPreviewView.calculateHit(event) }
                // Body text view is inside the link preview for layout convenience
            }
            // AUDIO
            message is MmsMessageRecord && message.slideDeck.audioSlide != null -> {
                hideBody = true
                // Audio attachment
                if (mediaDownloaded || mediaInProgress || message.isOutgoing) {
                    binding.voiceMessageView.root.indexInAdapter = indexInAdapter
                    binding.voiceMessageView.root.delegate = context as? ConversationActivityV2
                    binding.voiceMessageView.root.bind(message, isStartOfMessageCluster, isEndOfMessageCluster)
                    // We have to use onContentClick (rather than a click listener directly on the voice
                    // message view) so as to not interfere with all the other gestures.
                    onContentClick.add { binding.voiceMessageView.root.togglePlayback() }
                    onContentDoubleTap = { binding.voiceMessageView.root.handleDoubleTap() }
                } else {
                    (message.slideDeck.audioSlide?.asAttachment() as? DatabaseAttachment)?.let { attachment ->
                        binding.pendingAttachmentView.root.bind(
                            PendingAttachmentView.AttachmentType.AUDIO,
                            getTextColor(context,message),
                            attachment
                        )
                        onContentClick.add { binding.pendingAttachmentView.root.showDownloadDialog(message.recipient, attachment) }
                    }
                }
            }
            // DOCUMENT
            message is MmsMessageRecord && message.slideDeck.documentSlide != null -> {
                hideBody = true
                // Document attachment
                if (mediaDownloaded || mediaInProgress || message.isOutgoing) {
                    binding.documentView.root.bind(message, getTextColor(context, message))
                } else {
                    (message.slideDeck.documentSlide?.asAttachment() as? DatabaseAttachment)?.let { attachment ->
                        binding.pendingAttachmentView.root.bind(
                            PendingAttachmentView.AttachmentType.DOCUMENT,
                            getTextColor(context,message),
                            attachment
                            )
                        onContentClick.add { binding.pendingAttachmentView.root.showDownloadDialog(message.recipient, attachment) }
                    }
                }
            }
            // IMAGE / VIDEO
            message is MmsMessageRecord && message.slideDeck.asAttachments().isNotEmpty() -> {
                if (mediaDownloaded || mediaInProgress || message.isOutgoing) {
                    // isStart and isEnd of cluster needed for calculating the mask for full bubble image groups
                    // bind after add view because views are inflated and calculated during bind
                    binding.albumThumbnailView.bind(
                        glideRequests = glide,
                        message = message,
                        isStart = isStartOfMessageCluster,
                        isEnd = isEndOfMessageCluster
                    )
                    val layoutParams = binding.albumThumbnailView.layoutParams as ConstraintLayout.LayoutParams
                    layoutParams.horizontalBias = if (message.isOutgoing) 1f else 0f
                    binding.albumThumbnailView.layoutParams = layoutParams
                    onContentClick.add { event ->
                        binding.albumThumbnailView.calculateHitObject(event, message, thread)
                    }
                } else {
                    hideBody = true
                    binding.albumThumbnailView.clearViews()
                    val firstAttachment = message.slideDeck.asAttachments().first() as? DatabaseAttachment
                    firstAttachment?.let { attachment ->
                        binding.pendingAttachmentView.root.bind(
                            PendingAttachmentView.AttachmentType.MEDIA,
                            getTextColor(context,message),
                            attachment
                            )
                        onContentClick.add {
                            binding.pendingAttachmentView.root.showDownloadDialog(message.recipient, attachment)
                        }
                    }
                }
            }
            message.isOpenGroupInvitation -> {
                hideBody = true
                binding.openGroupInvitationView.root.bind(message, getTextColor(context, message))
                onContentClick.add { binding.openGroupInvitationView.root.joinOpenGroup() }
            }
        }

        binding.bodyTextView.isVisible = message.body.isNotEmpty() && !hideBody

        if (message.body.isNotEmpty() && !hideBody) {
            val color = getTextColor(context, message)
            binding.bodyTextView.setTextColor(color)
            binding.bodyTextView.setLinkTextColor(color)
            val body = getBodySpans(context, message, searchQuery)
            binding.bodyTextView.text = body
            onContentClick.add { e: MotionEvent ->
                binding.bodyTextView.getIntersectedModalSpans(e).iterator().forEach { span ->
                    span.onClick(binding.bodyTextView)
                }
            }
        }
        val layoutParams = binding.contentParent.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.horizontalBias = if (message.isOutgoing) 1f else 0f
        binding.contentParent.layoutParams = layoutParams
    }

    private fun ViewVisibleMessageContentBinding.barrierViewsGone(): Boolean =
        listOf<View>(albumThumbnailView, linkPreviewView, voiceMessageView.root, quoteView.root).none { it.isVisible }

    private fun getBackground(isOutgoing: Boolean): Drawable {
        val backgroundID = if (isOutgoing) R.drawable.message_bubble_background_sent_alone else R.drawable.message_bubble_background_received_alone
        return ResourcesCompat.getDrawable(resources, backgroundID, context.theme)!!
    }

    fun recycle() {
        arrayOf(
            binding.deletedMessageView.root,
            binding.pendingAttachmentView.root,
            binding.voiceMessageView.root,
            binding.openGroupInvitationView.root,
            binding.documentView.root,
            binding.quoteView.root,
            binding.linkPreviewView,
            binding.albumThumbnailView,
            binding.bodyTextView
        ).forEach { view: View -> view.isVisible = false }
    }

    fun playVoiceMessage() {
        binding.voiceMessageView.root.togglePlayback()
    }
    // endregion

    // region Convenience
    companion object {

        fun getBodySpans(context: Context, message: MessageRecord, searchQuery: String?): Spannable {
            var body = message.body.toSpannable()

            body = MentionUtilities.highlightMentions(body, message.isOutgoing, message.threadId, context)
            body = SearchUtil.getHighlightedSpan(Locale.getDefault(),
                { BackgroundColorSpan(Color.WHITE) }, body, searchQuery)
            body = SearchUtil.getHighlightedSpan(Locale.getDefault(),
                { ForegroundColorSpan(Color.BLACK) }, body, searchQuery)

            Linkify.addLinks(body, Linkify.WEB_URLS)

            // replace URLSpans with ModalURLSpans
            body.getSpans<URLSpan>(0, body.length).toList().forEach { urlSpan ->
                val updatedUrl = urlSpan.url.let { HttpUrl.parse(it).toString() }
                val replacementSpan = ModalURLSpan(updatedUrl) { url ->
                    val activity = context as AppCompatActivity
                    ModalUrlBottomSheet(url).show(activity.supportFragmentManager, "Open URL Dialog")
                }
                val start = body.getSpanStart(urlSpan)
                val end = body.getSpanEnd(urlSpan)
                val flags = body.getSpanFlags(urlSpan)
                body.removeSpan(urlSpan)
                body.setSpan(replacementSpan, start, end, flags)
            }
            return body
        }

        @ColorInt
        fun getTextColor(context: Context, message: MessageRecord): Int {
            val colorAttribute = if (message.isOutgoing) {
                // sent
                R.attr.message_sent_text_color
            } else {
                // received
                R.attr.message_received_text_color
            }
            return context.getColorFromAttr(colorAttribute)
        }
    }
    // endregion
}
