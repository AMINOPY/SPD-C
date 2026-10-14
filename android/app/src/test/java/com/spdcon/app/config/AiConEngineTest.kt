package com.spdcon.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiConEngineTest {
    private fun profile(name: String, latencyProtocol: ConfigProtocol = ConfigProtocol.VLESS) = ConfigProfile(
        name = name,
        protocol = latencyProtocol,
        server = "$name.example.com",
        port = 443,
        credential = "00000000-0000-4000-8000-000000000001",
        network = "tcp",
        security = "tls",
        parameters = mapOf("sni" to "$name.example.com")
    )

    private fun report(name: String, connected: Boolean, latency: Long) = ConnectionDiagnosticReport(
        profileName = name,
        stages = listOf(DiagnosticStageResult(
            "tunnel",
            if (connected) DiagnosticStageState.PASS else DiagnosticStageState.FAIL,
            if (connected) "request succeeded" else "request failed",
            latency
        )),
        tunnelConnected = connected,
        tunnelLatencyMs = if (connected) latency else null,
        coreVersion = "test",
        diagnosis = null
    )

    @Test
    fun onlyLiveVerifiedAndBundledRuntimeCompatibleProfilesCanWin() {
        val fastButFailed = profile("failed")
        val slowerWorking = profile("working")
        val result = AiConEngine.rankCandidates(
            listOf(fastButFailed, slowerWorking),
            mapOf(
                "failed" to report("failed", false, 10),
                "working" to report("working", true, 130)
            ),
            AiConNetworkSnapshot("cellular", true, 4000, 1000, null, "4G LTE", null, null, emptyList(), "test")
        )
        assertEquals(listOf("working"), result.map { it.sourceProfile.name })
    }

    @Test
    fun generatedProfileIsNamedAiConAndPreservesRealServerCredentials() {
        val source = profile("my-node")
        val generated = AiConEngine.createAiProfile(source)
        assertEquals("AI_CON", generated.name)
        assertEquals(source.server, generated.server)
        assertEquals(source.port, generated.port)
        assertEquals(source.credential, generated.credential)
        assertTrue(generated.tags.contains("AI_CON"))
        assertFalse(generated.favorite)
    }

    @Test
    fun unknownNetworkDoesNotBlockRankingVerifiedProfiles() {
        val source = profile("verified")
        val ranked = AiConEngine.rankCandidates(
            listOf(source),
            mapOf(source.name to report(source.name, true, 90)),
            AiConNetworkSnapshot("unknown", false, null, null, null, null, null, null, emptyList(), "unknown")
        )
        assertEquals(1, ranked.size)
    }
}
