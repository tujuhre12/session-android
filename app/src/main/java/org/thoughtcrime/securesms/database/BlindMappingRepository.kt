package org.thoughtcrime.securesms.database

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import network.loki.messenger.libsession_util.util.BlindKeyAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.userConfigsChanged
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.dependencies.ManagerScope
import org.thoughtcrime.securesms.util.mapStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private typealias CommunityServerUrl = String

/**
 * A class that handles the blind mappings of addresses reactively.
 */
@Singleton
class BlindMappingRepository @Inject constructor(
    configFactory: ConfigFactory,
    prefs: TextSecurePreferences,
    @param:ManagerScope private val scope: CoroutineScope,
) {

    /**
     * A [StateFlow] that emits a map of community server URLs to their respective mappings of
     * blinded addresses to 05 prefixed addresses
     */
    @Suppress("OPT_IN_USAGE")
    val mappings: StateFlow<Map<CommunityServerUrl, Map<Address.Blinded, Address.Standard>>> = prefs.watchLocalNumber()
        .filterNotNull()
        .flatMapLatest { localAddress ->
            configFactory
                .userConfigsChanged(200L)
                .onStart { emit(Unit) }
                .map {
                    configFactory.withUserConfigs { configs ->
                        Pair(
                            configs.userGroups.allCommunityInfo().map { it.community },
                            configs.contacts.all().map { Address.Standard(AccountId(it.id)) }
                                    + Address.Standard(AccountId(localAddress))
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
                                sessionId = contactAddress.id.hexString,
                                serverPubKey = community.pubKeyHex
                            ).asSequence() + BlindKeyAPI.blind25Id(
                                sessionId = contactAddress.id.hexString,
                                serverPubKey = community.pubKeyHex
                            )

                            allBlindIDs.map { blindId ->
                                Address.Blinded(AccountId(blindId)) to contactAddress
                            }
                        }
                        .toMap()
                }
        }
        .stateIn(GlobalScope, started = SharingStarted.Eagerly, initialValue = emptyMap())

    fun getMapping(
        serverUrl: CommunityServerUrl,
        blindedAddress: Address.Blinded
    ): Address.Standard? {
        return mappings.value[serverUrl]?.get(blindedAddress)
    }

    fun findMappings(blindedAddress: Address.Blinded): Sequence<Pair<CommunityServerUrl, Address.Standard>> {
        return mappings.value
            .asSequence()
            .mapNotNull { (url, mapping) ->
                mapping.get(blindedAddress)?.let { url to it }
            }
    }

    fun getReverseMappings(
        contactAddress: Address.Standard,
    ): List<Pair<CommunityServerUrl, Address.Blinded>> {
        return mappings.value.flatMap { (communityUrl, mapping) ->
            mapping.filter { it.value == contactAddress }
                .map { (blindedAddress, _) -> communityUrl to blindedAddress }
        }
    }

    fun observeMapping(
        communityAddress: CommunityServerUrl,
        blindedAddress: Address.Blinded
    ): StateFlow<Address.Standard?> {
        return mappings.mapStateFlow(scope) { it[communityAddress]?.get(blindedAddress) }
    }
}