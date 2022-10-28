package org.session.libsession.utilities.bencode

import java.util.LinkedList

object Bencode {
    class Decoder(source: CharArray) {

        private val iterator = LinkedList<Char>().apply {
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
            val lengthStrings = buildString {
                while (iterator.isNotEmpty() && iterator.peek() != SEPARATOR) {
                    append(iterator.pop())
                }
            }.toCharArray()
            iterator.pop() // drop `:`
            val length = String(lengthStrings).toIntOrNull(10) ?: return null
            val remaining = (0 until length).map { iterator.pop() }.toCharArray()
            return BencodeString(String(remaining))
        }

        /**
         * Decode an int element from iterator assumed to have structure `i{int}e`
         */
        private fun decodeInt(): BencodeElement? {
            iterator.pop() // drop `i`
            val intString = buildString {
                while (iterator.isNotEmpty() && iterator.peek() != END_INDICATOR) {
                    append(iterator.pop())
                }
            }
            val asInt = intString.toIntOrNull(10) ?: return null
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
            private val NUMBERS = arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
            private const val INT_INDICATOR = 'i'
            private const val LIST_INDICATOR = 'l'
            private const val DICT_INDICATOR = 'd'
            private const val END_INDICATOR = 'e'
            private const val SEPARATOR = ':'
        }

    }

}

sealed class BencodeElement {
    abstract fun encode(): CharArray
}

fun String.bencode() = BencodeString(this)
fun Int.bencode() = BencodeInteger(this)

data class BencodeString(val value: String): BencodeElement() {
    override fun encode(): CharArray = buildString {
        append(value.length.toString())
        append(':')
        append(value)
    }.toCharArray()
}
data class BencodeInteger(val value: Int): BencodeElement() {
    override fun encode(): CharArray = buildString {
        append('i')
        append(value)
        append('e')
    }.toCharArray()
}
data class BencodeList(val values: List<BencodeElement>): BencodeElement() {

    constructor(vararg values: BencodeElement) : this(values.toList())

    override fun encode(): CharArray = buildString {
        append('l')
        for (value in values) {
            append(value.encode())
        }
        append('e')
    }.toCharArray()
}
data class BencodeDict(val values: Map<String, BencodeElement>): BencodeElement() {

    constructor(vararg values: Pair<String, BencodeElement>) : this(values.toMap())

    override fun encode(): CharArray = buildString {
        append('d')
        for ((key, value) in values) {
            append(BencodeString(key).encode())
            append(value.encode())
        }
        append('e')
    }.toCharArray()
}