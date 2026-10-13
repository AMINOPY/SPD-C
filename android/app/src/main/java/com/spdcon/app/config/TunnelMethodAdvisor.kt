package com.spdcon.app.config

enum class TunnelMethod {
    VLESS_REALITY,
    VLESS_WEBSOCKET_TLS,
    VLESS_TCP_TLS,
    VLESS_GRPC_TLS,
    VMESS_TLS,
    TROJAN_TLS,
    SHADOWSOCKS,
    HYSTERIA2,
    TUIC,
    OTHER
}

enum class TunnelSelectionConfidence { LIVE_VERIFIED, COMPATIBLE_UNTESTED, EXTERNAL_RUNTIME_REQUIRED, CONFIGURATION_INCOMPLETE }

data class TunnelMethodAssessment(
    val profileName: String,
    val method: TunnelMethod,
    val runtimeName: String,
    val confidence: TunnelSelectionConfidence,
    val selectableInBundledApp: Boolean,
    val score: Int,
    val summaryFa: String,
    val requirementsFa: List<String>
)

/**
 * Assesses saved profiles only. It does not invent server capabilities or silently
 * convert transports: a matching protocol on the client is not proof the server supports it.
 */
object TunnelMethodAdvisor {
    fun assess(
        profile: ConfigProfile,
        goal: TunnelSelectionGoal = TunnelSelectionGoal.STANDARD,
        report: ConnectionDiagnosticReport? = null
    ): TunnelMethodAssessment {
        val runtime = ConfigRuntimeCompatibility.inspectForBundledRuntime(profile)
        val method = when {
            profile.protocol == ConfigProtocol.VLESS && profile.security.equals("reality", true) -> TunnelMethod.VLESS_REALITY
            profile.protocol == ConfigProtocol.VLESS && profile.network.lowercase() in setOf("ws", "websocket") && profile.security.equals("tls", true) -> TunnelMethod.VLESS_WEBSOCKET_TLS
            profile.protocol == ConfigProtocol.VLESS && profile.network.lowercase() == "grpc" && profile.security.equals("tls", true) -> TunnelMethod.VLESS_GRPC_TLS
            profile.protocol == ConfigProtocol.VLESS && profile.security.equals("tls", true) -> TunnelMethod.VLESS_TCP_TLS
            profile.protocol == ConfigProtocol.VMESS && profile.security.equals("tls", true) -> TunnelMethod.VMESS_TLS
            profile.protocol == ConfigProtocol.TROJAN && profile.security.equals("tls", true) -> TunnelMethod.TROJAN_TLS
            profile.protocol == ConfigProtocol.SHADOWSOCKS -> TunnelMethod.SHADOWSOCKS
            profile.protocol == ConfigProtocol.HYSTERIA2 -> TunnelMethod.HYSTERIA2
            profile.protocol == ConfigProtocol.TUIC -> TunnelMethod.TUIC
            else -> TunnelMethod.OTHER
        }
        val validationErrors = profile.validate()
        val requirements = buildList {
            if (!runtime.supported) add(runtime.message)
            if (profile.security.equals("reality", true)) {
                add("REALITY نیازمند SNI، کلید عمومی و shortId دقیق همان سرور است.")
            }
            if (method == TunnelMethod.VLESS_WEBSOCKET_TLS) {
                if (profile.parameters["host"].isNullOrBlank()) add("برای WS پشت CDN، Host باید با تنظیمات سرور/CDN هماهنگ باشد.")
                if (profile.parameters["sni"].isNullOrBlank()) add("SNI باید با گواهی TLS و دامنهٔ سرور هماهنگ باشد.")
                if (profile.parameters["path"].isNullOrBlank()) add("مسیر WebSocket باید با مسیر سمت سرور یکسان باشد.")
            }
            if (method == TunnelMethod.VLESS_GRPC_TLS && profile.parameters["serviceName"].isNullOrBlank() &&
                profile.parameters["service-name"].isNullOrBlank()) {
                add("نام سرویس gRPC باید با تنظیمات سمت سرور مطابقت داشته باشد.")
            }
            if (profile.protocol == ConfigProtocol.HYSTERIA2 || profile.protocol == ConfigProtocol.TUIC) {
                add("این پروتکل به runtime سازگار جداگانه نیاز دارد؛ APK فعلی آن را با Xray داخلی اجرا نمی‌کند.")
            }
            if (validationErrors.isNotEmpty()) add("اعتبارسنجی پروفایل: ${validationErrors.joinToString("; ")}")
        }
        val selectable = runtime.supported && validationErrors.isEmpty()
        val goalScore = when (goal) {
            TunnelSelectionGoal.STANDARD -> when (method) {
                TunnelMethod.VLESS_TCP_TLS, TunnelMethod.VLESS_WEBSOCKET_TLS, TunnelMethod.TROJAN_TLS, TunnelMethod.VMESS_TLS -> 100
                TunnelMethod.VLESS_REALITY -> 90
                else -> 0
            }
            TunnelSelectionGoal.RESTRICTED_NETWORK -> when (method) {
                TunnelMethod.VLESS_REALITY -> 300
                TunnelMethod.VLESS_WEBSOCKET_TLS, TunnelMethod.VLESS_TCP_TLS, TunnelMethod.TROJAN_TLS -> 200
                else -> 0
            }
            TunnelSelectionGoal.CLOUDFLARE_CDN -> if (method == TunnelMethod.VLESS_WEBSOCKET_TLS) 400 else 0
            TunnelSelectionGoal.UDP_SPEED -> if (method == TunnelMethod.HYSTERIA2 || method == TunnelMethod.TUIC) 400 else 0
        }
        val confidence = when {
            report?.tunnelConnected == true -> TunnelSelectionConfidence.LIVE_VERIFIED
            validationErrors.isNotEmpty() -> TunnelSelectionConfidence.CONFIGURATION_INCOMPLETE
            !runtime.supported -> TunnelSelectionConfidence.EXTERNAL_RUNTIME_REQUIRED
            else -> TunnelSelectionConfidence.COMPATIBLE_UNTESTED
        }
        val liveScore = if (report?.tunnelConnected == true) 10_000 else if (report != null) -250 else 0
        val summary = when (confidence) {
            TunnelSelectionConfidence.LIVE_VERIFIED -> "پروتکل با همین پروفایل روی شبکهٔ فعلی تست تونل را گذرانده است؛ عبور از DPI در شبکه‌های دیگر تضمین نیست."
            TunnelSelectionConfidence.COMPATIBLE_UNTESTED -> "قالب با هستهٔ داخلی سازگار است، اما هنوز با تست تونل واقعی تأیید نشده است."
            TunnelSelectionConfidence.EXTERNAL_RUNTIME_REQUIRED -> "برای اجرای این پروتکل به هسته/کلاینت سازگار خارجی نیاز است."
            TunnelSelectionConfidence.CONFIGURATION_INCOMPLETE -> "پروفایل ناقص یا نامعتبر است و قبل از انتخاب باید اصلاح شود."
        }
        return TunnelMethodAssessment(
            profileName = profile.name,
            method = method,
            runtimeName = runtime.runtimeName,
            confidence = confidence,
            selectableInBundledApp = selectable,
            score = (if (selectable) 5_000 else -5_000) + goalScore + liveScore,
            summaryFa = summary,
            requirementsFa = requirements
        )
    }

    fun rankSavedProfiles(
        profiles: List<ConfigProfile>,
        goal: TunnelSelectionGoal = TunnelSelectionGoal.STANDARD,
        reportsByProfileName: Map<String, ConnectionDiagnosticReport> = emptyMap()
    ): List<TunnelMethodAssessment> = profiles.map {
        assess(it, goal, reportsByProfileName[it.name])
    }.sortedWith(compareByDescending<TunnelMethodAssessment> { it.score }.thenBy { it.profileName })
}
