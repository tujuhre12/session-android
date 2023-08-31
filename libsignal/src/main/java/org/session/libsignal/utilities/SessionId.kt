package org.session.libsignal.utilities

class SessionId {

    companion object {
        // needed because JNI doesn't want to detect alternate constructors :shrug:
        @JvmStatic
        fun from(id: String): SessionId = SessionId(id)
    }

    var prefix: IdPrefix?
    var publicKey: String

    constructor(id: String) {
        prefix = IdPrefix.fromValue(id)
        publicKey = id.drop(2)
    }

    constructor(prefix: IdPrefix, publicKey: ByteArray) {
        this.prefix = prefix
        this.publicKey = publicKey.toHexString()
    }

    fun hexString() = prefix?.value + publicKey

    fun bytes(): ByteArray = Hex.fromStringCondensed(prefix?.value + publicKey)
}