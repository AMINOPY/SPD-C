package com.spdcon.app.ui

/**
 * Ranks candidates by evidence strength before latency. A low ping alone never
 * means an IP is clean, protocol-compatible, or able to carry a tunnel.
 */
data class ConnectionEvidence(
    val tunnelSucceeded: Boolean? = null,
    val tlsVerified: Boolean? = null,
    val tcpReachable: Boolean? = null,
    val dnsCompatible: Boolean? = null,
    val latencyMs: Long? = null
)

enum class CandidateConfidence(val label: String) {
    TUNNEL_VERIFIED("Tunnel verified"),
    TLS_VERIFIED("TLS verified; tunnel untested"),
    TCP_REACHABLE("TCP reachable; tunnel untested"),
    DNS_COMPATIBLE("DNS compatible only"),
    UNVERIFIED("Not verified")
}

data class RankedConnectionCandidate(
    val evidence: ConnectionEvidence,
    val confidence: CandidateConfidence,
    val score: Int
)

object ConnectionCandidateRanker {
    fun rank(evidence: ConnectionEvidence): RankedConnectionCandidate {
        val confidence = when {
            evidence.tunnelSucceeded == true -> CandidateConfidence.TUNNEL_VERIFIED
            evidence.tlsVerified == true -> CandidateConfidence.TLS_VERIFIED
            evidence.tcpReachable == true -> CandidateConfidence.TCP_REACHABLE
            evidence.dnsCompatible == true -> CandidateConfidence.DNS_COMPATIBLE
            else -> CandidateConfidence.UNVERIFIED
        }
        val base = when (confidence) {
            CandidateConfidence.TUNNEL_VERIFIED -> 1000
            CandidateConfidence.TLS_VERIFIED -> 800
            CandidateConfidence.TCP_REACHABLE -> 600
            CandidateConfidence.DNS_COMPATIBLE -> 400
            CandidateConfidence.UNVERIFIED -> 0
        }
        // Latency is only a tie-breaker inside the evidence tier, never proof of health.
        val latencyPenalty = evidence.latencyMs?.coerceIn(0, 10_000)?.div(100)?.toInt() ?: 99
        return RankedConnectionCandidate(evidence, confidence, base - latencyPenalty)
    }

    fun rankTlsResults(results: List<CleanIpResult>): List<CleanIpResult> =
        results.sortedWith(
            compareByDescending<CleanIpResult> {
                rank(ConnectionEvidence(tlsVerified = it.tlsOk, latencyMs = it.latencyMs)).score
            }.thenBy { it.latencyMs }
        )
}
