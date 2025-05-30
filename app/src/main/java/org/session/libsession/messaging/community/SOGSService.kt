package org.session.libsession.messaging.community

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

interface SOGSService {
    @GET("room/{roomToken}")
    suspend fun getRoomInfo(@Path("roomToken") roomToken: String): RoomInfo
}

interface UnsignedSOGSService {
    @GET("capabilities")
    suspend fun getCapabilities(): Capabilities
}

@Serializable
data class Capabilities(
    val capabilities: List<String>
)