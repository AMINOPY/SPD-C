package com.spdcon.app.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayClientConfigGeneratorTest {
    private fun profile(
        protocol: ConfigProtocol = ConfigProtocol.VLESS,
        security: String = "tls",
        network: String = "ws",
        parameters: Map<String, String> = mapOf("sni" to "cdn.example.com", "host" to "cdn.example.com", "path" to "/tunnel")
    ) = ConfigProfile(
        name = "test",
        protocol = protocol,
        server = "node.example.com",
        port = 443,
        credential = "test-credential",
        network = network,
        security = security,
        parameters = parameters
    )

    @Test fun vlessWebSocketTlsBuildsFullDocument() {
        val root = JSONObject(XrayClientConfigGenerator.generate(profile()).getOrThrow())
        val outbound = root.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vless", outbound.getString("protocol"))
        assertEquals("node.example.com", outbound.getJSONObject("settings").getJSONArray("vnext")
            .getJSONObject(0).getString("address"))
        assertEquals("/tunnel", outbound.getJSONObject("streamSettings").getJSONObject("wsSettings").getString("path"))
        assertEquals("cdn.example.com", outbound.getJSONObject("streamSettings").getJSONObject("tlsSettings").getString("serverName"))
        assertTrue(root.has("routing"))
        assertTrue(root.has("inbounds"))
        assertEquals(2, root.getJSONArray("inbounds").length())
    }

    @Test fun trojanUsesPasswordAndTls() {
        val root = JSONObject(XrayClientConfigGenerator.generate(
            profile(ConfigProtocol.TROJAN, "tls", "tcp", mapOf("sni" to "node.example.com"))
        ).getOrThrow())
        val outbound = root.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("trojan", outbound.getString("protocol"))
        assertEquals("test-credential", outbound.getJSONObject("settings").getJSONArray("servers")
            .getJSONObject(0).getString("password"))
    }

    @Test fun shadowsocksUsesEncryptionMethod() {
        val root = JSONObject(XrayClientConfigGenerator.generate(
            profile(ConfigProtocol.SHADOWSOCKS, "none", "tcp", mapOf("method" to "aes-128-gcm"))
        ).getOrThrow())
        val outbound = root.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("aes-128-gcm", outbound.getJSONObject("settings").getJSONArray("servers")
            .getJSONObject(0).getString("method"))
        assertFalse(outbound.has("streamSettings"))
    }

    @Test fun adultFilterAddsGeositeBlockRule() {
        val root = JSONObject(XrayClientConfigGenerator.generate(
            profile(parameters = mapOf("blockAdult" to "true"))
        ).getOrThrow())
        val rules = root.getJSONObject("routing").getJSONArray("rules")
        assertEquals("geosite:category-porn", rules.getJSONObject(rules.length() - 1)
            .getJSONArray("domain").getString(0))
    }

    @Test fun xhttpExportsPathAndModeAndRejectsUnsafeValues() {
        val good = JSONObject(XrayClientConfigGenerator.generate(
            profile(network = "xhttp", parameters = mapOf("sni" to "cdn.example.com", "path" to "/de/ws", "mode" to "stream-up"))
        ).getOrThrow())
        val stream = good.getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings")
        assertEquals("/de/ws", stream.getJSONObject("xhttpSettings").getString("path"))
        assertEquals("stream-up", stream.getJSONObject("xhttpSettings").getString("mode"))

        assertTrue(XrayClientConfigGenerator.generate(
            profile(network = "xhttp", parameters = mapOf("path" to "/../escape", "mode" to "stream-up"))
        ).isFailure)
        assertTrue(XrayClientConfigGenerator.generate(
            profile(network = "xhttp", parameters = mapOf("path" to "/ok", "mode" to "not-a-mode"))
        ).isFailure)
    }

    @Test fun rejectsProtocolsNotSupportedByXrayCore() {
        assertTrue(XrayClientConfigGenerator.generate(
            profile(ConfigProtocol.HYSTERIA2, "tls", "udp")
        ).isFailure)
        assertFalse(XrayClientConfigGenerator.supports(profile(ConfigProtocol.TUIC, "tls", "udp")))
    }
}
