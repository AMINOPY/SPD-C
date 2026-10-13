package com.spdcon.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigLinkSerializerTest {
    @Test
    fun serializesVlessWithRealityParameters() {
        val profile = ConfigProfile(
            name = "Reality node",
            protocol = ConfigProtocol.VLESS,
            server = "example.com",
            port = 443,
            credential = "00000000-0000-0000-0000-000000000000",
            network = "raw",
            security = "reality",
            parameters = mapOf(
                "sni" to "example.com",
                "pbk" to "public-key",
                "sid" to "a1b2",
                "fp" to "chrome"
            )
        )
        val result = ConfigLinkSerializer.serialize(profile)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().startsWith("vless://"))
        assertTrue(result.getOrThrow().contains("security=reality"))
        assertTrue(result.getOrThrow().contains("pbk=public-key"))
    }

    @Test
    fun parsesAndSerializesShadowsocks() {
        val input = "ss://" + java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("chacha20-ietf-poly1305:password@example.com:8388".toByteArray()) + "#Test"
        val parsed = ConfigLinkParser.parse(input)
        assertTrue(parsed.isSuccess)
        val profile = parsed.getOrThrow()
        assertEquals(ConfigProtocol.SHADOWSOCKS, profile.protocol)
        assertEquals("chacha20-ietf-poly1305", profile.parameters["method"])
        val serialized = ConfigLinkSerializer.serialize(profile)
        assertTrue(serialized.isSuccess)
        assertTrue(serialized.getOrThrow().startsWith("ss://"))
    }
}
