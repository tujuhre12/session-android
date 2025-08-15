package org.thoughtcrime.securesms.pro.subscription

import jakarta.inject.Inject
import org.session.libsession.utilities.TextSecurePreferences

/**
 * Helper class to handle the selection and management of our available subscription providers
 */
class SubscriptionProviderCoordinator @Inject constructor(
    private val providerManager: SubscriptionManagerFactory,
    private val prefs: TextSecurePreferences
) {

    private var currentManager: SubscriptionManager? = null
    private var currentProvider: SubscriptionProvider? = null

    suspend fun initializeSubscriptions() {
        val providers = providerManager.getAvailableProviders()

        when {
            providers.isEmpty() -> {
                // Use NoOp
                currentProvider = null
                currentManager = NoOpSubscriptionManager()
            }
            providers.size == 1 -> {
                // Auto-select single option
                selectProvider(providers.first())
            }
            else -> {
                // Check if user has previously selected a provider
                val savedProviderId = prefs.getSubscriptionProvider()
                val savedProvider = providers.find { it.id == savedProviderId }

                if (savedProvider != null) {
                    selectProvider(savedProvider)
                } else {
                    // User needs to choose - current manager remains null
                    currentProvider = null
                    currentManager = null
                }
            }
        }
    }

    fun switchProvider(providerId: String) {
        val provider = providerManager.getAvailableProviders()
            .find { it.id == providerId }
            ?: throw IllegalArgumentException("Provider not found: $providerId")

        selectProvider(provider)

        // Save user's choice
        prefs.setSubscriptionProvider(providerId)
    }

    private fun selectProvider(provider: SubscriptionProvider) {
        currentProvider = provider
        currentManager = providerManager.createManager(provider.id)
    }

    fun getCurrentProvider(): SubscriptionProvider? = currentProvider

    fun getSubscriptionManager(): SubscriptionManager {
        return currentManager ?: throw IllegalStateException(
            "No subscription provider selected. Call initializeSubscriptions() first."
        )
    }

    fun needsProviderSelection(): Boolean {
        return providerManager.getAvailableProviders().size > 1 && currentProvider == null
    }
}