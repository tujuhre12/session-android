package org.thoughtcrime.securesms.pro.subscription

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Google Play Store implementation of our subscription manager
 */
@Singleton
class PlayStoreSubscriptionManager @Inject constructor(): SubscriptionManager {
    override val id = "google_play_store"
    override val displayName = ""
    override val description = ""
    override val iconRes = null
}