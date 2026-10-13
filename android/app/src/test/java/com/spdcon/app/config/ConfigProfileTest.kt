package com.spdcon.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigProfileTest {
    @Test
    fun validProfileHasNoValidationErrors() {
        val profile = ConfigProfile(
            name = "Home",
            protocol = ConfigProtocol.VLESS,
            server = "example.com",
            port = 443,
            credential = "00000000-0000-0000-0000-000000000000"
        )
        assertTrue(profile.validate().isEmpty())
    }

    @Test
    fun rejectsUrlInsteadOfBareServerAddress() {
        val profile = ConfigProfile(
            name = "Injected URL",
            protocol = ConfigProtocol.VLESS,
            server = "https://example.com/path?x=1",
            port = 443,
            credential = "00000000-0000-0000-0000-000000000000"
        )
        assertTrue(profile.validate().any { it.startsWith("Server must be a hostname") })
    }

    @Test
    fun acceptsBracketedIpv6Server() {
        val profile = ConfigProfile(
            name = "IPv6",
            protocol = ConfigProtocol.VLESS,
            server = "[2001:db8::1]",
            port = 443,
            credential = "00000000-0000-0000-0000-000000000000"
        )
        assertTrue(profile.validate().isEmpty())
    }

    @Test
    fun invalidPortIsReported() {
        val profile = ConfigProfile(
            name = "Bad",
            protocol = ConfigProtocol.TROJAN,
            server = "example.com",
            port = 70000,
            credential = "secret"
        )
        assertEquals(listOf("Port must be between 1 and 65535"), profile.validate())
    }
    @Test
    fun rejectsUnsupportedTransportAndSecurityCombinations() {
        val unsupportedTransport = ConfigProfile(
            name = "Bad transport",
            protocol = ConfigProtocol.VLESS,
            server = "example.com",
            port = 443,
            credential = "00000000-0000-0000-0000-000000000000",
            network = "made-up"
        )
        assertTrue(unsupportedTransport.validate().any { it.contains("Unsupported transport") })

        val hysteriaWithoutUdp = ConfigProfile(
            name = "Bad Hysteria transport",
            protocol = ConfigProtocol.HYSTERIA2,
            server = "example.com",
            port = 443,
            credential = "secret",
            network = "tcp",
            security = "tls"
        )
        assertTrue(hysteriaWithoutUdp.validate().any { it.contains("requires UDP") })

        val shadowsocksWithTls = ConfigProfile(
            name = "Bad Shadowsocks security",
            protocol = ConfigProtocol.SHADOWSOCKS,
            server = "example.com",
            port = 8388,
            credential = "secret",
            security = "tls",
            parameters = mapOf("method" to "aes-128-gcm")
        )
        assertTrue(shadowsocksWithTls.validate().any { it.contains("Shadowsocks profiles") })
    }

    @Test
    fun rejectsUnsafeTransportPathAndHostHeader() {
        val unsafePath = ConfigProfile(
            name = "Unsafe path",
            protocol = ConfigProtocol.VLESS,
            server = "example.com",
            port = 443,
            credential = "00000000-0000-0000-0000-000000000000",
            network = "ws",
            parameters = mapOf("path" to "/../escape", "host" to "cdn.example.com")
        )
        assertTrue(unsafePath.validate().any { it.contains("Transport path") })

        val unsafeHost = unsafePath.copy(
            parameters = mapOf("path" to "/ws", "host" to "cdn.example.com\r\nX-Injected: yes")
        )
        assertTrue(unsafeHost.validate().any { it.contains("Transport Host") })
    }

    @Test
    fun rejectsUnsafeTlsAndTransportMetadata() {
        val base = ConfigProfile(
            name = "Unsafe metadata",
            protocol = ConfigProtocol.VLESS,
            server = "example.com",
            port = 443,
            credential = "00000000-0000-0000-0000-000000000000",
            network = "ws",
            security = "tls",
            parameters = mapOf("sni" to "https://evil.example/path", "host" to "cdn.example.com")
        )
        assertTrue(base.validate().any { it.contains("SNI/serverName") })

        val badAlpn = base.copy(parameters = mapOf("sni" to "cdn.example.com", "alpn" to "h2, bad token"))
        assertTrue(badAlpn.validate().any { it.contains("ALPN") })

        val badHeader = base.copy(network = "tcp", parameters = mapOf("headerType" to "fake"))
        assertTrue(badHeader.validate().any { it.contains("TCP header type") })

        val badGrpc = base.copy(network = "grpc", parameters = mapOf("serviceName" to "svc/name"))
        assertTrue(badGrpc.validate().any { it.contains("gRPC service name") })
    }

}
