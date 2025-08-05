package org.session.libsession.utilities.recipients

import network.loki.messenger.libsession_util.util.Bytes
import network.loki.messenger.libsession_util.util.UserPic

/**
 * Represents a remote file that can be downloaded.
 */
sealed interface RemoteFile {
    data class Encrypted(val url: String, val key: Bytes) : RemoteFile
    data class Community(val communityServerBaseUrl: String, val roomId: String, val fileId: String) : RemoteFile
    companion object {
        fun UserPic.toRecipientAvatar(): Encrypted? {
            return when {
                url.isBlank() -> null
                else ->  Encrypted(
                    url = url,
                    key = key
                )
            }
        }

        fun from(url: String, bytes: ByteArray?): RemoteFile? {
            return if (url.isNotBlank() && bytes != null && bytes.isNotEmpty()) {
                Encrypted(url, Bytes(bytes))
            } else {
                null
            }
        }
    }
}

fun RemoteFile.toUserPic(): UserPic? {
    return when (this) {
        is RemoteFile.Encrypted -> UserPic(url, key)
        is RemoteFile.Community -> null
    }
}