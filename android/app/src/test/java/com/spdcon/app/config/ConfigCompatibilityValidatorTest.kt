package com.spdcon.app.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigCompatibilityValidatorTest {
    private fun vless(network: String, security: String, parameters: Map<String, String> = emptyMap()) =
        ConfigProfile(
            name = "compatibility test",
            protocol = ConfigProtocol.VLESS,
            server = "example.com",
            port = 443,
            credential = "00000000-0000-4000-8000-000000000001",
            network = network,
            security = security,
            parameters = parameters
        )

    @Test
    fun rejectsRealityOverWebSocket() {
        val errors = vless(
            network = "ws",
            security = "reality",
            parameters = mapOf("sni" to "example.com", "pbk" to "test-key", "sid" to "aabb")
        ).validate()
        assertTrue(errors.any { it.contains("REALITY") && it.contains("WS") || it.contains("REALITY") && it.contains("transport") })
    }

    @Test
    fun acceptsVlessRealityRawWithVisionWhenRequiredFieldsExist() {
        val errors = vless(
            network = "tcp",
            security = "reality",
            parameters = mapOf(
                "sni" to "example.com",
                "pbk" to "test-key",
                "sid" to "aabb",
                "flow" to "xtls-rprx-vision"
            )
        ).validate()
        assertFalse(errors.any { it.contains("flow") || it.contains("REALITY") })
    }

    @Test
    fun rejectsVisionFlowOnTlsWebsocket() {
        val errors = vless(
            network = "ws",
            security = "tls",
            parameters = mapOf("flow" to "xtls-rprx-vision")
        ).validate()
        assertTrue(errors.any { it.contains("Vision") || it.contains("flow") })
    }

    @Test
    fun rejectsUnsafeFingerprintForReality() {
        val errors = vless(
            network = "tcp",
            security = "reality",
            parameters = mapOf(
                "sni" to "example.com",
                "pbk" to "test-key",
                "sid" to "",
                "fp" to "unsafe"
            )
        ).validate()
        assertTrue(errors.any { it.contains("unsafe") })
    }

    @Test
    fun rejectsInvalidXhttpMode() {
        val errors = vless(
            network = "xhttp",
            security = "tls",
            parameters = mapOf("path" to "/transport", "mode" to "not-a-mode")
        ).validate()
        assertTrue(errors.any { it.contains("XHTTP mode") })
    }
}
