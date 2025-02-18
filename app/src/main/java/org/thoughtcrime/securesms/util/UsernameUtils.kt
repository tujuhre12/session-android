package org.thoughtcrime.securesms.util

import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.truncateIdForDisplay
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsernameUtils @Inject constructor(
    private val prefs: TextSecurePreferences,
    private val configFactory: ConfigFactory,
) {
    fun getCurrentUsernameWithAccountIdFallback(): String = prefs.getProfileName()
        ?: truncateIdForDisplay( prefs.getLocalNumber() ?: "")

    fun getCurrentUsername(): String? = prefs.getProfileName()

    fun saveCurrentUserName(name: String) {
        configFactory.withMutableUserConfigs {
            it.userProfile.setName(name)
        }
    }
}