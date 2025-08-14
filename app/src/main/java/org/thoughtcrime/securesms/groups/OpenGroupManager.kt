package org.thoughtcrime.securesms.groups

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.sending_receiving.pollers.OpenGroupPollerManager
import org.session.libsession.utilities.ConfigFactoryProtocol
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manage common operations for open groups, such as adding, deleting, and updating them.
 */
@Singleton
class OpenGroupManager @Inject constructor(
    private val configFactory: ConfigFactoryProtocol,
    private val pollerManager: OpenGroupPollerManager,
) {
    suspend fun add(server: String, room: String, publicKey: String) {
        configFactory.withMutableUserConfigs { configs ->
            val community = configs.userGroups.getOrConstructCommunityInfo(
                baseUrl = server,
                room = room,
                pubKeyHex = publicKey,
            )

            configs.userGroups.set(community)
        }

        // Wait until we have a poller for the server
        val poller = pollerManager.pollers
            .mapNotNull { it[server] }
            .first()

        // Request a poll anyway and wait for it to complete
        poller.poller.requestPollOnceAndWait()

        // Wait until we have this roomed polled
        poller.poller.lastPolledRooms.first { room in it }
    }

    fun delete(server: String, room: String) {
        configFactory.withMutableUserConfigs { configs ->
            configs.userGroups.eraseCommunity(server, room)
            configs.convoInfoVolatile.eraseCommunity(server, room)
        }
    }

    suspend fun addOpenGroup(urlAsString: String) {
        val url = urlAsString.toHttpUrlOrNull() ?: return
        val server = OpenGroup.getServer(urlAsString)
        val room = url.pathSegments.firstOrNull() ?: return
        val publicKey = url.queryParameter("public_key") ?: return

        add(server.toString().removeSuffix("/"), room, publicKey) // assume migrated from calling function
    }
}