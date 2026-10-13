package com.spdcon.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionCandidateRankerTest {
    @Test
    fun successfulTunnelRanksAboveTlsOnlyEvenWhenSlower() {
        val tunnel = ConnectionCandidateRanker.rank(
            ConnectionEvidence(tunnelSucceeded = true, tlsVerified = true, latencyMs = 500)
        )
        val tlsOnly = ConnectionCandidateRanker.rank(
            ConnectionEvidence(tlsVerified = true, latencyMs = 20)
        )
        assertTrue(tunnel.score > tlsOnly.score)
        assertEquals(CandidateConfidence.TUNNEL_VERIFIED, tunnel.confidence)
    }

    @Test
    fun tlsEvidenceRanksAboveDnsOnlyRegardlessOfLatency() {
        val tls = ConnectionCandidateRanker.rank(ConnectionEvidence(tlsVerified = true, latencyMs = 3000))
        val dns = ConnectionCandidateRanker.rank(ConnectionEvidence(dnsCompatible = true, latencyMs = 1))
        assertTrue(tls.score > dns.score)
    }

    @Test
    fun latencyAloneNeverMarksCandidateVerified() {
        val result = ConnectionCandidateRanker.rank(ConnectionEvidence(latencyMs = 1))
        assertEquals(CandidateConfidence.UNVERIFIED, result.confidence)
        assertEquals(-99, result.score)
    }

    @Test
    fun tlsScannerResultsPrioritizeVerifiedHandshakeThenLatency() {
        val results = listOf(
            CleanIpResult("192.0.2.2", 10, false, "TLS failed"),
            CleanIpResult("192.0.2.3", 80, true, "TLS verified"),
            CleanIpResult("192.0.2.4", 20, true, "TLS verified")
        )
        val ranked = ConnectionCandidateRanker.rankTlsResults(results)
        assertEquals(listOf("192.0.2.4", "192.0.2.3", "192.0.2.2"), ranked.map { it.ip })
    }
}
