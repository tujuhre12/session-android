package org.session.libsession.messaging.sending_receiving.pollers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsignal.utilities.Log
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
            val nextPoll = poll()
            delay(nextPoll)
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun poll(): Long {
        try {
            val snode = SnodeAPI.getSingleTargetSnode(closedGroupSessionId.hexString()).get()
            val info = configFactoryProtocol.getOrConstructGroupInfoConfig(closedGroupSessionId)
            val members = configFactoryProtocol.getOrConstructGroupMemberConfig(closedGroupSessionId)
            val keys = configFactoryProtocol.getGroupKeysConfig(closedGroupSessionId)

        } catch (e: Exception) {
            Log.e("GroupPoller", "Polling failed for group", e)
        }
        return POLL_INTERVAL // this might change in future
    }

}