package org.thoughtcrime.securesms.pro.subscription

/**
 * Represents the implementation details of a given subscription provider
 */
interface SubscriptionManager {
    val id: String
    val displayName: String
    val description: String
    val iconRes: Int?

    enum class SubscriptionType {
        ONE_MONTH, THREE_MONTHS, TWELVE_MONTHS
    }

    fun purchasePlan(subscriptionType: SubscriptionType)
}