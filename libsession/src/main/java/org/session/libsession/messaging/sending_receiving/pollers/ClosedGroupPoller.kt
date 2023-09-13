package org.session.libsession.messaging.sending_receiving.pollers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import network.loki.messenger.libsession_util.GroupInfoConfig
import network.loki.messenger.libsession_util.GroupKeysConfig
import network.loki.messenger.libsession_util.GroupMembersConfig
import network.loki.messenger.libsession_util.util.GroupInfo
import org.session.libsession.snode.RawResponse
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.SessionId

class ClosedGroupPoller(private val executor: CoroutineScope,
                        private val closedGroupSessionId: SessionId,
                        private val configFactoryProtocol: ConfigFactoryProtocol) {

    data class ParsedRawMessage(
        val data: ByteArray,
        val hash: String,
        val timestamp: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ParsedRawMessage

            if (!data.contentEquals(other.data)) return false
            if (hash != other.hash) return false
            if (timestamp != other.timestamp) return false

            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + hash.hashCode()
            result = 31 * result + timestamp.hashCode()
            return result
        }
    }

    companion object {
        const val POLL_INTERVAL = 3_000L
    }

    private var isRunning: Boolean = false
    private var job: Job? = null

    fun start() {
        if (isRunning) return // already started, don't restart

        Log.d("ClosedGroupPoller", "Starting closed group poller for ${closedGroupSessionId.hexString().take(4)}")
        job?.cancel()
        job = executor.launch(Dispatchers.IO) {
            val closedGroups = configFactoryProtocol.userGroups?: return@launch
            isRunning = true
            while (isActive && isRunning) {
                val group = closedGroups.getClosedGroup(closedGroupSessionId.hexString()) ?: break
                val nextPoll = poll(group)
                if (nextPoll != null) {
                    delay(nextPoll)
                } else {
                    Log.d("ClosedGroupPoller", "Stopping the closed group poller")
                    return@launch
                }
            }
            // assume null poll time means don't continue polling, either the group has been deleted or something else
        }
    }

    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
    }

    fun poll(group: GroupInfo.ClosedGroupInfo): Long? {
        try {
            val snode = SnodeAPI.getSingleTargetSnode(closedGroupSessionId.hexString()).get()
            val info = configFactoryProtocol.getGroupInfoConfig(closedGroupSessionId) ?: return null
            val members = configFactoryProtocol.getGroupMemberConfig(closedGroupSessionId) ?: return null
            val keys = configFactoryProtocol.getGroupKeysConfig(closedGroupSessionId) ?: return null

            val keysIndex = 0
            val infoIndex = 1
            val membersIndex = 2
            val messageIndex = 3

            val messagePoll = SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                snode,
                closedGroupSessionId.hexString(),
                Namespace.DEFAULT,
                maxSize = null,
                group.signingKey()
            ) ?: return null
            val infoPoll = SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                snode,
                closedGroupSessionId.hexString(),
                info.configNamespace(),
                maxSize = null,
                group.signingKey()
            ) ?: return null
            val membersPoll = SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                snode,
                closedGroupSessionId.hexString(),
                members.configNamespace(),
                maxSize = null,
                group.signingKey()
            ) ?: return null
            val keysPoll = SnodeAPI.buildAuthenticatedRetrieveBatchRequest(
                snode,
                closedGroupSessionId.hexString(),
                GroupKeysConfig.storageNamespace(),
                maxSize = null,
                group.signingKey()
            ) ?: return null

            val pollResult = SnodeAPI.getRawBatchResponse(
                snode,
                closedGroupSessionId.hexString(),
                listOf(keysPoll, infoPoll, membersPoll, messagePoll)
            ).get()

            // TODO: add the extend duration TTLs for known hashes here

            // if poll result body is null here we don't have any things ig
            Log.d("ClosedGroupPoller", "Poll results @${SnodeAPI.nowWithOffset}:")
            (pollResult["results"] as List<RawResponse>).forEachIndexed { index, response ->
                when (index) {
                    keysIndex -> handleKeyPoll(response, keys, info, members)
                    infoIndex -> handleInfo(response, info)
                    membersIndex -> handleMembers(response, members)
                    messageIndex -> handleMessages(response, keys)
                }
            }

            configFactoryProtocol.saveGroupConfigs(keys, info, members)
            keys.free()
            info.free()
            members.free()

        } catch (e: Exception) {
            Log.e("GroupPoller", "Polling failed for group", e)
            return POLL_INTERVAL
        }
        return POLL_INTERVAL // this might change in future
    }

    private fun parseMessages(response: RawResponse): List<ParsedRawMessage> {
        val body = response["body"] as? RawResponse
        if (body == null) {
            Log.e("GroupPoller", "Batch parse messages contained no body!")
            return emptyList()
        }
        val messages = body["messages"] as? List<*> ?: return emptyList()
        return messages.mapNotNull { messageMap ->
            val rawMessageAsJSON = messageMap as? Map<*, *> ?: return@mapNotNull null
            val base64EncodedData = rawMessageAsJSON["data"] as? String ?: return@mapNotNull null
            val hash = rawMessageAsJSON["hash"] as? String ?: return@mapNotNull null
            val timestamp = rawMessageAsJSON["timestamp"] as? Long ?: return@mapNotNull null
            val data = base64EncodedData.let { Base64.decode(it) }
            ParsedRawMessage(data, hash, timestamp)
        }
    }

    private fun handleKeyPoll(response: RawResponse,
                              keysConfig: GroupKeysConfig,
                              infoConfig: GroupInfoConfig,
                              membersConfig: GroupMembersConfig) {
        // get all the data to hash objects and process them
        parseMessages(response).forEach { (message, hash, timestamp) ->
            keysConfig.loadKey(message, hash, timestamp, infoConfig, membersConfig)
            Log.d("ClosedGroupPoller", "Merged $hash for keys on ${closedGroupSessionId.hexString()}")
        }
    }

    private fun handleInfo(response: RawResponse,
                           infoConfig: GroupInfoConfig) {
        parseMessages(response).forEach { (message, hash, _) ->
            infoConfig.merge(hash to message)
            Log.d("ClosedGroupPoller", "Merged $hash for info on ${closedGroupSessionId.hexString()}")
        }
    }

    private fun handleMembers(response: RawResponse,
                              membersConfig: GroupMembersConfig) {
        parseMessages(response).forEach { (message, hash, _) ->
            membersConfig.merge(hash to message)
            Log.d("ClosedGroupPoller", "Merged $hash for members on ${closedGroupSessionId.hexString()}")
        }
    }

    private fun handleMessages(response: RawResponse, keysConfig: GroupKeysConfig) {
        val messages = parseMessages(response)
        if (messages.isNotEmpty()) {
            // TODO: process decrypting bundles
        }
    }

}