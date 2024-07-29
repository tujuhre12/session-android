package network.loki.messenger.libsession_util.util

object BlindKeyAPI {
    init {
        System.loadLibrary("session_util")
    }

    external fun blindVersionKeyPair(ed25519SecretKey: ByteArray): KeyPair
    external fun blindVersionSign(ed25519SecretKey: ByteArray, timestamp: Long): ByteArray
}