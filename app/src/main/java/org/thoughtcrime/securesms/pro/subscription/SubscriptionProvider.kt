package org.thoughtcrime.securesms.pro.subscription


/**
 * Represents a subscription store / provider
 */
data class SubscriptionProvider(
    val id: String,
    val displayName: String = "",
    val description: String = "",
    val iconRes: Int? = null
)

