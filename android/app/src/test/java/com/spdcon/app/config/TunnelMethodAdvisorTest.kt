package com.spdcon.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelMethodAdvisorTest {
    private fun profile(
        name: String,
        protocol: ConfigProtocol = ConfigProtocol.VLESS,
        network: String = "tcp",
        security: String = "tls",
        parameters: Map<String, String> = emptyMap()
    ) = ConfigProfile(
        name = name,
        protocol = protocol,
        server = "$name.example.com",
        port = 443,
        credential = "00000000-0000-4000-8000-000000000001",
        network = network,
        security = security,
        parameters = parameters
    )

    @Test
    fun cloudflareGoalRanksOnlyWsTlsMethodHighly() {
        val ws = profile("ws", network = "ws", parameters = mapOf(
            "host" to "cdn.example.com", "sni" to "cdn.example.com", "path" to "/proxy"
        ))
        val tcp = profile("tcp")
        val ranked = TunnelMethodAdvisor.rankSavedProfiles(
            listOf(tcp, ws), TunnelSelectionGoal.CLOUDFLARE_CDN
        )
        assertEquals("ws", ranked.first().profileName)
        assertEquals(TunnelMethod.VLESS_WEBSOCKET_TLS, ranked.first().method)
    }

    @Test
    fun realityMethodRequiresServerValuesFromProfile() {
        val reality = profile("reality", security = "reality")
        val assessment = TunnelMethodAdvisor.assess(reality, TunnelSelectionGoal.RESTRICTED_NETWORK)
        assertFalse(assessment.selectableInBundledApp)
        assertEquals(TunnelSelectionConfidence.CONFIGURATION_INCOMPLETE, assessment.confidence)
        assertTrue(assessment.requirementsFa.any { it.contains("REALITY") })
    }

    @Test
    fun udpProtocolIsNotSelectableInBundledXrayRuntime() {
        val h2 = profile("h2", ConfigProtocol.HYSTERIA2, "udp", "tls")
        val assessment = TunnelMethodAdvisor.assess(h2, TunnelSelectionGoal.UDP_SPEED)
        assertFalse(assessment.selectableInBundledApp)
        assertEquals(TunnelSelectionConfidence.EXTERNAL_RUNTIME_REQUIRED, assessment.confidence)
    }

    @Test
    fun successfulRealProbeIsLabelledCurrentNetworkOnly() {
        val p = profile("vless")
        val report = ConnectionDiagnosticReport(
            profileName = p.name,
            stages = listOf(DiagnosticStageResult("tunnel", DiagnosticStageState.PASS, "request succeeded", 100)),
            tunnelConnected = true,
            tunnelLatencyMs = 100,
            coreVersion = "test",
            diagnosis = null
        )
        val assessment = TunnelMethodAdvisor.assess(p, report = report)
        assertEquals(TunnelSelectionConfidence.LIVE_VERIFIED, assessment.confidence)
        assertTrue(assessment.summaryFa.contains("شبکهٔ فعلی"))
        assertFalse(assessment.summaryFa.contains("تضمین می‌شود"))
    }
}
