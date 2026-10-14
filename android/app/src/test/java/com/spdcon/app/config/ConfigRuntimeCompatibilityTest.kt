package com.spdcon.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigRuntimeCompatibilityTest {
    private fun profile(protocol: ConfigProtocol, network: String = "tcp") = ConfigProfile(
        name = "test",
        protocol = protocol,
        server = "node.example.com",
        port = 443,
        credential = if (protocol == ConfigProtocol.HYSTERIA2) "hy2-secret" else "00000000-0000-0000-0000-000000000000",
        network = network,
        security = if (protocol == ConfigProtocol.HYSTERIA2 || protocol == ConfigProtocol.TUIC) "tls" else "none",
        parameters = if (protocol == ConfigProtocol.TUIC) mapOf("password" to "tuic-secret") else emptyMap()
    )

    @Test fun supportedXrayProtocolsPassTheRuntimeGate() {
        listOf(ConfigProtocol.VLESS, ConfigProtocol.VMESS, ConfigProtocol.TROJAN, ConfigProtocol.SHADOWSOCKS)
            .forEach { protocol ->
                assertTrue(protocol.toString(), ConfigRuntimeCompatibility.inspectForBundledRuntime(profile(protocol)).supported)
            }
    }

    @Test fun hysteriaAndTuicAreNotMisreportedAsRunnable() {
        val hysteria = ConfigRuntimeCompatibility.inspectForBundledRuntime(profile(ConfigProtocol.HYSTERIA2, "udp"))
        val tuic = ConfigRuntimeCompatibility.inspectForBundledRuntime(profile(ConfigProtocol.TUIC, "udp"))
        assertFalse(hysteria.supported)
        assertFalse(tuic.supported)
        assertEquals(ConfigExportTarget.HIDDIFY, hysteria.fallbackTarget)
        assertEquals(ConfigExportTarget.HIDDIFY, tuic.fallbackTarget)
        assertTrue(hysteria.message.contains("cannot run it"))
    }

    @Test fun invalidProfilesFailBeforeRuntimeStartup() {
        val invalid = profile(ConfigProtocol.VLESS).copy(port = 0)
        val result = ConfigRuntimeCompatibility.inspectForBundledRuntime(invalid)
        assertFalse(result.supported)
        assertTrue(result.message.contains("Port must be between"))
    }
}
