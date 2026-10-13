package com.spdcon.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiResistanceAssessorTest {
    private fun report(
        name: String,
        connected: Boolean,
        stages: List<DiagnosticStageResult>,
        diagnosis: ClassifiedConnectionFailure? = null
    ) = ConnectionDiagnosticReport(
        profileName = name,
        stages = stages,
        tunnelConnected = connected,
        tunnelLatencyMs = if (connected) 100 else null,
        coreVersion = "test",
        diagnosis = diagnosis
    )

    @Test
    fun successfulTunnelDoesNotClaimDpiResistance() {
        val current = report("same-profile", true, listOf(
            DiagnosticStageResult("tunnel", DiagnosticStageState.PASS, "success")
        ))
        val result = DpiResistanceAssessor.assess(current)
        assertEquals(DpiEvidenceStatus.TUNNEL_WORKS_ON_CURRENT_NETWORK, result.status)
        assertFalse(result.confirmedDpi)
        assertTrue(result.explanationFa.contains("اثبات"))
    }

    @Test
    fun sameProfilePassingElsewhereIsOnlyPossibleNetworkSpecificInterference() {
        val current = report("same-profile", false, listOf(
            DiagnosticStageResult("tcp", DiagnosticStageState.PASS, "port open"),
            DiagnosticStageResult("tunnel", DiagnosticStageState.FAIL, "handshake failed")
        ))
        val other = report("same-profile", true, listOf(
            DiagnosticStageResult("tunnel", DiagnosticStageState.PASS, "success")
        ))
        val result = DpiResistanceAssessor.assess(current, other)
        assertEquals(DpiEvidenceStatus.POSSIBLE_NETWORK_SPECIFIC_INTERFERENCE, result.status)
        assertFalse(result.confirmedDpi)
        assertTrue(result.explanationFa.contains("فقط نشانه"))
    }

    @Test
    fun unreachableNodeDoesNotMeanDpiDetected() {
        val current = report("profile", false, listOf(
            DiagnosticStageResult("dns", DiagnosticStageState.PASS, "resolved"),
            DiagnosticStageResult("tcp", DiagnosticStageState.FAIL, "timeout"),
            DiagnosticStageResult("tunnel", DiagnosticStageState.FAIL, "timeout")
        ), ConnectionFailureClassifier.classify("connection timed out"))
        val result = DpiResistanceAssessor.assess(current)
        assertEquals(DpiEvidenceStatus.NODE_OR_DNS_UNREACHABLE, result.status)
        assertFalse(result.confirmedDpi)
    }
}
