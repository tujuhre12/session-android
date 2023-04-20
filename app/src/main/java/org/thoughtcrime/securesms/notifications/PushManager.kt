package org.thoughtcrime.securesms.notifications

interface PushManager {
    fun register(force: Boolean)
    fun unregister(token: String)
}