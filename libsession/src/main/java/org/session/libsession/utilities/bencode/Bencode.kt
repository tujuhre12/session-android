package org.session.libsession.utilities.bencode

import org.session.libsession.utilities.bencode.Bencode.Decoder.Companion.DICT_INDICATOR
import org.session.libsession.utilities.bencode.Bencode.Decoder.Companion.END_INDICATOR
import org.session.libsession.utilities.bencode.Bencode.Decoder.Companion.LIST_INDICATOR
import java.util.LinkedList

object Bencode {
    class Decoder(source: ByteArray) {

        private val iterator = LinkedList<Byte>().apply {
            addAll(source.asIterable())
        }

        /**
         * Decode an element based on next marker assumed to be string/int/list/dict or return null
         */
        fun decode(): BencodeElement? {
            val result = when (iterator.peek()) {
                in NUMBERS -> decodeString()
                INT_INDICATOR -> decodeInt()
                LIST_INDICATOR -> decodeList()
                DICT_INDICATOR -> decodeDict()
                else -> {
                    null
                }
            }
            return result
        }

        /**
         * Decode a string element from iterator assumed to have structure `{length}:{data}`
         */
        private fun decodeString(): BencodeString? {
            val lengthStrings = buildList<Byte> {
                while (iterator.isNotEmpty() && iterator.peek() != SEPARATOR) {
                    add(iterator.pop())
                }
            }.toByteArray()
            iterator.pop() // drop `:`
            val length = lengthStrings.decodeToString().toIntOrNull(10) ?: return null
            val remaining = (0 until length).map { iterator.pop() }.toByteArray()
            return BencodeString(String(remaining))
        }

        /**
         * Decode an int element from iterator assumed to have structure `i{int}e`
         */
        private fun decodeInt(): BencodeElement? {
            iterator.pop() // drop `i`
            val intString = buildList<Byte> {
                while (iterator.isNotEmpty() && iterator.peek() != END_INDICATOR) {
                    add(iterator.pop())
                }
            }.toByteArray()
            val asInt = intString.decodeToString().toIntOrNull(10) ?: return null
            iterator.pop() // drop `e`
            return BencodeInteger(asInt)
        }

        /**
         * Decode a list element from iterator assumed to have structure `l{data}e`
         */
        private fun decodeList(): BencodeElement {
            iterator.pop() // drop `l`
            val listElements = mutableListOf<BencodeElement>()
            while (iterator.isNotEmpty() && iterator.peek() != END_INDICATOR) {
                decode()?.let { nextElement ->
                    listElements += nextElement
                }
            }
            iterator.pop() // drop `e`
            return BencodeList(listElements)
        }

        /**
         * Decode a dict element from iterator assumed to have structure `d{data}e`
         */
        private fun decodeDict(): BencodeElement? {
            iterator.pop() // drop `d`
            val dictElements = mutableMapOf<String,BencodeElement>()
            while (iterator.isNotEmpty() && iterator.peek() != END_INDICATOR) {
                val key = decodeString() ?: return null
                val value = decode() ?: return null
                dictElements += key.value to value
            }
            iterator.pop() // drop `e`
            return BencodeDict(dictElements)
        }

        companion object {
            val NUMBERS = arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9').map { it.code.toByte() }
            const val INT_INDICATOR = 'i'.code.toByte()
            const val LIST_INDICATOR = 'l'.code.toByte()
            const val DICT_INDICATOR = 'd'.code.toByte()
            const val END_INDICATOR = 'e'.code.toByte()
            const val SEPARATOR = ':'.code.toByte()
        }

    }

}

sealed class BencodeElement {
    abstract fun encode(): ByteArray
}

fun String.bencode() = BencodeString(this)
fun Int.bencode() = BencodeInteger(this)

data class BencodeString(val value: String): BencodeElement() {
    override fun encode(): ByteArray = buildString {
        append(value.length.toString())
        append(':')
        append(value)
    }.toByteArray()
}
data class BencodeInteger(val value: Int): BencodeElement() {
    override fun encode(): ByteArray = buildString {
        append('i')
        append(value)
        append('e')
    }.toByteArray()
}

data class BencodeList(val values: List<BencodeElement>): BencodeElement() {

    constructor(vararg values: BencodeElement) : this(values.toList())

    override fun encode(): ByteArray = buildList {
        add(LIST_INDICATOR)
        for (value in values) {
            addAll(value.encode().toTypedArray())
        }
        add(END_INDICATOR)
    }.toByteArray()
}

data class BencodeDict(val values: Map<String, BencodeElement>): BencodeElement() {

    constructor(vararg values: Pair<String, BencodeElement>) : this(values.toMap())

    override fun encode(): ByteArray = buildList {
        add(DICT_INDICATOR)
        for ((key, value) in values) {
            addAll(BencodeString(key).encode().toTypedArray())
            addAll(value.encode().toTypedArray())
        }
        add(END_INDICATOR)
    }.toByteArray()
}