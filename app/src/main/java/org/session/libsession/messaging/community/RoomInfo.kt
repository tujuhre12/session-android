package org.session.libsession.messaging.community

import kotlinx.serialization.Serializable

@Serializable
data class RoomInfo(
    val token: String,
    val name: String,
    val description: String = "",
)