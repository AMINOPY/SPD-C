package com.spdcon.app.ui

import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.max

data class CleanIpResult(val ip: String, val latencyMs: Long, val tlsOk: Boolean, val detail: String)

/** Bounded TLS reachability scanner for supplied IPv4 candidates; no port sweeps. */
object CleanIpScanner {
    const val MAX_CANDIDATES = 256
    val SUPPORTED_TLS_PORTS = setOf(443, 2053, 2083, 2087, 2096, 8443)
    val DEFAULT_CLOUDFLARE_CANDIDATES = listOf(
        "104.16.0.1", "104.17.0.1", "104.18.0.1", "104.19.0.1",
        "104.20.0.1", "104.21.0.1", "104.22.0.1", "104.23.0.1",
        "104.24.0.1", "104.25.0.1", "172.64.0.1", "188.114.96.1"
    )

    fun parseCandidates(raw: String, maxCandidates: Int = MAX_CANDIDATES): List<String> {
        require(maxCandidates in 1..MAX_CANDIDATES) { "Candidate limit must be between 1 and $MAX_CANDIDATES" }
        val result = linkedSetOf<String>()
        val tokens = raw.lineSequence().map { it.substringBefore("#").trim() }
            .flatMap { it.split(Regex("[,;\\s]+")).asSequence() }.filter { it.isNotBlank() }
        for (token in tokens) {
            if (result.size >= maxCandidates) break
            when {
                token.contains('/') -> {
                    val p = token.split('/', limit = 2)
                    val base = parseIpv4(p[0]) ?: throw IllegalArgumentException("Invalid IPv4 CIDR: $token")
                    val prefix = p[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid CIDR prefix: $token")
                    require(prefix in 16..32) { "CIDR ranges broader than /16 are not allowed: $token" }
                    val count = 1L shl (32 - prefix)
                    val mask = (0xFFFF_FFFFL shl (32 - prefix)) and 0xFFFF_FFFFL
                    val network = base and mask
                    val step = max(1L, (count + maxCandidates - 1) / maxCandidates)
                    var offset = 0L
                    while (offset < count && result.size < maxCandidates) {
                        result += ipv4ToString(network + offset)
                        offset += step
                    }
                }
                token.contains('-') -> {
                    val p = token.split('-', limit = 2)
                    val start = parseIpv4(p[0]) ?: throw IllegalArgumentException("Invalid IPv4 range: $token")
                    val end = parseIpv4(p[1]) ?: throw IllegalArgumentException("Invalid IPv4 range: $token")
                    require(start <= end) { "IPv4 range start must not exceed end: $token" }
                    val count = end - start + 1
                    val step = max(1L, (count + maxCandidates - 1) / maxCandidates)
                    var offset = 0L
                    while (offset < count && result.size < maxCandidates) {
                        result += ipv4ToString(start + offset)
                        offset += step
                    }
                }
                else -> result += ipv4ToString(parseIpv4(token)
                    ?: throw IllegalArgumentException("Invalid IPv4 address: $token"))
            }
        }
        return result.take(maxCandidates)
    }

    fun isValidSni(value: String): Boolean {
        if (value.isBlank() || value.length > 253 ||
            value.any { it.isWhitespace() || it.isISOControl() || it in "/?#@:" } ||
            value.startsWith(".") || value.endsWith(".") || value.contains("..")) return false
        return value.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 && label.first().isLetterOrDigit() &&
                label.last().isLetterOrDigit() && label.all { it.isLetterOrDigit() || it == '-' }
        }
    }

    fun scan(ips: List<String>, port: Int, sni: String, timeoutMs: Int = 1800, workers: Int = 8): List<CleanIpResult> {
        require(port in SUPPORTED_TLS_PORTS) { "Choose a supported HTTPS port: \${SUPPORTED_TLS_PORTS.sorted().joinToString()}" }
        require(isValidSni(sni)) { "Enter the hostname of your actual TLS/Cloudflare domain" }
        require(timeoutMs in 500..5000) { "Timeout must be between 500 and 5000 ms" }
        val candidates = ips.distinct().take(MAX_CANDIDATES)
        require(candidates.isNotEmpty()) { "No IP candidates were provided" }
        require(candidates.all { parseIpv4(it) != null }) { "Candidates must be IPv4 addresses" }
        val pool = Executors.newFixedThreadPool(workers.coerceIn(1, 16))
        return try {
            pool.invokeAll(candidates.map { ip -> Callable { probe(ip, port, sni, timeoutMs) } })
                .map { it.get() }
                .sortedWith(compareBy<CleanIpResult> { !it.tlsOk }.thenBy { it.latencyMs })
        } finally { pool.shutdownNow() }
    }

    private fun probe(ip: String, port: Int, sni: String, timeoutMs: Int): CleanIpResult {
        val started = System.nanoTime()
        var tcpLatency = -1L
        return try {
            val raw = Socket()
            raw.connect(InetSocketAddress(ip, port), timeoutMs)
            raw.soTimeout = timeoutMs
            tcpLatency = elapsed(started)
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val ssl = factory.createSocket(raw, sni, port, true) as SSLSocket
            ssl.soTimeout = timeoutMs
            val params = ssl.sslParameters
            params.serverNames = listOf(SNIHostName(sni))
            params.endpointIdentificationAlgorithm = "HTTPS"
            ssl.sslParameters = params
            ssl.startHandshake()
            val latency = elapsed(started)
            ssl.close()
            CleanIpResult(ip, latency, true, "TLS verified")
        } catch (e: Exception) {
            CleanIpResult(ip, if (tcpLatency >= 0) tcpLatency else elapsed(started), false, e.javaClass.simpleName)
        }
    }

    private fun elapsed(start: Long) = (System.nanoTime() - start) / 1_000_000L

    private fun parseIpv4(value: String): Long? {
        val parts = value.trim().split('.')
        if (parts.size != 4) return null
        var out = 0L
        for (part in parts) {
            if (part.isEmpty() || part.length > 3 || part.any { !it.isDigit() }) return null
            val n = part.toIntOrNull() ?: return null
            if (n !in 0..255) return null
            out = (out shl 8) or n.toLong()
        }
        return out
    }

    private fun ipv4ToString(value: Long) =
        listOf(24, 16, 8, 0).joinToString(".") { ((value shr it) and 255).toString() }
}
