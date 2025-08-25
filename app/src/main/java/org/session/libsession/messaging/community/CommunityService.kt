package org.session.libsession.messaging.community

import retrofit2.http.GET

interface CommunityService {
    @GET("capabilities")
    suspend fun getServerCapabilities(): List<Capability>
}
