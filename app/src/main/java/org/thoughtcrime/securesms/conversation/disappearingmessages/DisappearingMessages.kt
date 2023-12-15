package org.thoughtcrime.securesms.conversation.disappearingmessages

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsession.utilities.SSKEnvironment.MessageExpirationManagerProtocol
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import javax.inject.Inject

class DisappearingMessages @Inject constructor(
    @ApplicationContext private val context: Context,
    private val textSecurePreferences: TextSecurePreferences,
    private val messageExpirationManager: MessageExpirationManagerProtocol,
) {
    fun set(threadId: Long, address: Address, mode: ExpiryMode) {
        val expiryChangeTimestampMs = SnodeAPI.nowWithOffset
        MessagingModuleConfiguration.shared.storage.setExpirationConfiguration(ExpirationConfiguration(threadId, mode, expiryChangeTimestampMs))

        val message = ExpirationTimerUpdate(mode).apply {
            sender = textSecurePreferences.getLocalNumber()
            isSenderSelf = true
            recipient = address.serialize()
            sentTimestamp = expiryChangeTimestampMs
        }

        messageExpirationManager.setExpirationTimer(message)
        MessageSender.send(message, address)
        ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(context)
    }
}
