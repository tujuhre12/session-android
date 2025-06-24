package org.thoughtcrime.securesms.pro

import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.utilities.TextSecurePreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProStatusManager @Inject constructor(
    private val prefs: TextSecurePreferences
){
    companion object {
        //todo PRO TEMPORARY STRINGS ONLY!!!
        const val UPDATETP = "Upgrade to"
        const val CTA_TXT = "Want to send longer messages? Upgrade to Session Pro to send longer messages up to 10,000 characters"
        const val CTA_FEAT1 = "Send messages up to 10k characters"
        const val CTA_FEAT2 = "Share files beyond the 10MB limit"
        const val CTA_FEAT3 = "Heaps more exclusive features"
        const val UPGRADE = "Upgrade"
        const val PRO_URL = "https://getsession.org/"
    }

    private val MAX_CHARACTER_PRO = 10000
    private val MAX_CHARACTER_REGULAR = 2000

    fun isCurrentUserPro(): Boolean {
        // if the debug is set, return that
        if (prefs.forceCurrentUserAsPro()) return true

        // otherwise return the true value
        return false //todo PRO implement real logic once it's in
    }

    /**
     * Returns the max length that a visible message can have based on its Pro status
     */
    fun getIncomingMessageMaxLength(message: VisibleMessage): Int {
        // if the debug is set, return that
        if (prefs.forceIncomingMessagesAsPro()) return MAX_CHARACTER_PRO

        // otherwise return the true value
        return MAX_CHARACTER_REGULAR //todo PRO implement real logic once it's in
    }

    // Temporary method and concept that we should remove once Pro is out
    fun isPostPro(): Boolean {
        return prefs.forcePostPro()
    }

    fun getCharacterLimit(): Int {
        return if (isCurrentUserPro()) MAX_CHARACTER_PRO else MAX_CHARACTER_REGULAR
    }
}