package org.thoughtcrime.securesms.pro.subscription


/**
 * Handles the available subscription managers for a given build type
 */
interface SubscriptionManagerFactory {
    fun getAvailableProviders(): List<SubscriptionProvider>
    fun createManager(providerId: String): SubscriptionManager
}

