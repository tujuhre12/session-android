package org.thoughtcrime.securesms.database

data class ExpirationInfo(val id: Long, val expiresIn: Long, val expireStarted: Long, val isMms: Boolean)
