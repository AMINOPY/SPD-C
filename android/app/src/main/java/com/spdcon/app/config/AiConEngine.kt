package com.spdcon.app.config

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.LinkProperties
import java.net.HttpURLConnection
import java.net.URL

data class AiConNetworkSnapshot(
    val transport: String,
    val validatedInternet: Boolean,
    val downstreamKbps: Int?,
    val upstreamKbps: Int?,
    val carrierName: String?,
    val radioTechnology: String?,
    val radioBand: String?,
    val signalDbm: Int?,
    val dnsServers: List<String>,
    val detailFa: String
) {
    val estimatedDownstreamMbps: Double?
        get() = downstreamKbps?.takeIf { it > 0 }?.div(1000.0)
}

data class AiConCandidate(
    val sourceProfile: ConfigProfile,
    val report: ConnectionDiagnosticReport,
    val score: Long
)

data class AiConBuildDecision(
    val profile: ConfigProfile?,
    val report: ConnectionDiagnosticReport?,
    val snapshot: AiConNetworkSnapshot,
    val testedCandidates: Int,
    val messageFa: String
)

/**
 * Local adaptive builder: it does not download or import configurations.
 * It uses real, operator-configured server credentials already present on device,
 * probes them, and produces an AI_CON profile only from a tunnel that passes a live probe.
 * A locally invented UUID or REALITY key cannot provision a remote server.
 */
object AiConEngine {
    fun inspectNetwork(context: Context): AiConNetworkSnapshot {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = connectivity.activeNetwork
        val capabilities = active?.let(connectivity::getNetworkCapabilities)
        val links: LinkProperties? = active?.let(connectivity::getLinkProperties)
        val transport = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "vpn"
            else -> "unknown"
        }
        val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        // Do not request phone-state, call-log, or location permissions.
        // Android exposes transport/bandwidth/DNS without those sensitive permissions.
        val down = capabilities?.linkDownstreamBandwidthKbps?.takeIf { it > 0 }
        val up = capabilities?.linkUpstreamBandwidthKbps?.takeIf { it > 0 }
        val dns = links?.dnsServers.orEmpty().mapNotNull { runCatching { it.hostAddress }.getOrNull() }.distinct()
        val summary = buildList {
            add(when (transport) {
                "cellular" -> "دیتای سیم‌کارت"
                "wifi" -> "وای‌فای"
                "ethernet" -> "اترنت"
                "vpn" -> "شبکه از مسیر VPN"
                else -> "نوع شبکه نامشخص"
            })
            if (down != null) add("پهنای‌باند اعلام‌شدهٔ دانلود: ${"%.1f".format(down / 1000.0)} Mbps")
            if (up != null) add("پهنای‌باند اعلام‌شدهٔ آپلود: ${"%.1f".format(up / 1000.0)} Mbps")
            add(if (validated) "اینترنت توسط Android تأیید شده" else "اینترنت تأیید نشده")
            if (dns.isNotEmpty()) add("DNS در دسترس است")
        }.joinToString(" • ")
        return AiConNetworkSnapshot(
            transport = transport,
            validatedInternet = validated,
            downstreamKbps = down,
            upstreamKbps = up,
            carrierName = null,
            radioTechnology = null,
            radioBand = null,
            signalDbm = null,
            dnsServers = dns,
            detailFa = summary
        )
    }

    /**
     * Short optional throughput sample; never downloads configuration data.
     * Returns null if the speed endpoint is unreachable or the sample is inconclusive.
     */
    fun measureDownloadMbps(timeoutMs: Int = 3500, bytes: Int = 262144): Double? = runCatching {
        require(timeoutMs in 500..10000 && bytes in 65536..1048576)
        val connection = (URL("https://speed.cloudflare.com/__down?bytes=$bytes").openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val started = System.nanoTime()
            val received = connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var total = 0
                while (total < bytes) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                }
                total
            }
            val seconds = (System.nanoTime() - started) / 1_000_000_000.0
            if (received <= 0 || seconds <= 0.0) null else received * 8.0 / seconds / 1_000_000.0
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    fun rankCandidates(
        profiles: List<ConfigProfile>,
        reportsByName: Map<String, ConnectionDiagnosticReport>,
        snapshot: AiConNetworkSnapshot,
        measuredDownloadMbps: Double? = null
    ): List<AiConCandidate> = profiles.mapNotNull { profile ->
        val report = reportsByName[profile.name] ?: return@mapNotNull null
        if (!ConfigRuntimeCompatibility.inspectForBundledRuntime(profile).supported) return@mapNotNull null
        if (profile.validate().isNotEmpty() || !report.tunnelConnected) return@mapNotNull null
        val latency = report.tunnelLatencyMs ?: Long.MAX_VALUE / 4
        val transportFit = when {
            snapshot.transport == "cellular" && profile.security.equals("reality", true) &&
                profile.network.lowercase() in setOf("tcp", "raw", "xhttp") -> 120L
            snapshot.transport == "cellular" && profile.security.equals("tls", true) &&
                profile.network.lowercase() in setOf("tcp", "raw", "ws", "websocket", "grpc") -> 80L
            profile.security.equals("tls", true) -> 35L
            else -> 0L
        }
        val advertisedBandwidthFit = when {
            (snapshot.downstreamKbps ?: 0) in 1..1500 && profile.network.lowercase() in setOf("tcp", "raw") -> 25L
            snapshot.transport == "wifi" && profile.network.lowercase() in setOf("ws", "websocket") -> 5L
            else -> 0L
        }
        // Treat the short direct-link speed sample as a weak hint only; the live tunnel probe and latency dominate.
        val speedFit = when {
            measuredDownloadMbps != null && measuredDownloadMbps < 2.0 &&
                profile.network.lowercase() in setOf("tcp", "raw") -> 20L
            measuredDownloadMbps != null && measuredDownloadMbps > 30.0 &&
                profile.network.lowercase() in setOf("ws", "websocket") -> 5L
            else -> 0L
        }
        val signalFit = when {
            snapshot.signalDbm != null && snapshot.signalDbm <= -115 &&
                profile.network.lowercase() in setOf("tcp", "raw") -> 15L
            snapshot.radioTechnology?.startsWith("2G") == true &&
                profile.network.lowercase() in setOf("tcp", "raw") -> 15L
            else -> 0L
        }
        AiConCandidate(profile, report, 100_000L + transportFit + advertisedBandwidthFit + speedFit + signalFit - latency.coerceAtMost(60_000L))
    }.sortedWith(compareByDescending<AiConCandidate> { it.score }.thenBy { it.report.tunnelLatencyMs ?: Long.MAX_VALUE })

    fun createAiProfile(candidate: ConfigProfile): ConfigProfile = candidate.copy(
        name = "AI_CON",
        remark = "Locally generated by AI_CON from a live-verified server profile",
        tags = (candidate.tags + listOf("AI_CON", "auto-selected")).distinct().take(12),
        favorite = false
    )

}
