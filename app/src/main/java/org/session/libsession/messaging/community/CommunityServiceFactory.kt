package org.session.libsession.messaging.community

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import retrofit2.Retrofit
import javax.inject.Inject

class CommunityServiceFactory @Inject constructor(
    private val json: Json,
) {
    fun create(serverUrl: HttpUrl, serverPubKeyHex: String): CommunityService {
        return Retrofit.Builder()
            .baseUrl(serverUrl)
            .callFactory {  }
    }
}