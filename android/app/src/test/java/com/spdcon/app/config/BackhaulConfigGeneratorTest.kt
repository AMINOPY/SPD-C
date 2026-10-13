package com.spdcon.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackhaulConfigGeneratorTest {
    @Test
    fun serverTemplateContainsMatchingTransportAndForwardPorts() {
        val settings = BackhaulSettings(
            role = BackhaulRole.SERVER,
            transport = "wsmux",
            bindPort = 8080,
            token = "0123456789abcdef",
            ports = listOf("443", "8443")
        )
        val output = BackhaulConfigGenerator.generate(settings).getOrThrow()
        assertTrue(output.contains("transport = \"wsmux\""))
        assertTrue(output.contains("bind_addr = \":8080\""))
        assertTrue(output.contains("\"443\""))
        assertTrue(output.contains("\"8443\""))
    }

    @Test
    fun clientCloudflareTemplateRequiresDomainAndWebsocketTransport() {
        val settings = BackhaulSettings(
            role = BackhaulRole.CLIENT,
            transport = "wsmux",
            remoteHost = "tunnel.example.com",
            remotePort = 8080,
            token = "0123456789abcdef",
            cloudflareProxy = true
        )
        val output = BackhaulConfigGenerator.generate(settings).getOrThrow()
        assertTrue(output.contains("remote_addr = \"tunnel.example.com:8080\""))
        assertTrue(output.contains("Cloudflare proxy is enabled"))
    }

    @Test
    fun shortTokenIsRejected() {
        val settings = BackhaulSettings(
            role = BackhaulRole.SERVER,
            transport = "ws",
            bindPort = 8080,
            token = "short",
            ports = listOf("443")
        )
        assertEquals(false, BackhaulConfigGenerator.generate(settings).isSuccess)
    }

    @Test
    fun unsupportedExperimentalTransportIsRejected() {
        val settings = BackhaulSettings(
            role = BackhaulRole.SERVER,
            transport = "uwsmux",
            bindPort = 8080,
            token = "0123456789abcdef",
            ports = listOf("443")
        )
        assertTrue(BackhaulConfigGenerator.validate(settings).any { it.contains("Unsupported Backhaul transport") })
    }
}
