@file:Suppress("NAME_SHADOWING")

package org.session.libsession.snode

import android.os.Build
import com.goterl.lazysodium.exceptions.SodiumException
import com.goterl.lazysodium.interfaces.GenericHash
import com.goterl.lazysodium.interfaces.PwHash
import com.goterl.lazysodium.interfaces.SecretBox
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.all
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.task
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.utilities.MessageWrapper
import org.session.libsession.messaging.utilities.SodiumUtilities.sodium
import org.session.libsession.utilities.toByteArray
import org.session.libsignal.crypto.getRandomElement
import org.session.libsignal.database.LokiAPIDatabaseProtocol
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Broadcaster
import org.session.libsignal.utilities.HTTP
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.Namespace
import org.session.libsignal.utilities.Snode
import org.session.libsignal.utilities.prettifiedDescription
import org.session.libsignal.utilities.retryIfNeeded
import java.security.SecureRandom
import java.util.Locale
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.properties.Delegates.observable

object SnodeAPI {
    internal val database: LokiAPIDatabaseProtocol
        get() = SnodeModule.shared.storage
    private val broadcaster: Broadcaster
        get() = SnodeModule.shared.broadcaster

    private var snodeFailureCount: MutableMap<Snode, Int> = mutableMapOf()
    internal var snodePool: Set<Snode>
        get() = database.getSnodePool()
        set(newValue) { database.setSnodePool(newValue) }
    /**
     * The offset between the user's clock and the Service Node's clock. Used in cases where the
     * user's clock is incorrect.
     */
    internal var clockOffset = 0L

    @JvmStatic
    val nowWithOffset
        get() = System.currentTimeMillis() + clockOffset

    internal var forkInfo by observable(database.getForkInfo()) { _, oldValue, newValue ->
        if (newValue > oldValue) {
            Log.d("Loki", "Setting new fork info new: $newValue, old: $oldValue")
            database.setForkInfo(newValue)
        }
    }

    // Settings
    private const val maxRetryCount = 6
    private const val minimumSnodePoolCount = 12
    private const val minimumSwarmSnodeCount = 3
    // Use port 4433 if the API level can handle the network security configuration and enforce pinned certificates
    private val seedNodePort = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) 443 else 4443

    private const val snodeFailureThreshold = 3
    private const val useOnionRequests = true

    private const val useTestnet = false

    private val seedNodePool = if (useTestnet) {
        setOf( "http://public.loki.foundation:38157" )
    } else {
        setOf(
            "https://seed1.getsession.org:$seedNodePort",
            "https://seed2.getsession.org:$seedNodePort",
            "https://seed3.getsession.org:$seedNodePort",
        )
    }

    private const val KEY_IP = "public_ip"
    private const val KEY_PORT = "storage_port"
    private const val KEY_X25519 = "pubkey_x25519"
    private const val KEY_ED25519 = "pubkey_ed25519"
    private const val KEY_VERSION = "storage_server_version"

    // Error
    sealed class Error(val description: String) : Exception(description) {
        object Generic : Error("An error occurred.")
        object ClockOutOfSync : Error("Your clock is out of sync with the Service Node network.")
        object NoKeyPair : Error("Missing user key pair.")
        object SigningFailed : Error("Couldn't sign verification data.")
        // ONS
        object DecryptionFailed : Error("Couldn't decrypt ONS name.")
        object HashingFailed : Error("Couldn't compute ONS name hash.")
        object ValidationFailed : Error("ONS name validation failed.")
    }

    // Batch
    data class SnodeBatchRequestInfo(
        val method: String,
        val params: Map<String, Any>,
        @Transient
        val namespace: Int?
    ) // assume signatures, pubkey and namespaces are attached in parameters if required

    // Internal API
    internal fun invoke(
        method: Snode.Method,
        snode: Snode,
        parameters: Map<String, Any>,
        publicKey: String? = null,
        version: Version = Version.V3
    ): RawResponsePromise = if (useOnionRequests) OnionRequestAPI.sendOnionRequest(method, parameters, snode, version, publicKey).map {
            val body = it.body ?: throw Error.Generic
            JsonUtil.fromJson(body, Map::class.java)
        } else task {
            val payload = mapOf( "method" to method.rawValue, "params" to parameters )
            try {
                val url = "${snode.address}:${snode.port}/storage_rpc/v1"
                val response = HTTP.execute(HTTP.Verb.POST, url, payload).toString()
                JsonUtil.fromJson(response, Map::class.java)
            } catch (exception: Exception) {
                (exception as? HTTP.HTTPRequestFailedException)?.run {
                    handleSnodeError(statusCode, json, snode, publicKey)
                    // TODO Check if we meant to throw the error returned by handleSnodeError
                    throw exception
                }
                Log.d("Loki", "Unhandled exception: $exception.")
                throw exception
            }
        }

    internal fun getRandomSnode(): Promise<Snode, Exception> =
        snodePool.takeIf { it.size >= minimumSnodePoolCount }?.let { Promise.of(it.getRandomElement()) } ?: task {
            val target = seedNodePool.random()
            val url = "$target/json_rpc"
            Log.d("Loki", "Populating snode pool using: $target.")
            val parameters = mapOf(
                "method" to "get_n_service_nodes",
                "params" to mapOf(
                    "active_only" to true,
                    "fields" to mapOf(
                        KEY_IP to true, KEY_PORT to true,
                        KEY_X25519 to true, KEY_ED25519 to true,
                        KEY_VERSION to true
                    )
                )
            )
            val response = HTTP.execute(HTTP.Verb.POST, url, parameters, useSeedNodeConnection = true)
            val json = try {
                JsonUtil.fromJson(response, Map::class.java)
            } catch (exception: Exception) {
                mapOf( "result" to response.toString())
            }
            val intermediate = json["result"] as? Map<*, *> ?: throw Error.Generic
                .also { Log.d("Loki", "Failed to update snode pool, intermediate was null.") }
            val rawSnodes = intermediate["service_node_states"] as? List<*> ?: throw Error.Generic
                .also { Log.d("Loki", "Failed to update snode pool, rawSnodes was null.") }

            rawSnodes.asSequence().mapNotNull { it as? Map<*, *> }.mapNotNull { rawSnode ->
                createSnode(
                    address = rawSnode[KEY_IP] as? String,
                    port = rawSnode[KEY_PORT] as? Int,
                    ed25519Key = rawSnode[KEY_ED25519] as? String,
                    x25519Key = rawSnode[KEY_X25519] as? String,
                    version = (rawSnode[KEY_VERSION] as? List<*>)
                        ?.filterIsInstance<Int>()
                        ?.let(Snode::Version)
                ).also { if (it == null) Log.d("Loki", "Failed to parse: ${rawSnode.prettifiedDescription()}.") }
            }.toSet().also {
                Log.d("Loki", "Persisting snode pool to database.")
                this.snodePool = it
            }.runCatching { getRandomElement() }.onFailure {
                Log.d("Loki", "Got an empty snode pool from: $target.")
                throw SnodeAPI.Error.Generic
            }.getOrThrow()
        }

    private fun createSnode(address: String?, port: Int?, ed25519Key: String?, x25519Key: String?, version: Snode.Version? = Snode.Version.ZERO): Snode? {
        return Snode(
            address?.takeUnless { it == "0.0.0.0" }?.let { "https://$it" } ?: return null,
            port ?: return null,
            Snode.KeySet(ed25519Key ?: return null, x25519Key ?: return null),
            version ?: return null
        )
    }

    internal fun dropSnodeFromSwarmIfNeeded(snode: Snode, publicKey: String) {
        database.getSwarm(publicKey)?.takeIf { snode in it }?.let {
            database.setSwarm(publicKey, it - snode)
        }
    }

    internal fun getSingleTargetSnode(publicKey: String): Promise<Snode, Exception> {
        // SecureRandom() should be cryptographically secure
        return getSwarm(publicKey).map { it.shuffled(SecureRandom()).random() }
    }

    // Public API
    fun getAccountID(onsName: String): Promise<String, Exception> {
        val validationCount = 3
        val accountIDByteCount = 33
        // Hash the ONS name using BLAKE2b
        val onsName = onsName.toLowerCase(Locale.US)
        val nameAsData = onsName.toByteArray()
        val nameHash = ByteArray(GenericHash.BYTES)
        if (!sodium.cryptoGenericHash(nameHash, nameHash.size, nameAsData, nameAsData.size.toLong())) {
            throw Error.HashingFailed
        }
        val base64EncodedNameHash = Base64.encodeBytes(nameHash)
        // Ask 3 different snodes for the Account ID associated with the given name hash
        val parameters = mapOf(
            "endpoint" to "ons_resolve",
            "params" to mapOf( "type" to 0, "name_hash" to base64EncodedNameHash )
        )
        val promises = List(validationCount) {
            getRandomSnode().bind { snode ->
                retryIfNeeded(maxRetryCount) {
                    invoke(Snode.Method.OxenDaemonRPCCall, snode, parameters)
                }
            }
        }
        return all(promises).map { results ->
            val accountIDs = mutableListOf<String>()
            for (json in results) {
                val intermediate = json["result"] as? Map<*, *>
                val hexEncodedCiphertext = intermediate?.get("encrypted_value") as? String ?: throw Error.Generic
                val ciphertext = Hex.fromStringCondensed(hexEncodedCiphertext)
                val isArgon2Based = (intermediate["nonce"] == null)
                if (isArgon2Based) {
                    // Handle old Argon2-based encryption used before HF16
                    val salt = ByteArray(PwHash.SALTBYTES)
                    val nonce = ByteArray(SecretBox.NONCEBYTES)
                    val accountIDAsData = ByteArray(accountIDByteCount)
                    val key = try {
                        Key.fromHexString(sodium.cryptoPwHash(onsName, SecretBox.KEYBYTES, salt, PwHash.OPSLIMIT_MODERATE, PwHash.MEMLIMIT_MODERATE, PwHash.Alg.PWHASH_ALG_ARGON2ID13)).asBytes
                    } catch (e: SodiumException) {
                        throw Error.HashingFailed
                    }
                    if (!sodium.cryptoSecretBoxOpenEasy(accountIDAsData, ciphertext, ciphertext.size.toLong(), nonce, key)) {
                        throw Error.DecryptionFailed
                    }
                    accountIDs.add(Hex.toStringCondensed(accountIDAsData))
                } else {
                    val hexEncodedNonce = intermediate["nonce"] as? String ?: throw Error.Generic
                    val nonce = Hex.fromStringCondensed(hexEncodedNonce)
                    val key = ByteArray(GenericHash.BYTES)
                    if (!sodium.cryptoGenericHash(key, key.size, nameAsData, nameAsData.size.toLong(), nameHash, nameHash.size)) {
                        throw Error.HashingFailed
                    }
                    val accountIDAsData = ByteArray(accountIDByteCount)
                    if (!sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(accountIDAsData, null, null, ciphertext, ciphertext.size.toLong(), null, 0, nonce, key)) {
                        throw Error.DecryptionFailed
                    }
                    accountIDs.add(Hex.toStringCondensed(accountIDAsData))
                }
            }
            accountIDs.takeIf { it.size == validationCount && it.toSet().size == 1 }?.first()
                ?: throw Error.ValidationFailed
        }
    }

    fun getSwarm(publicKey: String): Promise<Set<Snode>, Exception> =
        database.getSwarm(publicKey)?.takeIf { it.size >= minimumSwarmSnodeCount }?.let(Promise.Companion::of)
            ?: getRandomSnode().bind {
                invoke(Snode.Method.GetSwarm, it, parameters = mapOf( "pubKey" to publicKey ), publicKey)
            }.map {
                parseSnodes(it).toSet()
            }.success {
                database.setSwarm(publicKey, it)
            }

    private fun signAndEncode(data: ByteArray, userED25519KeyPair: KeyPair) = sign(data, userED25519KeyPair).let(Base64::encodeBytes)
    private fun sign(data: ByteArray, userED25519KeyPair: KeyPair): ByteArray = ByteArray(Sign.BYTES).also {
        sodium.cryptoSignDetached(
            it,
            data,
            data.size.toLong(),
            userED25519KeyPair.secretKey.asBytes
        )
    }

    fun getRawMessages(snode: Snode, publicKey: String, requiresAuth: Boolean = true, namespace: Int = 0): RawResponsePromise {
        // Get last message hash
        val lastHashValue = database.getLastMessageHashValue(snode, publicKey, namespace) ?: ""
        val parameters = mutableMapOf<String, Any>(
            "pubKey" to publicKey,
            "last_hash" to lastHashValue,
        )
        // Construct signature
        if (requiresAuth) {
            val userED25519KeyPair = try {
                MessagingModuleConfiguration.shared.getUserED25519KeyPair()
                    ?: return Promise.ofFail(Error.NoKeyPair)
            } catch (e: Exception) {
                Log.e("Loki", "Error getting KeyPair", e)
                return Promise.ofFail(Error.NoKeyPair)
            }
            val timestamp = System.currentTimeMillis() + clockOffset
            val ed25519PublicKey = userED25519KeyPair.publicKey.asHexString
            val verificationData = buildString {
                append("retrieve")
                if (namespace != 0) append(namespace)
                append(timestamp)
            }.toByteArray()
            val signature = try {
                signAndEncode(verificationData, userED25519KeyPair)
            } catch (exception: Exception) {
                return Promise.ofFail(Error.SigningFailed)
            }
            parameters["timestamp"] = timestamp
            parameters["pubkey_ed25519"] = ed25519PublicKey
            parameters["signature"] = signature
        }

        // If the namespace is default (0) here it will be implicitly read as 0 on the storage server
        // we only need to specify it explicitly if we want to (in future) or if it is non-zero
        if (namespace != 0) {
            parameters["namespace"] = namespace
        }

        // Make the request
        return invoke(Snode.Method.Retrieve, snode, parameters, publicKey)
    }

    fun buildAuthenticatedStoreBatchInfo(namespace: Int, message: SnodeMessage): SnodeBatchRequestInfo? {
        // used for sig generation since it is also the value used in timestamp parameter
        val messageTimestamp = message.timestamp

        val userED25519KeyPair = runCatching { MessagingModuleConfiguration.shared.getUserED25519KeyPair() }.getOrNull() ?: return null

        val verificationData = "store$namespace$messageTimestamp".toByteArray()
        val signature = try {
            signAndEncode(verificationData, userED25519KeyPair)
        } catch (e: Exception) {
            Log.e("Loki", "Signing data failed with user secret key", e)
            return null
        }

        val params = buildMap {
            // load the message data params into the sub request
            // currently loads:
            // pubKey
            // data
            // ttl
            // timestamp
            putAll(message.toJSON())
            this["namespace"] = namespace
            // timestamp already set
            this["pubkey_ed25519"] = userED25519KeyPair.publicKey.asHexString
            this["signature"] = signature
        }

        return SnodeBatchRequestInfo(
            Snode.Method.SendMessage.rawValue,
            params,
            namespace
        )
    }

    /**
     * Message hashes can be shared across multiple namespaces (for a single public key destination)
     * @param publicKey the destination's identity public key to delete from (05...)
     * @param messageHashes a list of stored message hashes to delete from the server
     * @param required indicates that *at least one* message in the list is deleted from the server, otherwise it will return 404
     */
    fun buildAuthenticatedDeleteBatchInfo(publicKey: String, messageHashes: List<String>, required: Boolean = false): SnodeBatchRequestInfo? {
        val userEd25519KeyPair = try {
            MessagingModuleConfiguration.shared.getUserED25519KeyPair() ?: return null
        } catch (e: Exception) {
            return null
        }
        val ed25519PublicKey = userEd25519KeyPair.publicKey.asHexString
        val verificationData = sequenceOf("delete").plus(messageHashes).toByteArray()
        val signature = try {
            signAndEncode(verificationData, userEd25519KeyPair)
        } catch (e: Exception) {
            Log.e("Loki", "Signing data failed with user secret key", e)
            return null
        }
        val params = buildMap {
            this["pubkey"] = publicKey
            this["required"] = required // could be omitted technically but explicit here
            this["messages"] = messageHashes
            this["pubkey_ed25519"] = ed25519PublicKey
            this["signature"] = signature
        }
        return SnodeBatchRequestInfo(
            Snode.Method.DeleteMessage.rawValue,
            params,
            null
        )
    }

    fun buildAuthenticatedRetrieveBatchRequest(snode: Snode, publicKey: String, namespace: Int = 0, maxSize: Int? = null): SnodeBatchRequestInfo? {
        val lastHashValue = database.getLastMessageHashValue(snode, publicKey, namespace) ?: ""
        val userEd25519KeyPair = runCatching { MessagingModuleConfiguration.shared.getUserED25519KeyPair() }.getOrNull() ?: return null
        val ed25519PublicKey = userEd25519KeyPair.publicKey.asHexString
        val timestamp = System.currentTimeMillis() + clockOffset
        val verificationData = if (namespace == 0) "retrieve$timestamp".toByteArray()
        else "retrieve$namespace$timestamp".toByteArray()
        val signature = try {
            signAndEncode(verificationData, userEd25519KeyPair)
        } catch (e: Exception) {
            Log.e("Loki", "Signing data failed with user secret key", e)
            return null
        }
        val params = buildMap {
            this["pubkey"] = publicKey
            this["last_hash"] = lastHashValue
            this["timestamp"] = timestamp
            this["pubkey_ed25519"] = ed25519PublicKey
            this["signature"] = signature
            if (namespace != 0) this["namespace"] = namespace
            if (maxSize != null) this["max_size"] = maxSize
        }
        return SnodeBatchRequestInfo(
            Snode.Method.Retrieve.rawValue,
            params,
            namespace
        )
    }

    fun buildAuthenticatedAlterTtlBatchRequest(
        messageHashes: List<String>,
        newExpiry: Long,
        publicKey: String,
        shorten: Boolean = false,
        extend: Boolean = false): SnodeBatchRequestInfo? {
        val params = buildAlterTtlParams(messageHashes, newExpiry, publicKey, extend, shorten) ?: return null
        return SnodeBatchRequestInfo(
            Snode.Method.Expire.rawValue,
            params,
            null
        )
    }

    fun getRawBatchResponse(snode: Snode, publicKey: String, requests: List<SnodeBatchRequestInfo>, sequence: Boolean = false): RawResponsePromise {
        val parameters = buildMap { this["requests"] = requests }
        return invoke(if (sequence) Snode.Method.Sequence else Snode.Method.Batch, snode, parameters, publicKey).success { rawResponses ->
            rawResponses["results"].let { it as List<RawResponse> }
                .asSequence()
                .filter { it["code"] as? Int != 200 }
                .forEach { response ->
                    Log.w("Loki", "response code was not 200")
                    handleSnodeError(
                        response["code"] as? Int ?: 0,
                        response["body"] as? Map<*, *>,
                        snode,
                        publicKey
                    )
                }
        }
    }

    fun getExpiries(messageHashes: List<String>, publicKey: String) : RawResponsePromise {
        val userEd25519KeyPair = MessagingModuleConfiguration.shared.getUserED25519KeyPair() ?: return Promise.ofFail(NullPointerException("No user key pair"))
        val hashes = messageHashes.takeIf { it.size != 1 } ?: (messageHashes + "///////////////////////////////////////////") // TODO remove this when bug is fixed on nodes.
        return retryIfNeeded(maxRetryCount) {
            val timestamp = System.currentTimeMillis() + clockOffset
            val signData = "${Snode.Method.GetExpiries.rawValue}$timestamp${hashes.joinToString(separator = "")}".toByteArray()

            val ed25519PublicKey = userEd25519KeyPair.publicKey.asHexString
            val signature = try {
                signAndEncode(signData, userEd25519KeyPair)
            } catch (e: Exception) {
                Log.e("Loki", "Signing data failed with user secret key", e)
                return@retryIfNeeded Promise.ofFail(e)
            }
            val params = mapOf(
                "pubkey" to publicKey,
                "messages" to hashes,
                "timestamp" to timestamp,
                "pubkey_ed25519" to ed25519PublicKey,
                "signature" to signature
            )
            getSingleTargetSnode(publicKey) bind { snode ->
                invoke(Snode.Method.GetExpiries, snode, params, publicKey)
            }
        }
    }

    fun alterTtl(messageHashes: List<String>, newExpiry: Long, publicKey: String, extend: Boolean = false, shorten: Boolean = false): RawResponsePromise =
        retryIfNeeded(maxRetryCount) {
            val params = buildAlterTtlParams(messageHashes, newExpiry, publicKey, extend, shorten)
                ?: return@retryIfNeeded Promise.ofFail(
                    Exception("Couldn't build signed params for alterTtl request for newExpiry=$newExpiry, extend=$extend, shorten=$shorten")
                )
            getSingleTargetSnode(publicKey).bind { snode ->
                invoke(Snode.Method.Expire, snode, params, publicKey)
            }
        }

    private fun buildAlterTtlParams( // TODO: in future this will probably need to use the closed group subkeys / admin keys for group swarms
        messageHashes: List<String>,
        newExpiry: Long,
        publicKey: String,
        extend: Boolean = false,
        shorten: Boolean = false
    ): Map<String, Any>? {
        val userEd25519KeyPair = MessagingModuleConfiguration.shared.getUserED25519KeyPair() ?: return null

        val shortenOrExtend = if (extend) "extend" else if (shorten) "shorten" else ""

        val signData = "${Snode.Method.Expire.rawValue}$shortenOrExtend$newExpiry${messageHashes.joinToString(separator = "")}".toByteArray()

        val signature = try {
            signAndEncode(signData, userEd25519KeyPair)
        } catch (e: Exception) {
            Log.e("Loki", "Signing data failed with user secret key", e)
            return null
        }

        return buildMap {
            this["expiry"] = newExpiry
            this["messages"] = messageHashes
            when {
                extend -> this["extend"] = true
                shorten -> this["shorten"] = true
            }
            this["pubkey"] = publicKey
            this["pubkey_ed25519"] = userEd25519KeyPair.publicKey.asHexString
            this["signature"] = signature
        }
    }

    fun getMessages(publicKey: String): MessageListPromise = retryIfNeeded(maxRetryCount) {
       getSingleTargetSnode(publicKey).bind { snode ->
            getRawMessages(snode, publicKey).map { parseRawMessagesResponse(it, snode, publicKey) }
        }
    }

    private fun getNetworkTime(snode: Snode): Promise<Pair<Snode, Long>, Exception> =
        invoke(Snode.Method.Info, snode, emptyMap()).map { rawResponse ->
            val timestamp = rawResponse["timestamp"] as? Long ?: -1
            snode to timestamp
        }

    fun sendMessage(message: SnodeMessage, requiresAuth: Boolean = false, namespace: Int = 0): RawResponsePromise =
        retryIfNeeded(maxRetryCount) {
            val module = MessagingModuleConfiguration.shared
            val userED25519KeyPair = module.getUserED25519KeyPair() ?: return@retryIfNeeded Promise.ofFail(Error.NoKeyPair)
            val parameters = message.toJSON().toMutableMap<String, Any>()
            // Construct signature
            if (requiresAuth) {
                val sigTimestamp = nowWithOffset
                val ed25519PublicKey = userED25519KeyPair.publicKey.asHexString
                // assume namespace here is non-zero, as zero namespace doesn't require auth
                val verificationData = "store$namespace$sigTimestamp".toByteArray()
                val signature = try {
                    signAndEncode(verificationData, userED25519KeyPair)
                } catch (exception: Exception) {
                    return@retryIfNeeded Promise.ofFail(Error.SigningFailed)
                }
                parameters["sig_timestamp"] = sigTimestamp
                parameters["pubkey_ed25519"] = ed25519PublicKey
                parameters["signature"] = signature
            }
            // If the namespace is default (0) here it will be implicitly read as 0 on the storage server
            // we only need to specify it explicitly if we want to (in future) or if it is non-zero
            if (namespace != 0) {
                parameters["namespace"] = namespace
            }
            val destination = message.recipient
            getSingleTargetSnode(destination).bind { snode ->
                invoke(Snode.Method.SendMessage, snode, parameters, destination)
            }
        }

    fun deleteMessage(publicKey: String, serverHashes: List<String>): Promise<Map<String,Boolean>, Exception> =
        retryIfNeeded(maxRetryCount) {
            val module = MessagingModuleConfiguration.shared
            val userED25519KeyPair = module.getUserED25519KeyPair() ?: return@retryIfNeeded Promise.ofFail(Error.NoKeyPair)
            val userPublicKey = module.storage.getUserPublicKey() ?: return@retryIfNeeded Promise.ofFail(Error.NoKeyPair)
            getSingleTargetSnode(publicKey).bind { snode ->
                retryIfNeeded(maxRetryCount) {
                    val verificationData = sequenceOf(Snode.Method.DeleteMessage.rawValue).plus(serverHashes).toByteArray()
                    val deleteMessageParams = mapOf(
                        "pubkey" to userPublicKey,
                        "pubkey_ed25519" to userED25519KeyPair.publicKey.asHexString,
                        "messages" to serverHashes,
                        "signature" to signAndEncode(verificationData, userED25519KeyPair)
                    )
                    invoke(Snode.Method.DeleteMessage, snode, deleteMessageParams, publicKey).map { rawResponse ->
                        val swarms = rawResponse["swarm"] as? Map<String, Any> ?: return@map mapOf()
                        val result = swarms.mapNotNull { (hexSnodePublicKey, rawJSON) ->
                            val json = rawJSON as? Map<String, Any> ?: return@mapNotNull null
                            val isFailed = json["failed"] as? Boolean ?: false
                            val statusCode = json["code"] as? String
                            val reason = json["reason"] as? String
                            hexSnodePublicKey to if (isFailed) {
                                Log.e("Loki", "Failed to delete messages from: $hexSnodePublicKey due to error: $reason ($statusCode).")
                                false
                            } else {
                                val hashes = json["deleted"] as List<String> // Hashes of deleted messages
                                val signature = json["signature"] as String
                                val snodePublicKey = Key.fromHexString(hexSnodePublicKey)
                                // The signature looks like ( PUBKEY_HEX || RMSG[0] || ... || RMSG[N] || DMSG[0] || ... || DMSG[M] )
                                val message = sequenceOf(userPublicKey).plus(serverHashes).plus(hashes).toByteArray()
                                sodium.cryptoSignVerifyDetached(Base64.decode(signature), message, message.size, snodePublicKey.asBytes)
                            }
                        }
                        return@map result.toMap()
                    }.fail { e ->
                        Log.e("Loki", "Failed to delete messages", e)
                    }
                }
            }
        }

    // Parsing
    private fun parseSnodes(rawResponse: Any): List<Snode> =
        (rawResponse as? Map<*, *>)
            ?.run { get("snodes") as? List<*> }
            ?.asSequence()
            ?.mapNotNull { it as? Map<*, *> }
            ?.mapNotNull {
                createSnode(
                    address = it["ip"] as? String,
                    port = (it["port"] as? String)?.toInt(),
                    ed25519Key = it[KEY_ED25519] as? String,
                    x25519Key = it[KEY_X25519] as? String
                ).apply { if (this == null) Log.d("Loki", "Failed to parse snode from: ${it.prettifiedDescription()}.") }
            }?.toList() ?: listOf<Snode>().also { Log.d("Loki", "Failed to parse snodes from: ${rawResponse.prettifiedDescription()}.") }

    fun deleteAllMessages(): Promise<Map<String,Boolean>, Exception> =
        retryIfNeeded(maxRetryCount) {
            val module = MessagingModuleConfiguration.shared
            val userED25519KeyPair = module.getUserED25519KeyPair() ?: return@retryIfNeeded Promise.ofFail(Error.NoKeyPair)
            val userPublicKey = module.storage.getUserPublicKey() ?: return@retryIfNeeded Promise.ofFail(Error.NoKeyPair)
            getSingleTargetSnode(userPublicKey).bind { snode ->
                retryIfNeeded(maxRetryCount) {
                    getNetworkTime(snode).bind { (_, timestamp) ->
                        val verificationData = (Snode.Method.DeleteAll.rawValue + Namespace.ALL + timestamp.toString()).toByteArray()
                        val deleteMessageParams = mapOf(
                            "pubkey" to userPublicKey,
                            "pubkey_ed25519" to userED25519KeyPair.publicKey.asHexString,
                            "timestamp" to timestamp,
                            "signature" to signAndEncode(verificationData, userED25519KeyPair),
                            "namespace" to Namespace.ALL,
                        )
                        invoke(Snode.Method.DeleteAll, snode, deleteMessageParams, userPublicKey).map {
                            rawResponse -> parseDeletions(userPublicKey, timestamp, rawResponse)
                        }.fail { e ->
                            Log.e("Loki", "Failed to clear data", e)
                        }
                    }
                }
            }
        }

    fun parseRawMessagesResponse(rawResponse: RawResponse, snode: Snode, publicKey: String, namespace: Int = 0, updateLatestHash: Boolean = true, updateStoredHashes: Boolean = true): List<Pair<SignalServiceProtos.Envelope, String?>> =
        (rawResponse["messages"] as? List<*>)?.let { messages ->
            if (updateLatestHash) {
                updateLastMessageHashValueIfPossible(snode, publicKey, messages, namespace)
            }
            removeDuplicates(publicKey, messages, namespace, updateStoredHashes).let(::parseEnvelopes)
        } ?: listOf()

    fun updateLastMessageHashValueIfPossible(snode: Snode, publicKey: String, rawMessages: List<*>, namespace: Int) {
        val lastMessageAsJSON = rawMessages.lastOrNull() as? Map<*, *>
        val hashValue = lastMessageAsJSON?.get("hash") as? String
        if (hashValue != null) {
            database.setLastMessageHashValue(snode, publicKey, hashValue, namespace)
        } else if (rawMessages.isNotEmpty()) {
            Log.d("Loki", "Failed to update last message hash value from: ${rawMessages.prettifiedDescription()}.")
        }
    }

    fun removeDuplicates(publicKey: String, rawMessages: List<*>, namespace: Int, updateStoredHashes: Boolean): List<*> {
        val originalMessageHashValues = database.getReceivedMessageHashValues(publicKey, namespace) ?: emptySet()
        val receivedMessageHashValues = originalMessageHashValues.toMutableSet()
        val result = rawMessages.filter { rawMessage ->
            (rawMessage as? Map<*, *>)
                ?.let { it["hash"] as? String }
                ?.let { receivedMessageHashValues.add(it) }
                ?: false.also { Log.d("Loki", "Missing hash value for message: ${rawMessage?.prettifiedDescription()}.") }
        }
        if (updateStoredHashes && originalMessageHashValues.containsAll(receivedMessageHashValues)) {
            database.setReceivedMessageHashValues(publicKey, receivedMessageHashValues, namespace)
        }
        return result
    }

    private fun parseEnvelopes(rawMessages: List<*>): List<Pair<SignalServiceProtos.Envelope, String?>> =
        rawMessages.mapNotNull { rawMessage ->
            val rawMessageAsJSON = rawMessage as? Map<*, *>
            val base64EncodedData = rawMessageAsJSON?.get("data") as? String
            val data = base64EncodedData?.let { Base64.decode(it) }

            data?.runCatching(MessageWrapper::unwrap)
                ?.map { it to rawMessageAsJSON["hash"] as? String }
                ?.onFailure { Log.d("Loki", "Failed to unwrap data for message: ${rawMessage.prettifiedDescription()}.") }

            if (data != null) {
                try {
                    MessageWrapper.unwrap(data) to rawMessageAsJSON["hash"] as? String
                } catch (e: Exception) {
                    Log.d("Loki", "Failed to unwrap data for message: ${rawMessage.prettifiedDescription()}.")
                    null
                }
            } else {
                Log.d("Loki", "Failed to decode data for message: ${rawMessage?.prettifiedDescription()}.")
                null
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun parseDeletions(userPublicKey: String, timestamp: Long, rawResponse: RawResponse): Map<String, Boolean> {
        val swarms = rawResponse["swarm"] as? Map<String, Any> ?: return mapOf()
        return swarms.mapNotNull { (hexSnodePublicKey, rawJSON) ->
            val json = rawJSON as? Map<String, Any> ?: return@mapNotNull null
            val isFailed = json["failed"] as? Boolean ?: false
            val statusCode = json["code"] as? String
            val reason = json["reason"] as? String
            hexSnodePublicKey to if (isFailed) {
                Log.e("Loki", "Failed to delete all messages from: $hexSnodePublicKey due to error: $reason ($statusCode).")
                false
            } else {
                val hashes = (json["deleted"] as Map<String,List<String>>).flatMap { (_, hashes) -> hashes }.sorted() // Hashes of deleted messages
                val signature = json["signature"] as String
                val snodePublicKey = Key.fromHexString(hexSnodePublicKey)
                // The signature looks like ( PUBKEY_HEX || TIMESTAMP || DELETEDHASH[0] || ... || DELETEDHASH[N] )
                val message = sequenceOf(userPublicKey, "$timestamp").plus(hashes).toByteArray()
                sodium.cryptoSignVerifyDetached(Base64.decode(signature), message, message.size, snodePublicKey.asBytes)
            }
        }.toMap()
    }

    // endregion

    // Error Handling
    internal fun handleSnodeError(statusCode: Int, json: Map<*, *>?, snode: Snode, publicKey: String? = null): Throwable? = runCatching {
        fun handleBadSnode() {
            val oldFailureCount = snodeFailureCount[snode] ?: 0
            val newFailureCount = oldFailureCount + 1
            snodeFailureCount[snode] = newFailureCount
            Log.d("Loki", "Couldn't reach snode at $snode; setting failure count to $newFailureCount.")
            if (newFailureCount >= snodeFailureThreshold) {
                Log.d("Loki", "Failure threshold reached for: $snode; dropping it.")
                publicKey?.let { dropSnodeFromSwarmIfNeeded(snode, it) }
                snodePool -= snode
                Log.d("Loki", "Snode pool count: ${snodePool.count()}.")
                snodeFailureCount[snode] = 0
            }
        }
        when (statusCode) {
            // Usually indicates that the snode isn't up to date
            400, 500, 502, 503 -> handleBadSnode()
            406 -> {
                Log.d("Loki", "The user's clock is out of sync with the service node network.")
                broadcaster.broadcast("clockOutOfSync")
                throw Error.ClockOutOfSync
            }
            421 -> {
                // The snode isn't associated with the given public key anymore
                if (publicKey != null) {
                    json?.let(::parseSnodes)
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { database.setSwarm(publicKey, it.toSet()) }
                        ?: run {
                            Log.d("Loki", "Invalidating swarm for: $publicKey.")
                            dropSnodeFromSwarmIfNeeded(snode, publicKey)
                        }
                } else Log.d("Loki", "Got a 421 without an associated public key.")
            }
            404 -> {
                Log.d("Loki", "404, probably no file found")
                throw Error.Generic
            }
            else -> {
                handleBadSnode()
                Log.d("Loki", "Unhandled response code: ${statusCode}.")
                throw Error.Generic
            }
        }
    }.exceptionOrNull()
}

// Type Aliases
typealias RawResponse = Map<*, *>
typealias MessageListPromise = Promise<List<Pair<SignalServiceProtos.Envelope, String?>>, Exception>
typealias RawResponsePromise = Promise<RawResponse, Exception>
