package com.spdcon.app.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigDiagnosticsTest {
    private fun profile(
        protocol: ConfigProtocol = ConfigProtocol.VLESS,
        network: String = "ws",
        security: String = "tls",
        port: Int = 443,
        parameters: Map<String, String> = mapOf("host" to "edge.example.com", "sni" to "edge.example.com", "path" to "/")
    ) = ConfigProfile(
        name = "test",
        protocol = protocol,
        server = "origin.example.com",
        port = port,
        credential = "test-credential",
        network = network,
        security = security,
        parameters = parameters
    )

    @Test
    fun cdnModeWarnsWhenHostOrSniMissing() {
        val messages = ConfigDiagnostics.inspect(
            profile(parameters = mapOf("path" to "/"), port = 443),
            cdnEnabled = true
        ).map { it.message }
        assertTrue(messages.any { it.contains("Host خالی") })
        assertTrue(messages.any { it.contains("SNI خالی") })
    }

    @Test
    fun cdnModeWarnsAboutUnsupportedTransportAndUnusualPort() {
        val messages = ConfigDiagnostics.inspect(
            profile(network = "tcp", security = "reality", port = 9443, parameters = emptyMap()),
            cdnEnabled = true
        ).map { it.message }
        assertTrue(messages.any { it.contains("VLESS + WS + TLS") })
        assertTrue(messages.any { it.contains("پورت انتخابی") })
    }

    @Test
    fun grpcRequiresServiceNameHint() {
        val messages = ConfigDiagnostics.inspect(profile(network = "grpc", parameters = emptyMap()))
            .map { it.message }
        assertTrue(messages.any { it.contains("نام سرویس gRPC خالی") })
    }

    @Test
    fun insecureTlsIsWarned() {
        val messages = ConfigDiagnostics.inspect(
            profile(parameters = mapOf("allowInsecure" to "1", "sni" to "edge.example.com"))
        ).map { it.message }
        assertTrue(messages.any { it.contains("امنیت را کاهش می‌دهد") })
    }

    @Test
    fun saneTlsWebsocketProfileHasNoWarnings() {
        val diagnostics = ConfigDiagnostics.inspect(profile())
        assertFalse(diagnostics.any { it.level == DiagnosticLevel.WARNING })
    }
}
