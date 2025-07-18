package org.thoughtcrime.securesms.database

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import network.loki.messenger.libsession_util.util.BlindKeyAPI
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.userConfigsChanged
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.util.mapStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private typealias CommunityServerUrl = String
private typealias BlindedAddress = AccountId

/**
 * A class that handles the blind mappings of addresses reactively.
 */
@Singleton
class BlindMappingRepository @Inject constructor(
    configFactory: ConfigFactory,
    prefs: TextSecurePreferences,
) {

    /**
     * A [StateFlow] that emits a map of community server URLs to their respective mappings of
     * blinded addresses to 05 prefixed addresses
     */
    @Suppress("OPT_IN_USAGE")
    val mappings: StateFlow<Map<CommunityServerUrl, Map<BlindedAddress, AccountId>>> = prefs.watchLocalNumber()
        .filterNotNull()
        .flatMapLatest { localAddress ->
            configFactory
                .userConfigsChanged(200L)
                .onStart { emit(Unit) }
                .map {
                    configFactory.withUserConfigs { configs ->
                        Pair(
                            configs.userGroups.allCommunityInfo().map { it.community },
                            configs.contacts.all().map { AccountId(it.id) }
                                    + AccountId(localAddress)
                        )
                    }
                }
        }
        .distinctUntilChanged()
        .map { (allCommunities, allContacts) ->
            allCommunities.asSequence()
                .associate { community ->
                    community.baseUrl to allContacts.asSequence()
                        .flatMap { contactAddress ->
                            val allBlindIDs = BlindKeyAPI.blind15Ids(
                                sessionId = contactAddress.hexString,
                                serverPubKey = community.pubKeyHex
                            ).asSequence() + BlindKeyAPI.blind25Id(
                                sessionId = contactAddress.hexString,
                                serverPubKey = community.pubKeyHex
                            )

                            allBlindIDs.map { blindId ->
                                AccountId(blindId) to contactAddress
                            }
                        }
                        .toMap()
                }
        }
        .stateIn(GlobalScope, started = SharingStarted.Eagerly, initialValue = emptyMap())

    fun getMapping(
        serverUrl: CommunityServerUrl,
        blindedAddress: BlindedAddress
    ): AccountId? {
        return mappings.value[serverUrl]?.get(blindedAddress)
    }

    fun getReverseMappings(
        contactAddress: AccountId,
    ): List<Pair<CommunityServerUrl, BlindedAddress>> {
        return mappings.value.flatMap { (communityUrl, mapping) ->
            mapping.filter { it.value == contactAddress }
                .map { (blindedAddress, _) -> communityUrl to blindedAddress }
        }
    }

    fun observeMapping(
        communityAddress: CommunityServerUrl,
        blindedAddress: BlindedAddress
    ): StateFlow<AccountId?> {
        return mappings.mapStateFlow(GlobalScope) { it[communityAddress]?.get(blindedAddress) }
    }
}