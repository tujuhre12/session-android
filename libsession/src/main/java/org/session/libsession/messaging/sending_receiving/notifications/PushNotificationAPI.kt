package org.session.libsession.messaging.sending_receiving.notifications

import android.annotation.SuppressLint
import nl.komponents.kovenant.functional.map
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.Version
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.retryIfNeeded

@SuppressLint("StaticFieldLeak")
object PushNotificationAPI {
    private const val TAG = "PushNotificationAPI"

    val context = MessagingModuleConfiguration.shared.context
    const val server = "https://push.getsession.org"
    const val serverPublicKey: String = "d7557fe563e2610de876c0ac7341b62f3c82d5eea4b62c702392ea4368f51b3b"
    private const val legacyServer = "https://live.apns.getsession.org"
    private const val legacyServerPublicKey = "642a6585919742e5a2d4dc51244964fbcd8bcab2b75612407de58b810740d049"
    private const val maxRetryCount = 4

    private enum class ClosedGroupOperation(val rawValue: String) {
        Subscribe("subscribe_closed_group"),
        Unsubscribe("unsubscribe_closed_group");
    }

    fun register(token: String? = TextSecurePreferences.getFCMToken(context)) {
        Log.d(TAG, "register: $token")

        token?.let(::unregisterV1)
        subscribeGroups()
    }

    @JvmStatic
    fun unregister(token: String) {
        Log.d(TAG, "unregister: $token")

        unregisterV1(token)
        unsubscribeGroups()
    }

    private fun unregisterV1(token: String) {
        val parameters = mapOf( "token" to token )
        val url = "$legacyServer/unregister"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body).build()
        retryIfNeeded(maxRetryCount) {
            OnionRequestAPI.sendOnionRequest(request, legacyServer, legacyServerPublicKey, Version.V2).map { response ->
                when (response.info["code"]) {
                    null, 0 -> Log.d(TAG, "Couldn't disable FCM due to error: ${response.info["message"]}.")
                    else -> Log.d(TAG, "unregisterV1 success token: $token")
                }
            }.fail { exception ->
                Log.d(TAG, "Couldn't disable FCM due to error: ${exception}.")
            }
        }
    }

    // Legacy Closed Groups

    fun subscribeGroup(
        closedGroupPublicKey: String,
        publicKey: String = MessagingModuleConfiguration.shared.storage.getUserPublicKey()!!
    ) = performGroupOperation(ClosedGroupOperation.Subscribe, closedGroupPublicKey, publicKey)

    private fun subscribeGroups(
        closedGroupPublicKeys: Collection<String> = MessagingModuleConfiguration.shared.storage.getAllClosedGroupPublicKeys(),
        publicKey: String = MessagingModuleConfiguration.shared.storage.getUserPublicKey()!!
    ) = closedGroupPublicKeys.forEach { performGroupOperation(ClosedGroupOperation.Subscribe, it, publicKey) }

    fun unsubscribeGroup(
        closedGroupPublicKey: String,
        publicKey: String = MessagingModuleConfiguration.shared.storage.getUserPublicKey()!!
    ) = performGroupOperation(ClosedGroupOperation.Unsubscribe, closedGroupPublicKey, publicKey)

    private fun unsubscribeGroups(
        closedGroupPublicKeys: Collection<String> = MessagingModuleConfiguration.shared.storage.getAllClosedGroupPublicKeys(),
        publicKey: String = MessagingModuleConfiguration.shared.storage.getUserPublicKey()!!
    ) = closedGroupPublicKeys.forEach { performGroupOperation(ClosedGroupOperation.Unsubscribe, it, publicKey) }

    private fun performGroupOperation(
        operation: ClosedGroupOperation,
        closedGroupPublicKey: String,
        publicKey: String
    ) {
        if (!TextSecurePreferences.isUsingFCM(context)) return

        val parameters = mapOf( "closedGroupPublicKey" to closedGroupPublicKey, "pubKey" to publicKey )
        val url = "$legacyServer/${operation.rawValue}"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body).build()

        retryIfNeeded(maxRetryCount) {
            OnionRequestAPI.sendOnionRequest(request, legacyServer, legacyServerPublicKey, Version.V2).map { response ->
                when (response.info["code"]) {
                    null, 0 -> Log.d(TAG, "performGroupOperation fail: ${operation.rawValue}: $closedGroupPublicKey due to error: ${response.info["message"]}.")
                    else -> Log.d(TAG, "performGroupOperation success: ${operation.rawValue}")
                }
            }.fail { exception ->
                Log.d(TAG, "Couldn't ${operation.rawValue}: $closedGroupPublicKey due to error: ${exception}.")
            }
        }
    }
}
