package org.session.libsession.messaging.sending_receiving.notifications

import android.annotation.SuppressLint
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.all
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.task
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.Version
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.emptyPromise
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

    fun register(
        token: String? = TextSecurePreferences.getLegacyFCMToken(context)
    ): Promise<*, Exception> = all(
        register(token, TextSecurePreferences.getLocalNumber(context)),
        subscribeGroups()
    )

    fun register(token: String?, publicKey: String?): Promise<Unit, Exception> =
        retryIfNeeded(maxRetryCount) {
            val parameters = mapOf("token" to token!!, "pubKey" to publicKey!!)
            val url = "$legacyServer/register"
            val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
            val request = Request.Builder().url(url).post(body).build()

            OnionRequestAPI.sendOnionRequest(request, legacyServer, legacyServerPublicKey, Version.V2).map { response ->
                when (response.info["code"]) {
                    null, 0 -> throw Exception("error: ${response.info["message"]}.")
                    else -> TextSecurePreferences.setLegacyFCMToken(context, token)
                }
            } fail { exception ->
                Log.d(TAG, "Couldn't register for FCM due to error: ${exception}.")
            }
        }

    /**
     * Unregister push notifications for 1-1 conversations as this is now done in FirebasePushManager.
     */
    fun unregister(): Promise<*, Exception> {
        val token = TextSecurePreferences.getLegacyFCMToken(context) ?: emptyPromise()

        return retryIfNeeded(maxRetryCount) {
            val parameters = mapOf( "token" to token )
            val url = "$legacyServer/unregister"
            val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
            val request = Request.Builder().url(url).post(body).build()

            OnionRequestAPI.sendOnionRequest(
                request,
                legacyServer,
                legacyServerPublicKey,
                Version.V2
            ) success {
                when (it.info["code"]) {
                    null, 0 -> throw Exception("error: ${it.info["message"]}.")
                    else -> Log.d(TAG, "unregisterV1 success token: $token")
                }
                TextSecurePreferences.clearLegacyFCMToken(context)
            } fail {
                    exception -> Log.d(TAG, "Couldn't disable FCM with token: $token due to error: ${exception}.")
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
    ) = closedGroupPublicKeys.map { performGroupOperation(ClosedGroupOperation.Subscribe, it, publicKey) }.let(::all)

    fun unsubscribeGroup(
        closedGroupPublicKey: String,
        publicKey: String = MessagingModuleConfiguration.shared.storage.getUserPublicKey()!!
    ) = performGroupOperation(ClosedGroupOperation.Unsubscribe, closedGroupPublicKey, publicKey)

    private fun unsubscribeGroups(
        closedGroupPublicKeys: Collection<String> = MessagingModuleConfiguration.shared.storage.getAllClosedGroupPublicKeys(),
        publicKey: String = MessagingModuleConfiguration.shared.storage.getUserPublicKey()!!
    ) = closedGroupPublicKeys.map { performGroupOperation(ClosedGroupOperation.Unsubscribe, it, publicKey) }.let(::all)

    private fun performGroupOperation(
        operation: ClosedGroupOperation,
        closedGroupPublicKey: String,
        publicKey: String
    ): Promise<*, Exception> {
        val parameters = mapOf( "closedGroupPublicKey" to closedGroupPublicKey, "pubKey" to publicKey )
        val url = "$legacyServer/${operation.rawValue}"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body).build()

        return retryIfNeeded(maxRetryCount) {
            OnionRequestAPI.sendOnionRequest(
                request,
                legacyServer,
                legacyServerPublicKey,
                Version.V2
            ) success {
                when (it.info["code"]) {
                    null, 0 -> throw Exception("${it.info["message"]}")
                    else -> Log.d(TAG, "performGroupOperation success: ${operation.rawValue}")
                }
            } fail { exception ->
                Log.d(TAG, "performGroupOperation fail: ${operation.rawValue}: $closedGroupPublicKey due to error: ${exception}.")
            }
        }
    }
}
