package org.session.libsession.messaging.sending_receiving.pollers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import network.loki.messenger.libsession_util.GroupInfoConfig
import network.loki.messenger.libsession_util.GroupKeysConfig
import network.loki.messenger.libsession_util.GroupMembersConfig
import network.loki.messenger.libsession_util.util.GroupInfo
import org.session.libsession.snode.RawResponse
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.SessionId

class ClosedGroupPoller(private val executor: CoroutineScope,
                        private val closedGroupSessionId: SessionId,
                        private val configFactoryProtocol: ConfigFactoryProtocol) {

    companion object {
        const val POLL_INTERVAL = 3_000L
    }

    private var isRunning: Boolean = false
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = executor.launch {
            val closedGroups = configFactoryProtocol.userGroups?: return@launch
            while (true) {
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
        job?.cancel()
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

            (pollResult["body"] as List<RawResponse>).forEachIndexed { index, response ->
                when (index) {
                    keysIndex -> handleKeyPoll(response, keys, info, members)
                    infoIndex -> handleInfo(response, info)
                    membersIndex -> handleMembers(response, members)
                    messageIndex -> handleMessages(response)
                }
            }

        } catch (e: Exception) {
            Log.e("GroupPoller", "Polling failed for group", e)
            return POLL_INTERVAL
        }
        return POLL_INTERVAL // this might change in future
    }

    private fun handleKeyPoll(response: RawResponse,
                              keysConfig: GroupKeysConfig,
                              infoConfig: GroupInfoConfig,
                              membersConfig: GroupMembersConfig) {

    }

    private fun handleInfo(response: RawResponse,
                           infoConfig: GroupInfoConfig) {

    }

    private fun handleMembers(response: RawResponse,
                              membersConfig: GroupMembersConfig) {

    }

    private fun handleMessages(response: RawResponse) {
        // TODO
    }



}