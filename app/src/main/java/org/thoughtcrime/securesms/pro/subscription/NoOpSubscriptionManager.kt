package org.thoughtcrime.securesms.pro.subscription

/**
 * An implementation representing a lack of support for subscription
 */
class NoOpSubscriptionManager: SubscriptionManager {
    override val id = "noop"
    override val displayName = ""
    override val description = ""
    override val iconRes = null
}