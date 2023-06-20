package org.session.libsession.messaging.sending_receiving.notifications

import android.annotation.SuppressLint
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.all
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
import org.session.libsignal.utilities.emptyPromise
import org.session.libsignal.utilities.retryIfNeeded

@SuppressLint("StaticFieldLeak")
object LegacyGroupsPushManager {
    private const val TAG = "LegacyGroupsPushManager"

    val context = MessagingModuleConfiguration.shared.context
    const val server = "https://push.getsession.org"
    const val serverPublicKey: String = "d7557fe563e2610de876c0ac7341b62f3c82d5eea4b62c702392ea4368f51b3b"
    private const val legacyServer = "https://dev.apns.getsession.org"
    private const val legacyServerPublicKey = "642a6585919742e5a2d4dc51244964fbcd8bcab2b75612407de58b810740d049"
    private const val maxRetryCount = 4

    fun register(
        token: String? = TextSecurePreferences.getFCMToken(context),
        publicKey: String? = TextSecurePreferences.getLocalNumber(context),
        legacyGroupPublicKeys: Collection<String> = MessagingModuleConfiguration.shared.storage.getAllClosedGroupPublicKeys()
    ): Promise<Unit, Exception> =
        retryIfNeeded(maxRetryCount) {
            Log.d(TAG, "register() called")

            val parameters = mapOf(
                "token" to token!!,
                "pubKey" to publicKey!!,
                "legacyGroupPublicKeys" to legacyGroupPublicKeys.takeIf { it.isNotEmpty() }!!
            )
            val url = "$legacyServer/register_legacy_groups_only"
            val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
            val request = Request.Builder().url(url).post(body).build()

            OnionRequestAPI.sendOnionRequest(request, legacyServer, legacyServerPublicKey, Version.V2).map { response ->
                when (response.info["code"]) {
                    null, 0 -> throw Exception("error: ${response.info["message"]}.")
                    else -> Log.d(TAG, "register() success!!")
                }
            }
        } fail { exception ->
            Log.d(TAG, "Couldn't register for FCM due to error: ${exception}.")
        }

    /**
     * Unregister push notifications for 1-1 conversations as this is now done in FirebasePushManager.
     */
    fun unregister(): Promise<*, Exception> {
        Log.d(TAG, "unregister() called")

        val token = TextSecurePreferences.getFCMToken(context) ?: emptyPromise()

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
                android.util.Log.d(TAG, "unregister() success!!")

                when (it.info["code"]) {
                    null, 0 -> throw Exception("error: ${it.info["message"]}.")
                    else -> Log.d(TAG, "unregisterV1 success token: $token")
                }
            } fail {
                    Log.d(TAG, "Couldn't disable FCM with token: $token due to error: $it.")
            }
        }
    }

    // Legacy Closed Groups

    fun subscribeGroup(
        closedGroupPublicKey: String,
        publicKey: String = MessagingModuleConfiguration.shared.storage.getUserPublicKey()!!
    ) = performGroupOperation("subscribe_closed_group", closedGroupPublicKey, publicKey)

    fun unsubscribeGroup(
        closedGroupPublicKey: String,
        publicKey: String = MessagingModuleConfiguration.shared.storage.getUserPublicKey()!!
    ) = performGroupOperation("unsubscribe_closed_group", closedGroupPublicKey, publicKey)

    private fun performGroupOperation(
        operation: String,
        closedGroupPublicKey: String,
        publicKey: String
    ): Promise<*, Exception> {
        val parameters = mapOf( "closedGroupPublicKey" to closedGroupPublicKey, "pubKey" to publicKey )
        val url = "$legacyServer/$operation"
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
                    else -> Log.d(TAG, "performGroupOperation success: $operation")
                }
            } fail { exception ->
                Log.d(TAG, "performGroupOperation fail: $operation: $closedGroupPublicKey due to error: ${exception}.")
            }
        }
    }
}
