package com.spdcon.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionStrategySelectorTest {
    private fun profile(
        name: String,
        protocol: ConfigProtocol = ConfigProtocol.VLESS,
        network: String = "tcp",
        security: String = "tls",
        favorite: Boolean = false
    ) = ConfigProfile(
        name = name,
        protocol = protocol,
        server = "$name.example.com",
        port = 443,
        credential = "00000000-0000-4000-8000-000000000001",
        network = network,
        security = security,
        parameters = if (security == "reality") mapOf("sni" to "example.com", "pbk" to "publickey", "sid" to "") else emptyMap(),
        favorite = favorite
    )

    private fun report(name: String, connected: Boolean, latency: Long) = ConnectionDiagnosticReport(
        profileName = name,
        stages = emptyList(),
        tunnelConnected = connected,
        tunnelLatencyMs = latency,
        coreVersion = "test",
        diagnosis = null
    )

    @Test
    fun realTunnelSuccessOutranksFasterUntestedProfile() {
        val slowVerified = profile("verified")
        val fastUntested = profile("untested")
        val ranked = ConnectionStrategySelector.rank(
            listOf(fastUntested, slowVerified),
            mapOf("verified" to report("verified", true, 800))
        )
        assertEquals("verified", ranked.first().profile.name)
        assertTrue(ranked.first().tunnelVerified)
        assertFalse(ranked.last().tunnelVerified)
    }

    @Test
    fun unsupportedBundledRuntimeIsNotSelectedForLiveTesting() {
        val xray = profile("xray")
        val udp = profile("udp", ConfigProtocol.HYSTERIA2, "udp", "tls")
        val candidates = ConnectionStrategySelector.rankForLiveTesting(listOf(udp, xray), limit = 5)
        assertEquals(listOf("xray"), candidates.map { it.name })
    }

    @Test
    fun cloudflareGoalPrefersMatchingVlessWebsocketTlsProfile() {
        val cdn = profile("cdn", network = "ws")
        val mismatched = profile("tcp")
        val ranked = ConnectionStrategySelector.rank(
            listOf(mismatched, cdn.copy(parameters = mapOf("host" to "edge.example.com", "sni" to "edge.example.com"))),
            goal = SetupGoal.CLOUDFLARE_CDN
        )
        assertEquals("cdn", ranked.first().profile.name)
    }

    @Test
    fun failedProbeDoesNotBecomeTunnelVerified() {
        val candidate = profile("failed")
        val ranked = ConnectionStrategySelector.rank(
            listOf(candidate),
            mapOf("failed" to report("failed", false, 120))
        ).single()
        assertFalse(ranked.tunnelVerified)
        assertTrue(ranked.reasonFa.contains("تأیید نشد"))
    }
}
