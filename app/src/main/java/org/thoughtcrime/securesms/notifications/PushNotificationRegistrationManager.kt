package org.thoughtcrime.securesms.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import network.loki.messenger.libsession_util.GroupKeysConfig
import network.loki.messenger.libsession_util.util.KeyPair
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.notifications.TokenFetcher
import org.session.libsession.snode.SwarmAuth
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.getGroup
import org.session.libsession.utilities.watchUserConfigChanges
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushNotificationRegistrationManager @Inject constructor(
    private val pushRegistryV2: PushRegistryV2,
    private val configFactory: ConfigFactoryProtocol,
    private val storage: StorageProtocol,
    private val tokenFetcher: TokenFetcher,
    private val prefs: TextSecurePreferences,
) {
    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class, FlowPreview::class)
    private val activeSubscriptions =
        // The combine outputs current user id and the FCM token, only if the push is enabled
        combine(
            prefs.watchLocalNumber(),
            prefs.pushEnabled,
            tokenFetcher.token, // The token is only here to trigger a flow update, we won't need it as part of the output
        ) { myAccountId, pushEnabled, _ -> myAccountId.takeIf { pushEnabled }?.let(::AccountId) }

            // This flatMap outputs the list all of the AccountIDs that should be subscribed to
            .flatMapLatest { myAccountId ->
                if (myAccountId == null) {
                    flowOf(emptySet())
                } else {
                    // We need to listen to every config update, as every group's subscription
                    // might need update when the config changes.
                    configFactory
                        .configUpdateNotifications
                        .debounce(1_000L) // Don't want to create many objects too often when the config changes
                        .map {
                            val allGroupIDs =
                                configFactory.withUserConfigs { it.userGroups.allClosedGroupInfo() }
                                    .asSequence()
                                    .filter { it.shouldPoll }
                                    .map { it.groupAccountId }

                            (allGroupIDs + sequenceOf(myAccountId)).toSet()
                        }
                }
            }

            // This scan compares the existing subscriptions with the new list of AccountIDs to subscribe to,
            // then register or unregister the push notification accordingly
            .scan(emptyMap<AccountId, Subscription>()) { previous, activeAccountIDs ->
                supervisorScope {
                    // Fetch the FCM token before we do anything with the subscriptions
                    val token = withTimeoutOrNull(1000L) { tokenFetcher.fetch() }
                    if (token == null) {
                        Log.w(TAG, "Unable to get FCM token in time")
                        return@supervisorScope previous
                    }

                    // Go through the previous subscription and unregister the ones not in the new set
                    for ((id, subscription) in previous) {
                        if (id !in activeAccountIDs) {
                            Log.d(TAG, "Unregistering push notification for $id")
                            unregister(subscription)
                        }
                    }

                    // Go through the new set and register the ones not in the previous subscription,
                    // or the token has changed since the last registration
                    activeAccountIDs.associateWith { id ->
                        var subscription = previous[id]
                        if (subscription == null) {
                            Log.d(TAG, "Registering push notification for $id")
                            subscription = register(id, token)
                        } else if (token != subscription.token) {
                            Log.d(TAG, "Re-registering push notification for $id")
                            unregister(subscription)
                            subscription = register(id, token)
                        } else {
                            // Give the existing subscription a chance to update its internal
                            // state in the case when no registration/de-registration is needed.
                            // This is important as some of the subscription's internal states
                            // needed to be up-to-date with the config system to be able to
                            // unsubscribe itself properly.
                            subscription = subscription.update(id, configFactory)
                        }

                        subscription
                    }

                    emptyMap()
                }
            }

            .stateIn(GlobalScope, SharingStarted.Eagerly, emptyMap())

    private fun CoroutineScope.unregister(subscription: Subscription) {
        TODO()
    }

    private fun CoroutineScope.register(accountId: AccountId, token: String): Subscription {
        TODO()
    }

    /**
     * A interface that records a subscription made for an account.
     *
     * This interface is used for tracking the pushing notification, AND also used for
     * unsubscribing itself, so it must contain enough information to do the unregistering.
     */
    private sealed class Subscription {
        abstract val token: String

        open fun update(id: AccountId, configFactory: ConfigFactoryProtocol): Subscription = this

        class OneToOneSubscription(
            val keys: KeyPair,
            override val token: String,
        ) : Subscription()

        class GroupSubscription(
            val currentAuth: SwarmAuth,
            override val token: String,
        ) : Subscription() {
            override fun update(id: AccountId, configFactory: ConfigFactoryProtocol): Subscription {
                val newAuth = configFactory.snapshotGroupAuth(id, currentAuth)
                if (newAuth != currentAuth) {
                    TODO()
                } else {
                    return this
                }
            }
        }

        class GroupAdminSubscription(
            val adminKey: ByteArray,
            override val token: String,
        ) : Subscription()
    }

    companion object {
        private const val TAG = "PushNotificationRegistrationManager"
    }
}