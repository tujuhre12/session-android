package org.session.libsession.messaging.sending_receiving.notifications

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionRequest(
    /** the 33-byte account being subscribed to; typically a session ID */
    val pubkey: String,
    /** when the pubkey starts with 05 (i.e. a session ID) this is the ed25519 32-byte pubkey associated with the session ID */
    val session_ed25519: String?,
    /** 32-byte swarm authentication subkey; omitted (or null) when not using subkey auth (new closed groups) */
    val subkey_tag: String?,
    /** array of integer namespaces to subscribe to, **must be sorted in ascending order** */
    val namespaces: List<Int>,
    /** if provided and true then notifications will include the body of the message (as long as it isn't too large) */
    val data: Boolean,
    /** the signature unix timestamp in seconds, not ms */
    val sig_ts: Long,
    /** the 64-byte ed25519 signature */
    val signature: String,
    /** the string identifying the notification service, "firebase" for android (currently) */
    val service: String,
    /** dict of service-specific data, currently just "token" field with device-specific token but different services might have other requirements */
    val service_info: Map<String, String>,
    /** 32-byte encryption key; notification payloads sent to the device will be encrypted with XChaCha20-Poly1305 via libsodium using this key.
     * persist it on device */
    val enc_key: String
)

@Serializable
data class SubscriptionResponse(
    val error: Int?,
    val message: String?,
    val success: Boolean?,
    val added: Boolean?,
    val updated: Boolean?,
) {
    companion object {
        /** invalid values, missing reuqired arguments etc, details in message */
        const val UNPARSEABLE_ERROR = 1
        /** the "service" value is not active / valid */
        const val SERVICE_NOT_AVAILABLE = 2
        /** something getting wrong internally talking to the backend */
        const val SERVICE_TIMEOUT = 3
        /** other error processing the subscription (details in the message) */
        const val GENERIC_ERROR = 4
    }
    fun isSuccess() = success == true && error == null
    fun errorInfo() = if (success == false && error != null) {
        true to message
    } else false to null
}