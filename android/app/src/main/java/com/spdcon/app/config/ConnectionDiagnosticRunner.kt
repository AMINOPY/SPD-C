package com.spdcon.app.config

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

enum class DiagnosticStageState { PASS, FAIL, SKIPPED, UNKNOWN }

data class DiagnosticStageResult(
    val name: String,
    val state: DiagnosticStageState,
    val detail: String,
    val latencyMs: Long? = null
)

data class ConnectionDiagnosticReport(
    val profileName: String,
    val stages: List<DiagnosticStageResult>,
    val tunnelConnected: Boolean,
    val tunnelLatencyMs: Long?,
    val coreVersion: String?,
    val diagnosis: ClassifiedConnectionFailure?
) {
    val tunnelVerified: Boolean get() = stages.any {
        it.name == "tunnel" && it.state == DiagnosticStageState.PASS
    }
}

/**
 * Runs independent local/network checks and then a real outbound HTTPS request
 * through the selected profile using the bundled Xray runtime. A TLS handshake alone
 * is never reported as a working tunnel or proof of DPI resistance.
 */
object ConnectionDiagnosticRunner {
    private const val SOCKET_TIMEOUT_MS = 2_500

    fun run(profile: ConfigProfile): ConnectionDiagnosticReport {
        val stages = mutableListOf<DiagnosticStageResult>()
        var failureText: String? = null

        val dnsStarted = System.nanoTime()
        val addresses = runCatching { InetAddress.getAllByName(profile.server).map { it.hostAddress }.distinct() }
        addresses.fold(
            onSuccess = { ips ->
                stages += DiagnosticStageResult(
                    "dns",
                    DiagnosticStageState.PASS,
                    if (ips.isEmpty()) "No IP addresses returned" else "Resolved to: ${ips.take(4).joinToString()}",
                    elapsedMs(dnsStarted)
                )
            },
            onFailure = { error ->
                failureText = error.message ?: error.javaClass.simpleName
                stages += DiagnosticStageResult("dns", DiagnosticStageState.FAIL, failureText!!, elapsedMs(dnsStarted))
            }
        )

        val tcpStarted = System.nanoTime()
        val tcp = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(profile.server, profile.port), SOCKET_TIMEOUT_MS)
            }
        }
        tcp.fold(
            onSuccess = {
                stages += DiagnosticStageResult("tcp", DiagnosticStageState.PASS, "${profile.server}:${profile.port} is reachable", elapsedMs(tcpStarted))
            },
            onFailure = { error ->
                if (failureText == null) failureText = error.message ?: error.javaClass.simpleName
                stages += DiagnosticStageResult("tcp", DiagnosticStageState.FAIL, error.message ?: error.javaClass.simpleName, elapsedMs(tcpStarted))
            }
        )

        if (profile.security.equals("tls", ignoreCase = true)) {
            val tlsStarted = System.nanoTime()
            val sni = profile.parameters["sni"]?.takeIf { it.isNotBlank() } ?: profile.server
            val tls = runCatching {
                Socket().use { raw ->
                    raw.connect(InetSocketAddress(profile.server, profile.port), SOCKET_TIMEOUT_MS)
                    raw.soTimeout = SOCKET_TIMEOUT_MS
                    (SSLSocketFactory.getDefault() as SSLSocketFactory)
                        .createSocket(raw, sni, profile.port, true).use { ssl ->
                            ssl as SSLSocket
                            ssl.soTimeout = SOCKET_TIMEOUT_MS
                            ssl.sslParameters = ssl.sslParameters.apply {
                                serverNames = if (isDnsHostname(sni)) listOf(SNIHostName(sni)) else emptyList()
                                endpointIdentificationAlgorithm = "HTTPS"
                            }
                            ssl.startHandshake()
                        }
                }
            }
            tls.fold(
                onSuccess = {
                    stages += DiagnosticStageResult("tls", DiagnosticStageState.PASS, "TLS handshake and hostname validation succeeded for $sni", elapsedMs(tlsStarted))
                },
                onFailure = { error ->
                    if (failureText == null) failureText = error.message ?: error.javaClass.simpleName
                    stages += DiagnosticStageResult("tls", DiagnosticStageState.FAIL, error.message ?: error.javaClass.simpleName, elapsedMs(tlsStarted))
                }
            )
        } else if (profile.security.equals("reality", ignoreCase = true)) {
            stages += DiagnosticStageResult("tls", DiagnosticStageState.SKIPPED, "REALITY is not ordinary TLS; validated by the Xray tunnel probe instead")
        } else {
            stages += DiagnosticStageResult("tls", DiagnosticStageState.SKIPPED, "Profile does not use TLS security")
        }

        val tunnelStarted = System.nanoTime()
        val tunnel = runCatching { XrayCoreTester.testProfile(profile) }
        tunnel.fold(
            onSuccess = { test ->
                stages += DiagnosticStageResult(
                    "tunnel",
                    if (test.connected) DiagnosticStageState.PASS else DiagnosticStageState.FAIL,
                    test.message,
                    test.latencyMs ?: elapsedMs(tunnelStarted)
                )
                if (!test.connected && failureText == null) failureText = test.message
            },
            onFailure = { error ->
                val detail = error.message ?: error.javaClass.simpleName
                stages += DiagnosticStageResult("tunnel", DiagnosticStageState.FAIL, detail, elapsedMs(tunnelStarted))
                if (failureText == null) failureText = detail
            }
        )
        val tunnelStage = stages.first { it.name == "tunnel" }
        val coreVersion = tunnel.getOrNull()?.coreVersion
        val diagnosis = if (tunnelStage.state == DiagnosticStageState.PASS) null else
            ConnectionFailureClassifier.classify(failureText ?: tunnelStage.detail, when {
                stages.any { it.name == "tunnel" && it.detail.contains("Xray-core", true) } -> "core"
                stages.any { it.name == "dns" && it.state == DiagnosticStageState.FAIL } -> "dns"
                else -> "network"
            })
        return ConnectionDiagnosticReport(
            profileName = profile.name,
            stages = stages,
            tunnelConnected = tunnelStage.state == DiagnosticStageState.PASS,
            tunnelLatencyMs = tunnelStage.latencyMs,
            coreVersion = coreVersion,
            diagnosis = diagnosis
        )
    }

    private fun isDnsHostname(value: String): Boolean =
        !value.matches(Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$")) && !value.contains(":")

    private fun elapsedMs(startedNs: Long): Long =
        ((System.nanoTime() - startedNs) / 1_000_000L).coerceAtLeast(0)
}
