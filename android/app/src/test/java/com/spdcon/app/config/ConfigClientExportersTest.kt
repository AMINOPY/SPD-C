package com.spdcon.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ConfigClientExportersTest {
    private fun profile(protocol: ConfigProtocol, name: String = protocol.name) = ConfigProfile(
        name = name,
        protocol = protocol,
        server = "node.example.com",
        port = when (protocol) {
            ConfigProtocol.SHADOWSOCKS -> 8388
            else -> 443
        },
        credential = when (protocol) {
            ConfigProtocol.SHADOWSOCKS -> "ss-secret"
            ConfigProtocol.HYSTERIA2 -> "hy2-secret"
            else -> "00000000-0000-0000-0000-000000000000"
        },
        network = if (protocol == ConfigProtocol.HYSTERIA2 || protocol == ConfigProtocol.TUIC) "udp" else "tcp",
        security = if (protocol == ConfigProtocol.HYSTERIA2 || protocol == ConfigProtocol.TUIC) "tls" else "none",
        parameters = when (protocol) {
            ConfigProtocol.SHADOWSOCKS -> mapOf("method" to "chacha20-ietf-poly1305")
            ConfigProtocol.TUIC -> mapOf("password" to "tuic-secret")
            else -> emptyMap()
        }
    )

    @Test fun v2rayNgExportsSingleSupportedShareLink() {
        val result = ConfigClientExporters.exportSingle(ConfigExportTarget.V2RAYNG, profile(ConfigProtocol.VLESS)).getOrThrow()
        assertEquals(1, result.profileCount)
        assertTrue(result.text.startsWith("vless://"))
        assertEquals("text/plain; charset=utf-8", result.mimeType)
    }

    @Test fun v2rayNgRejectsProtocolsItsXrayCoreCannotUse() {
        assertFalse(ConfigClientExporters.supports(ConfigExportTarget.V2RAYNG, profile(ConfigProtocol.HYSTERIA2)))
        assertTrue(ConfigClientExporters.exportSingle(ConfigExportTarget.V2RAYNG, profile(ConfigProtocol.HYSTERIA2)).isFailure)
        assertTrue(ConfigClientExporters.exportSingle(ConfigExportTarget.V2RAYNG, profile(ConfigProtocol.TUIC)).isFailure)
    }

    @Test fun hiddifyExportsMultipleProtocolsAsPlainShareLinks() {
        val result = ConfigClientExporters.export(
            ConfigExportTarget.HIDDIFY,
            listOf(profile(ConfigProtocol.VLESS), profile(ConfigProtocol.HYSTERIA2), profile(ConfigProtocol.TUIC))
        ).getOrThrow()
        assertEquals(3, result.profileCount)
        assertTrue(result.text.contains("vless://"))
        assertTrue(result.text.contains("hysteria2://"))
        assertTrue(result.text.contains("tuic://"))
        assertEquals(3, result.text.lines().size)
    }

    @Test fun canEncodeMultiProfileSubscriptionAsBase64() {
        val result = ConfigClientExporters.export(
            ConfigExportTarget.HIDDIFY,
            listOf(profile(ConfigProtocol.VLESS), profile(ConfigProtocol.VMESS)),
            base64Subscription = true
        ).getOrThrow()
        val decoded = String(Base64.getDecoder().decode(result.text), Charsets.UTF_8)
        assertTrue(decoded.startsWith("vless://"))
        assertTrue(decoded.contains("\nvmess://"))
    }

    @Test fun deduplicatesRepeatedProfilesAndRejectsEmptyInput() {
        val one = profile(ConfigProtocol.VLESS)
        assertEquals(1, ConfigClientExporters.export(ConfigExportTarget.HIDDIFY, listOf(one, one)).getOrThrow().profileCount)
        val otherSettings = one.copy(parameters = mapOf("sni" to "different.example.com"))
        assertEquals(2, ConfigClientExporters.export(ConfigExportTarget.HIDDIFY, listOf(one, otherSettings)).getOrThrow().profileCount)
        assertTrue(ConfigClientExporters.export(ConfigExportTarget.HIDDIFY, emptyList()).isFailure)
    }
}
