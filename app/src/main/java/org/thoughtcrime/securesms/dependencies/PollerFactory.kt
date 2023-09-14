package org.thoughtcrime.securesms.dependencies

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import org.session.libsession.messaging.sending_receiving.pollers.ClosedGroupPoller
import org.session.libsignal.utilities.SessionId
import java.util.concurrent.ConcurrentHashMap

class PollerFactory(private val scope: CoroutineScope,
                    private val configFactory: ConfigFactory) {

    private val pollers = ConcurrentHashMap<SessionId, ClosedGroupPoller>()

    fun pollerFor(sessionId: SessionId): ClosedGroupPoller? {
        val activeGroup = configFactory.userGroups?.getClosedGroup(sessionId.hexString()) ?: return null
        // TODO: add check for active group being invited / approved etc
        return pollers.getOrPut(sessionId) {
            ClosedGroupPoller(scope + SupervisorJob(), sessionId, configFactory)
        }
    }

    fun startAll() {
        configFactory.userGroups?.allClosedGroupInfo()?.forEach {
            pollerFor(it.groupSessionId)?.start()
        }
    }

    fun stopAll() {
        pollers.forEach { (_, poller) ->
            poller.stop()
        }
    }

    fun updatePollers() {
        val currentGroups = configFactory.userGroups?.allClosedGroupInfo() ?: return
        val toRemove = pollers.filter { (id, _) -> id !in currentGroups.map { it.groupSessionId } }
        toRemove.forEach { (id, _) ->
            pollers.remove(id)?.stop()
        }
        startAll()
    }

}