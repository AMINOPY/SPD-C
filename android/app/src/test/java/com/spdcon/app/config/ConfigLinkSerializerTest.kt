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
    fun serializesAdvancedTransportAndTlsFields() {
        val profile = ConfigProfile(
            name = "WebSocket TLS",
            protocol = ConfigProtocol.VLESS,
            server = "edge.example.com",
            port = 443,
            credential = "00000000-0000-0000-0000-000000000000",
            network = "ws",
            security = "tls",
            parameters = mapOf(
                "host" to "cdn.example.com",
                "path" to "/tunnel",
                "sni" to "cdn.example.com",
                "alpn" to "h2,http/1.1",
                "fp" to "chrome"
            )
        )
        val serialized = ConfigLinkSerializer.serialize(profile)
        assertTrue(serialized.isSuccess)
        val link = serialized.getOrThrow()
        assertTrue(link.contains("type=ws"))
        assertTrue(link.contains("security=tls"))
        assertTrue(link.contains("host=cdn.example.com"))
        assertTrue(link.contains("path=%2Ftunnel"))
        assertTrue(link.contains("sni=cdn.example.com"))
        assertTrue(link.contains("alpn=h2%2Chttp%2F1.1"))
    }

    @Test
    fun serializesVmessTlsAndTransportMetadata() {
        val profile = ConfigProfile(
            name = "VMess WS",
            protocol = ConfigProtocol.VMESS,
            server = "edge.example.com",
            port = 443,
            credential = "00000000-0000-0000-0000-000000000000",
            network = "ws",
            security = "tls",
            parameters = mapOf(
                "host" to "cdn.example.com",
                "path" to "/tunnel",
                "sni" to "cdn.example.com",
                "alpn" to "h2,http/1.1",
                "fp" to "chrome"
            )
        )
        val link = ConfigLinkSerializer.serialize(profile).getOrThrow()
        val payload = link.removePrefix("vmess://")
        val json = org.json.JSONObject(String(java.util.Base64.getDecoder().decode(payload)))
        assertEquals("cdn.example.com", json.getString("sni"))
        assertEquals("h2,http/1.1", json.getString("alpn"))
        assertEquals("chrome", json.getString("fp"))
        assertEquals("/tunnel", json.getString("path"))
    }

    @Test
    fun serializesAndParsesHysteria2Link() {
        val profile = ConfigProfile(
            name = "Hysteria test",
            protocol = ConfigProtocol.HYSTERIA2,
            server = "edge.example.com",
            port = 443,
            credential = "hy2-secret",
            network = "udp",
            security = "tls",
            parameters = mapOf(
                "sni" to "cdn.example.com",
                "obfs" to "salamander",
                "obfs-password" to "obfs-secret"
            )
        )
        val link = ConfigLinkSerializer.serialize(profile).getOrThrow()
        assertTrue(link.startsWith("hysteria2://hy2-secret@edge.example.com:443/"))
        val parsed = ConfigLinkParser.parse(link).getOrThrow()
        assertEquals(ConfigProtocol.HYSTERIA2, parsed.protocol)
        assertEquals("hy2-secret", parsed.credential)
        assertEquals("salamander", parsed.parameters["obfs"])
        assertEquals("obfs-secret", parsed.parameters["obfs-password"])
    }

    @Test
    fun serializesAndParsesTuicLinkWithSeparatePassword() {
        val profile = ConfigProfile(
            name = "TUIC test",
            protocol = ConfigProtocol.TUIC,
            server = "edge.example.com",
            port = 443,
            credential = "00000000-0000-0000-0000-000000000000",
            network = "udp",
            security = "tls",
            parameters = mapOf(
                "password" to "tuic-secret",
                "sni" to "cdn.example.com",
                "alpn" to "h3",
                "congestion_control" to "bbr",
                "udp_relay_mode" to "native"
            )
        )
        val link = ConfigLinkSerializer.serialize(profile).getOrThrow()
        assertTrue(link.startsWith("tuic://00000000-0000-0000-0000-000000000000:tuic-secret@"))
        val parsed = ConfigLinkParser.parse(link).getOrThrow()
        assertEquals(ConfigProtocol.TUIC, parsed.protocol)
        assertEquals("00000000-0000-0000-0000-000000000000", parsed.credential)
        assertEquals("tuic-secret", parsed.parameters["password"])
        assertEquals("h3", parsed.parameters["alpn"])
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
    @Test
    fun parsesSip002ShadowsocksUserInfoAndPreservesPluginQuery() {
        val userInfo = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("aes-128-gcm:secret".toByteArray())
        val input = "ss://$userInfo@example.com:8388?plugin=obfs-local%3Bobfs%3Dhttp#SIP002"
        val parsed = ConfigLinkParser.parse(input)
        assertTrue(parsed.isSuccess)
        assertEquals("secret", parsed.getOrThrow().credential)
        assertEquals("obfs-local;obfs=http", parsed.getOrThrow().parameters["plugin"])
        val serialized = ConfigLinkSerializer.serialize(parsed.getOrThrow())
        assertTrue(serialized.isSuccess)
        assertTrue(serialized.getOrThrow().contains("plugin=obfs-local%3Bobfs%3Dhttp"))
    }

    @Test
    fun preservesPlusInVlessCredentialAndParsesQuery() {
        val parsed = ConfigLinkParser.parse(
            "vless://abc+def@example.com:443?type=tcp&security=none#Plus%20Test"
        )
        assertTrue(parsed.isSuccess)
        assertEquals("abc+def", parsed.getOrThrow().credential)
        assertEquals("Plus Test", parsed.getOrThrow().name)
    }

    @Test
    fun parsesUrlSafeUnpaddedVmessPayload() {
        val json = org.json.JSONObject()
            .put("v", "2")
            .put("ps", "VMess test")
            .put("add", "example.com")
            .put("port", "443")
            .put("id", "00000000-0000-0000-0000-000000000000")
            .put("net", "ws")
            .put("tls", "tls")
            .put("sni", "cdn.example.com")
            .put("alpn", "h2,http/1.1")
            .put("fp", "chrome")
            .toString()
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toByteArray())
        val parsed = ConfigLinkParser.parse("vmess://$encoded")
        assertTrue(parsed.isSuccess)
        assertEquals("example.com", parsed.getOrThrow().server)
        assertEquals("ws", parsed.getOrThrow().network)
        assertEquals("tls", parsed.getOrThrow().security)
        assertEquals("cdn.example.com", parsed.getOrThrow().parameters["sni"])
        assertEquals("h2,http/1.1", parsed.getOrThrow().parameters["alpn"])
        assertEquals("chrome", parsed.getOrThrow().parameters["fp"])
    }

    @Test
    fun rejectsMalformedAndOversizedInputs() {
        assertTrue(ConfigLinkParser.parse("vless://").isFailure)
        assertTrue(ConfigLinkParser.parse("vless://" + "x".repeat(33_000)).isFailure)
    }

}
