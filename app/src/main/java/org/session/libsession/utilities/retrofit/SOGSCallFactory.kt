package org.session.libsession.utilities.retrofit

import network.loki.messenger.libsession_util.ED25519
import network.loki.messenger.libsession_util.Hash
import network.loki.messenger.libsession_util.util.BlindKeyAPI
import network.loki.messenger.libsession_util.util.KeyPair
import nl.komponents.kovenant.CancelablePromise
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.IOException
import okio.Timeout
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.SnodeClock
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.IdPrefix
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

class SOGSCallFactory(
    private val serverUrl: HttpUrl,
    private val serverPubKey: String,
    private val clockSource: SnodeClock,
    private val userEd25519KeyPair: KeyPair,
    private val signRequest: Boolean,
) : Call.Factory {
    val useBlindId = AtomicBoolean(false)

    override fun newCall(request: Request): Call {
        require(request.url.host == serverUrl.host && request.url.port == serverUrl.port) {
            "Onion request can only handle requests to the server at ${serverUrl.host}:${serverUrl.port}, " +
                    "but got request to ${request.url.host}:${request.url.port}"
        }

        return OnionRequestCall(request)
    }

    private inner class OnionRequestCall(private val request: Request) : Call {
        private val promise = lazy {
            val normalisedRequest = if (signRequest) {
                val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val timestampSeconds = clockSource.currentTimeSeconds()
                val contentType = request.body?.contentType()
                val bodyBytes = request.body?.let {
                    Buffer().apply { it.writeTo(this) }.readByteArray()
                }

                val bodyHash = bodyBytes?.let { Hash.hash64(it) } ?: byteArrayOf()
                val messageToSign = Hex.fromStringCondensed(serverPubKey)
                    .plus(nonce)
                    .plus("$timestampSeconds".toByteArray())
                    .plus(request.method.toByteArray())
                    .plus(request.url.encodedPath.toByteArray())
                    .plus(bodyHash)

                val signature: ByteArray
                val pubKey: String

                if (useBlindId.get()) {
                    pubKey = AccountId(
                        IdPrefix.BLINDED,
                        BlindKeyAPI.blind15KeyPair(
                            ed25519SecretKey = userEd25519KeyPair.secretKey.data,
                            serverPubKey = Hex.fromStringCondensed(serverPubKey)
                        ).pubKey.data
                    ).hexString

                    signature = BlindKeyAPI.blind15Sign(
                        ed25519SecretKey = userEd25519KeyPair.secretKey.data,
                        serverPubKey = serverPubKey,
                        message = messageToSign
                    )
                } else {
                    pubKey = AccountId(
                        IdPrefix.UN_BLINDED,
                        userEd25519KeyPair.pubKey.data
                    ).hexString

                    signature = ED25519.sign(
                        ed25519PrivateKey = userEd25519KeyPair.secretKey.data,
                        message = messageToSign
                    )
                }

                val requestBuilder = Request.Builder()
                    .url(request.url)

                requestBuilder.header("X-SOGS-Nonce", Base64.encodeBytes(nonce))
                requestBuilder.header("X-SOGS-Timestamp", "$timestampSeconds")
                requestBuilder.header("X-SOGS-Pubkey", pubKey)
                requestBuilder.header("X-SOGS-Signature", Base64.encodeBytes(signature))

                if (bodyBytes != null) {
                    if (request.method.equals("POST", ignoreCase = true)) {
                        requestBuilder.post(bodyBytes.toRequestBody(contentType))
                    } else if (request.method.equals("PUT", ignoreCase = true)) {
                        requestBuilder.put(bodyBytes.toRequestBody(contentType))
                    } else if (request.method.equals("PATCH", ignoreCase = true)) {
                        requestBuilder.patch(bodyBytes.toRequestBody(contentType))
                    } else if (request.method.equals("DELETE", ignoreCase = true)) {
                        requestBuilder.delete(bodyBytes.toRequestBody(contentType))
                    } else if (request.method.equals("GET", ignoreCase = true)) {
                        // GET requests should not have a body, but we can still add headers
                        requestBuilder.get()
                    } else {
                        throw IllegalArgumentException("Unsupported HTTP method: ${request.method}")
                    }
                }

                requestBuilder.build()
            } else {
                request
            }

            OnionRequestAPI.sendOnionRequest(
                request = normalisedRequest,
                server = serverUrl.host,
                x25519PublicKey = serverPubKey
            )
        }

        private val cancelled = AtomicBoolean(false)
        private val executed = AtomicBoolean(false)

        override fun cancel() {
            if (cancelled.getAndSet(true)) {
                return
            }

            if (promise.isInitialized()) {
                @Suppress("UNCHECKED_CAST")
                (promise.value as? CancelablePromise<*, Throwable>)?.cancel(CancellationException())
            }
        }

        override fun clone(): Call = OnionRequestCall(request)

        override fun enqueue(responseCallback: Callback) {
            check(!executed.getAndSet(true)) {
                "Onion requests can only be executed once"
            }

            promise.value.success { resp ->
                val body = resp.body?.let {
                    it.data.toByteString(it.offset, it.len)
                }?.toResponseBody()

                val headers = resp.info.asSequence()
                    .fold(Headers.Builder()) { builder, (key, value) ->
                        if (key is String && value is String) {
                            builder.add(key, value)
                        }
                        builder
                    }
                    .build()

                if (cancelled.get()) {
                    return@success
                }

                responseCallback.onResponse(
                    this,
                    Response.Builder()
                        .request(request)
                        .code(resp.code ?: 500)
                        .message(resp.message.orEmpty())
                        .body(body)
                        .headers(headers)
                        .protocol(Protocol.HTTP_1_1)
                        .build()
                )
            }.fail { error ->
                if (cancelled.get()) {
                    return@fail
                }

                responseCallback.onFailure(this, IOException(error))
            }
        }

        override fun execute(): Response {
            check(!executed.getAndSet(true)) {
                "Onion requests can only be executed once"
            }

            throw UnsupportedOperationException("Onion requests should not be executed synchronously")
        }

        override fun isCanceled(): Boolean = cancelled.get()
        override fun isExecuted(): Boolean = executed.get()
        override fun request(): Request = this.request
        override fun timeout(): Timeout = Timeout.NONE

    }
}
