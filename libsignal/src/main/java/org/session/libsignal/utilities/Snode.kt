package org.session.libsignal.utilities

import android.annotation.SuppressLint

fun Snode(string: String): Snode? {
    val components = string.split("-")
    val address = components[0]
    val port = components.getOrNull(1)?.toIntOrNull() ?: return null
    val ed25519Key = components.getOrNull(2) ?: return null
    val x25519Key = components.getOrNull(3) ?: return null
    val version = components.getOrNull(4)?.let(Snode::Version) ?: Snode.Version.ZERO
    return Snode(address, port, Snode.KeySet(ed25519Key, x25519Key), version)
}

class Snode(val address: String, val port: Int, val publicKeySet: KeySet?, val version: Version) {
    val ip: String get() = address.removePrefix("https://")

    enum class Method(val rawValue: String) {
        GetSwarm("get_snodes_for_pubkey"),
        Retrieve("retrieve"),
        SendMessage("store"),
        DeleteMessage("delete"),
        OxenDaemonRPCCall("oxend_request"),
        Info("info"),
        DeleteAll("delete_all"),
        Batch("batch"),
        Sequence("sequence"),
        Expire("expire"),
        GetExpiries("get_expiries")
    }

    data class KeySet(val ed25519Key: String, val x25519Key: String)

    override fun equals(other: Any?): Boolean {
        return if (other is Snode) {
            address == other.address && port == other.port
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return address.hashCode() xor port.hashCode()
    }

    override fun toString(): String { return "$address:$port" }

    companion object {
        private val CACHE = mutableMapOf<String, Version>()

        @SuppressLint("NotConstructor")
        fun Version(value: String) = CACHE.getOrElse(value) {
            Snode.Version(value)
        }

        fun Version(parts: List<Int>) = Version(parts.joinToString("."))
    }

    @JvmInline
    value class Version(val value: ULong) {
        companion object {
            val ZERO = Version(0UL)
            private const val MASK_BITS = 16
            private const val MASK = 0xFFFFUL

            private fun Sequence<ULong>.foldToVersionAsULong() = take(4).foldIndexed(0UL) { i, acc, it ->
                it.coerceAtMost(MASK) shl (3 - i) * MASK_BITS or acc
            }
        }

        internal constructor(parts: List<Int>): this(
            parts.asSequence()
                .map { it.toByte().toULong() }
                .foldToVersionAsULong()
        )

        internal constructor(value: String): this(
            value.splitToSequence(".")
                .map { it.toULongOrNull() ?: 0UL }
                .foldToVersionAsULong()
        )

        operator fun compareTo(other: Version): Int = value.compareTo(other.value)
    }
}
