package org.thoughtcrime.securesms.notifications

import org.session.libsignal.utilities.Log

class NoOpPushManager: PushManager {

    override fun refresh(force: Boolean) {
        Log.d("NoOpPushManager", "Push notifications not supported, not registering for push notifications")
    }
}
