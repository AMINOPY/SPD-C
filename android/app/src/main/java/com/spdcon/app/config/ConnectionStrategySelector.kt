package com.spdcon.app.config

enum class TunnelSelectionGoal {
    STANDARD,
    RESTRICTED_NETWORK,
    CLOUDFLARE_CDN,
    UDP_SPEED
}

/**
 * Ranks saved profiles using real probe results first, then explicit runtime and
 * transport compatibility. Recommendations never claim DPI resistance.
 */
data class RankedConnectionStrategy(
    val profile: ConfigProfile,
    val compatibility: RuntimeCompatibility,
    val report: ConnectionDiagnosticReport?,
    val score: Int,
    val reasonFa: String
) {
    val tunnelVerified: Boolean get() = report?.tunnelConnected == true
}

object ConnectionStrategySelector {
    fun rank(
        profiles: List<ConfigProfile>,
        reportsByProfileName: Map<String, ConnectionDiagnosticReport> = emptyMap(),
        goal: TunnelSelectionGoal = TunnelSelectionGoal.STANDARD
    ): List<RankedConnectionStrategy> =
        profiles.map { profile ->
            val compatibility = ConfigRuntimeCompatibility.inspectForBundledRuntime(profile)
            val report = reportsByProfileName[profile.name]
            val supportedScore = if (compatibility.supported) 5_000 else -5_000
            val tunnelScore = if (report?.tunnelConnected == true) 10_000 else 0
            val failedProbePenalty = if (report != null && !report.tunnelConnected) 350 else 0
            val goalScore = goalMatchScore(profile, goal)
            val favoriteScore = if (profile.favorite) 50 else 0
            val latencyPenalty = (report?.tunnelLatencyMs ?: 10_000L).coerceIn(0, 10_000).div(100).toInt()
            val reason = when {
                !compatibility.supported -> "این پروفایل با هستهٔ داخلی قابل اجرا نیست: ${compatibility.message}"
                report?.tunnelConnected == true -> "تونل واقعی این پروفایل روی شبکهٔ فعلی تأیید شد؛ این نتیجه تضمین عبور از DPI در شبکه‌های دیگر نیست."
                report != null -> "تست انجام شد اما تونل تأیید نشد؛ علت می‌تواند تنظیمات، سرور یا شبکه باشد."
                else -> "با هستهٔ داخلی سازگار است، اما هنوز با تست واقعی تأیید نشده است."
            }
            RankedConnectionStrategy(
                profile = profile,
                compatibility = compatibility,
                report = report,
                score = supportedScore + tunnelScore + goalScore + favoriteScore - failedProbePenalty - latencyPenalty,
                reasonFa = reason
            )
        }.sortedWith(compareByDescending<RankedConnectionStrategy> { it.score }.thenBy { it.profile.name })

    fun rankForLiveTesting(
        profiles: List<ConfigProfile>,
        goal: TunnelSelectionGoal = TunnelSelectionGoal.STANDARD,
        limit: Int = 5
    ): List<ConfigProfile> {
        require(limit in 1..20) { "Candidate limit must be between 1 and 20" }
        return rank(profiles, goal = goal)
            .filter { it.compatibility.supported }
            .take(limit)
            .map { it.profile }
    }

    private fun goalMatchScore(profile: ConfigProfile, goal: TunnelSelectionGoal): Int {
        val network = profile.network.lowercase()
        val security = profile.security.lowercase()
        return when (goal) {
            TunnelSelectionGoal.STANDARD -> if (security == "tls") 100 else 0
            TunnelSelectionGoal.RESTRICTED_NETWORK -> when {
                profile.protocol == ConfigProtocol.VLESS && security == "reality" -> 350
                profile.protocol == ConfigProtocol.VLESS && security == "tls" -> 250
                security == "tls" -> 100
                else -> 0
            }
            TunnelSelectionGoal.CLOUDFLARE_CDN -> if (
                profile.protocol == ConfigProtocol.VLESS && network in setOf("ws", "websocket") &&
                security == "tls" && !profile.parameters["host"].isNullOrBlank() &&
                !profile.parameters["sni"].isNullOrBlank()
            ) 400 else 0
            TunnelSelectionGoal.UDP_SPEED -> if (
                profile.protocol in setOf(ConfigProtocol.HYSTERIA2, ConfigProtocol.TUIC) && network == "udp"
            ) 400 else 0
        }
    }
}
