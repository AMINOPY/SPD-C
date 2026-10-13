package com.spdcon.app.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxConfigGeneratorTest {
    private fun vless(
        network: String = "tcp",
        security: String = "tls",
        parameters: Map<String, String> = mapOf("sni" to "example.com", "fp" to "chrome")
    ) = ConfigProfile(
        name = "sing-box test",
        protocol = ConfigProtocol.VLESS,
        server = "example.com",
        port = 443,
        credential = "00000000-0000-4000-8000-000000000001",
        network = network,
        security = security,
        parameters = parameters
    )

    @Test
    fun generatesVlessTlsOutboundAndLocalMixedInbound() {
        val root = JSONObject(SingBoxConfigGenerator.generate(vless()).getOrThrow())
        val inbound = root.getJSONArray("inbounds").getJSONObject(0)
        val outbound = root.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("mixed", inbound.getString("type"))
        assertEquals(2080, inbound.getInt("listen_port"))
        assertEquals("vless", outbound.getString("type"))
        assertEquals("00000000-0000-4000-8000-000000000001", outbound.getString("uuid"))
        assertEquals("example.com", outbound.getJSONObject("tls").getString("server_name"))
        assertEquals("chrome", outbound.getJSONObject("tls").getJSONObject("utls").getString("fingerprint"))
        assertEquals("proxy", root.getJSONObject("route").getString("final"))
    }

    @Test
    fun mapsRealityClientSettings() {
        val profile = vless(
            security = "reality",
            parameters = mapOf(
                "sni" to "reality.example.com",
                "pbk" to "public-key",
                "sid" to "aabb",
                "fp" to "chrome"
            )
        )
        val outbound = JSONObject(SingBoxConfigGenerator.generate(profile).getOrThrow())
            .getJSONArray("outbounds").getJSONObject(0)
        val tls = outbound.getJSONObject("tls")
        assertTrue(tls.getBoolean("enabled"))
        assertEquals("public-key", tls.getJSONObject("reality").getString("public_key"))
        assertEquals("aabb", tls.getJSONObject("reality").getString("short_id"))
    }

    @Test
    fun mapsWebsocketPathAndHost() {
        val profile = vless(
            network = "ws",
            security = "tls",
            parameters = mapOf("sni" to "example.com", "path" to "/edge", "host" to "cdn.example.com")
        )
        val outbound = JSONObject(SingBoxConfigGenerator.generate(profile).getOrThrow())
            .getJSONArray("outbounds").getJSONObject(0)
        val transport = outbound.getJSONObject("transport")
        assertEquals("ws", transport.getString("type"))
        assertEquals("/edge", transport.getString("path"))
        assertEquals("cdn.example.com", transport.getJSONObject("headers").getString("Host"))
    }

    @Test
    fun supportsHysteria2WithTlsAndPassword() {
        val profile = ConfigProfile(
            name = "hy2",
            protocol = ConfigProtocol.HYSTERIA2,
            server = "example.com",
            port = 443,
            credential = "secret-password",
            network = "udp",
            security = "tls",
            parameters = mapOf("sni" to "example.com")
        )
        val outbound = JSONObject(SingBoxConfigGenerator.generate(profile).getOrThrow())
            .getJSONArray("outbounds").getJSONObject(0)
        assertEquals("hysteria2", outbound.getString("type"))
        assertEquals("secret-password", outbound.getString("password"))
        assertTrue(outbound.getJSONObject("tls").getBoolean("enabled"))
    }

    @Test
    fun rejectsUnsupportedXhttpInsteadOfGuessing() {
        val result = SingBoxConfigGenerator.generate(vless(
            network = "xhttp",
            security = "tls",
            parameters = mapOf("sni" to "example.com", "path" to "/transport")
        ))
        assertTrue(result.isFailure)
    }

    @Test
    fun doesNotClaimAdultFilteringWithoutRuleSetMapping() {
        val profile = vless(parameters = mapOf("sni" to "example.com", "blockAdult" to "true"))
        val root = JSONObject(SingBoxConfigGenerator.generate(profile).getOrThrow())
        assertFalse(root.getJSONObject("route").has("rules"))
    }
}
