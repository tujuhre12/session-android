package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewControlMessageBinding
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.utilities.ExpirationUtil.getExpirationDisplayValue
import org.session.libsession.utilities.getExpirationTypeDisplayValue
import org.thoughtcrime.securesms.conversation.disappearingmessages.DisappearingMessages
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.showSessionDialog
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val MessageRecord.expiryMode get() = if (expiresIn <= 0) ExpiryMode.NONE
    else if (expireStarted == timestamp) ExpiryMode.AfterSend(expiresIn / 1000)
    else ExpiryMode.AfterRead(expiresIn / 1000)

@AndroidEntryPoint
class ControlMessageView : LinearLayout {

    private lateinit var binding: ViewControlMessageBinding

    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    @Inject lateinit var disappearingMessages: DisappearingMessages

    private fun initialize() {
        binding = ViewControlMessageBinding.inflate(LayoutInflater.from(context), this, true)
        layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
    }

    fun bind(message: MessageRecord, previous: MessageRecord?) {
        binding.dateBreakTextView.showDateBreak(message, previous)
        binding.iconImageView.isGone = true
        binding.expirationTimerView.isGone = true
        binding.followSetting.isGone = true
        var messageBody: CharSequence = message.getDisplayBody(context)
        binding.root.contentDescription = null
        when {
            message.isExpirationTimerUpdate -> {
                binding.apply {
                    expirationTimerView.isVisible = true


                    expirationTimerView.setExpirationTime(message.expireStarted, message.expiresIn)

                    followSetting.isVisible = ExpirationConfiguration.isNewConfigEnabled
                        && !message.isOutgoing
                        && message.expiryMode != (MessagingModuleConfiguration.shared.storage.getExpirationConfiguration(message.threadId)?.expiryMode ?: ExpiryMode.NONE)

                    followSetting.setOnClickListener {
                        context.showSessionDialog {
                            title(R.string.dialog_disappearing_messages_follow_setting_title)
                            if (message.expiresIn == 0L) {
                                text(R.string.dialog_disappearing_messages_follow_setting_off_body)
                            } else {
                                text(
                                    context.getString(
                                        R.string.dialog_disappearing_messages_follow_setting_on_body,
                                        getExpirationDisplayValue(context, message.expiresIn.milliseconds),
                                        context.getExpirationTypeDisplayValue(message.isNotDisappearAfterRead)
                                    )
                                )
                            }
                            destructiveButton(if (message.expiresIn == 0L) R.string.dialog_disappearing_messages_follow_setting_confirm else R.string.dialog_disappearing_messages_follow_setting_set) {
                                disappearingMessages.set(message.threadId, message.recipient.address, message.expiryMode)
                            }
                            cancelButton()
                        }
                    }
                }
            }
            message.isMediaSavedNotification -> {
                binding.iconImageView.apply {
                    setImageDrawable(
                        ResourcesCompat.getDrawable(resources, R.drawable.ic_file_download_white_36dp, context.theme)
                    )
                    isVisible = true
                }
            }
            message.isMessageRequestResponse -> {
                messageBody = context.getString(R.string.message_requests_accepted)
                binding.root.contentDescription=context.getString(R.string.AccessibilityId_message_request_config_message)
            }
            message.isCallLog -> {
                val drawable = when {
                    message.isIncomingCall -> R.drawable.ic_incoming_call
                    message.isOutgoingCall -> R.drawable.ic_outgoing_call
                    message.isFirstMissedCall -> R.drawable.ic_info_outline_light
                    else -> R.drawable.ic_missed_call
                }
                binding.iconImageView.apply {
                    setImageDrawable(ResourcesCompat.getDrawable(resources, drawable, context.theme))
                    isVisible = true
                }
            }
        }

        binding.textView.text = messageBody
    }

    fun recycle() {

    }
}