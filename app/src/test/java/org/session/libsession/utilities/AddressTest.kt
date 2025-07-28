package org.session.libsession.utilities

import org.junit.Assert.*
import org.junit.Test

class AddressTest {

    data class Scenario(
        val name: String,
        val rawAddress: String,
        val expectClass: Class<out Address>,
    )

    private val testScenarios = listOf(
        Scenario("Standard Address", "0538e63512fd78c04d45b83ec7f0f3d593f60276ce535d1160eb589a00cca7db59", Address.Standard::class.java),
        Scenario("Legacy group address", GroupUtil.doubleEncodeGroupID("ab0123"), Address.LegacyGroup::class.java),
        Scenario("Blinded Address", "1538e63512fd78c04d45b83ec7f0f3d593f60276ce535d1160eb589a00cca7db59", Address.Blinded::class.java),
        Scenario("Community Address", "__loki_public_chat_group__!68747470733a2f2f6f70656e2e67657473657373696f6e2e6f72672e73657373696f6e2d75706461746573", Address.Community::class.java),
    )

    @Test
    fun `should serialize and deserialize`() {
        testScenarios.forEach { scenario ->
            runCatching {
                val address = Address.fromSerialized(scenario.rawAddress)
                assertEquals(scenario.expectClass, address::class.java)

                val serialized = address.address

                assertEquals(scenario.rawAddress, serialized)
            }.onFailure {
                throw RuntimeException("Test failed for scenario: ${scenario.name}", it)
            }
        }
    }
}