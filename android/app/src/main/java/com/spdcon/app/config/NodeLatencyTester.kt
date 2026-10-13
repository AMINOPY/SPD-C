package com.spdcon.app.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis

data class NodeProbeResult(
    val name: String,
    val server: String,
    val port: Int,
    val latencyMs: Long?,
    val detail: String
) {
    val reachable: Boolean get() = latencyMs != null
}

/**
 * Measures TCP connect latency only. It does not authenticate a VPN protocol,
 * verify that a tunnel works, measure throughput, or identify a datacenter.
 */
object NodeLatencyTester {
    suspend fun probeAll(profiles: List<ConfigProfile>, timeoutMs: Int = 1500): List<NodeProbeResult> =
        coroutineScope {
            profiles.distinctBy { "${it.server.lowercase()}:${it.port}:${it.protocol}" }
                .take(30)
                .map { profile ->
                    async(Dispatchers.IO) { probe(profile, timeoutMs) }
                }
                .awaitAll()
                .sortedWith(compareBy<NodeProbeResult> { it.latencyMs == null }.thenBy { it.latencyMs ?: Long.MAX_VALUE })
        }

    suspend fun probe(profile: ConfigProfile, timeoutMs: Int = 1500): NodeProbeResult = withContext(Dispatchers.IO) {
        if (profile.protocol == ConfigProtocol.HYSTERIA2 || profile.protocol == ConfigProtocol.TUIC) {
            return@withContext NodeProbeResult(profile.name, profile.server, profile.port, null, "UDP protocol: TCP probe skipped")
        }
        var latency: Long? = null
        val detail = runCatching {
            val elapsed = measureTimeMillis {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(profile.server, profile.port), timeoutMs)
                }
            }
            latency = elapsed
            "TCP port reachable"
        }.getOrElse { error ->
            when (error) {
                is java.net.SocketTimeoutException -> "TCP timeout"
                else -> error.message?.take(100) ?: "TCP connection failed"
            }
        }
        NodeProbeResult(profile.name, profile.server, profile.port, latency, detail)
    }
}
