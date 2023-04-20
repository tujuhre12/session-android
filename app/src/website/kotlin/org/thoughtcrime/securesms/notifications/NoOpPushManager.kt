package org.thoughtcrime.securesms.notifications

import org.session.libsignal.utilities.Log

class NoOpPushManager: PushManager {

    override fun register(force: Boolean) {
        Log.d("NoOpPushManager", "Push notifications not supported, not registering for push notifications")
    }

    override fun unregister(token: String) {
        Log.d("NoOpPushManager", "Push notifications not supported, not unregistering for push notifications")
    }
}