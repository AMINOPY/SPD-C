package com.spdcon.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigLinkParserTest {
    @Test
    fun parsesVlessLink() {
        val result = ConfigLinkParser.parse(
            "vless://00000000-0000-0000-0000-000000000000@example.com:443?type=ws&security=tls#My%20Node"
        )
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals(ConfigProtocol.VLESS, profile.protocol)
        assertEquals("example.com", profile.server)
        assertEquals(443, profile.port)
        assertEquals("My Node", profile.name)
        assertEquals("ws", profile.network)
        assertEquals("tls", profile.security)
    }

    @Test
    fun rejectsUnsupportedScheme() {
        val result = ConfigLinkParser.parse("ftp://example.com/file")
        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsOversizedInput() {
        val result = ConfigLinkParser.parse("vless://" + "a".repeat(33_000))
        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsDuplicateQueryParametersInsteadOfSilentlyOverwriting() {
        val result = ConfigLinkParser.parse(
            "vless://00000000-0000-0000-0000-000000000000@example.com:443?type=ws&type=tcp&security=tls"
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsQueryParametersWithoutNames() {
        val result = ConfigLinkParser.parse(
            "vless://00000000-0000-0000-0000-000000000000@example.com:443?=unexpected&type=ws&security=tls"
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun preservesLiteralPlusInUriQueryValues() {
        val parsed = ConfigLinkParser.parse(
            "vless://abc@example.com:443?type=ws&security=tls&path=%2Fws%2Ba%2Bb&host=edge.example.com#Plus"
        )
        assertTrue(parsed.isSuccess)
        assertEquals("/ws+a+b", parsed.getOrThrow().parameters["path"])
    }

}
