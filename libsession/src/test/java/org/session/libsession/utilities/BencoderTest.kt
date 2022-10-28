package org.session.libsession.utilities

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.session.libsession.utilities.bencode.BencodeDict
import org.session.libsession.utilities.bencode.BencodeInteger
import org.session.libsession.utilities.bencode.BencodeList
import org.session.libsession.utilities.bencode.BencodeString
import org.session.libsession.utilities.bencode.Bencoder

class BencoderTest {

    @Test
    fun `it should decode a basic string`() {
        val basicString = "5:howdy".toCharArray()
        val bencoder = Bencoder.Decoder(basicString)
        val result = bencoder.decode()
        assertEquals(BencodeString("howdy"), result)
    }

    @Test
    fun `it should decode a basic integer`() {
        val basicInteger = "i3e".toCharArray()
        val bencoder = Bencoder.Decoder(basicInteger)
        val result = bencoder.decode()
        assertEquals(BencodeInteger(3), result)
    }

    @Test
    fun `it should decode a list of integers`() {
        val basicIntList = "li1ei2ee".toCharArray()
        val bencoder = Bencoder.Decoder(basicIntList)
        val result = bencoder.decode()
        assertEquals(
            BencodeList(
                listOf(
                    BencodeInteger(1),
                    BencodeInteger(2)
                )
            ),
            result
        )
    }

    @Test
    fun `it should decode a basic dict`() {
        val basicDict = "d4:spaml1:a1:bee".toCharArray()
        val bencoder = Bencoder.Decoder(basicDict)
        val result = bencoder.decode()
        assertEquals(
            BencodeDict(
                mapOf(
                    "spam" to BencodeList(
                        listOf(
                            BencodeString("a"),
                            BencodeString("b")
                        )
                    )
                )
            ),
            result
        )
    }

    @Test
    fun `it should encode a basic string`() {
        val basicString = "5:howdy".toCharArray()
        val element = BencodeString("howdy")
        assertArrayEquals(basicString, element.encode())
    }

    @Test
    fun `it should encode a basic int`() {
        val basicInt = "i3e".toCharArray()
        val element = BencodeInteger(3)
        assertArrayEquals(basicInt, element.encode())
    }

    @Test
    fun `it should encode a basic list`() {
        val basicList = "li1ei2ee".toCharArray()
        val element = BencodeList(
            listOf(
                BencodeInteger(1),
                BencodeInteger(2)
            )
        )
        assertArrayEquals(basicList, element.encode())
    }

    @Test
    fun `it should encode a basic dict`() {
        val basicDict = "d4:spaml1:a1:bee".toCharArray()
        val element = BencodeDict(
            mapOf(
                "spam" to BencodeList(
                    listOf(
                        BencodeString("a"),
                        BencodeString("b")
                    )
                )
            )
        )
        assertArrayEquals(basicDict, element.encode())
    }

}