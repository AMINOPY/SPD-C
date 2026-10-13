package com.spdcon.app.config

/** User-facing setup goals. These are recommendations, not proof that a server supports a transport. */
enum class SetupGoal(val titleFa: String) {
    STANDARD("اینترنت عادی"),
    RESTRICTED_NETWORK("شبکه محدود"),
    CLOUDFLARE_CDN("تونل CDN کلودفلر"),
    UDP_SPEED("اتصال UDP")
}

data class SmartConfigSuggestion(
    val protocol: ConfigProtocol,
    val port: Int,
    val network: String,
    val security: String,
    val host: String = "",
    val path: String = "",
    val alpn: String = "",
    val noteFa: String
)

object SmartConfigAdvisor {
    fun suggest(goal: SetupGoal, preferredProtocol: ConfigProtocol = ConfigProtocol.VLESS): SmartConfigSuggestion =
        when (goal) {
            SetupGoal.STANDARD -> when (preferredProtocol) {
                ConfigProtocol.HYSTERIA2, ConfigProtocol.TUIC -> SmartConfigSuggestion(
                    preferredProtocol, 443, "udp", "tls", alpn = "h3",
                    noteFa = "پیشنهاد اولیه است؛ سرور باید همین پروتکل و پورت را پشتیبانی کند."
                )
                ConfigProtocol.SHADOWSOCKS -> SmartConfigSuggestion(
                    preferredProtocol, 8388, "tcp", "none",
                    noteFa = "پورت 8388 مقدار رایج پیشنهادی است؛ پورت واقعی را با سرور هماهنگ کن."
                )
                ConfigProtocol.TROJAN -> SmartConfigSuggestion(
                    preferredProtocol, 443, "tcp", "tls",
                    noteFa = "سرور باید Trojan روی TLS داشته باشد."
                )
                ConfigProtocol.VMESS -> SmartConfigSuggestion(
                    preferredProtocol, 443, "ws", "tls", path = "/",
                    noteFa = "WS/TLS فقط وقتی کار می‌کند که سمت سرور هم همین انتقال تنظیم شده باشد."
                )
                else -> SmartConfigSuggestion(
                    ConfigProtocol.VLESS, 443, "tcp", "tls",
                    noteFa = "پیشنهاد عمومی TLS است؛ اگر سرور REALITY دارد، مقادیر واقعی همان سرور را وارد یا از لینک وارد کن."
                )
            }
            SetupGoal.RESTRICTED_NETWORK -> SmartConfigSuggestion(
                ConfigProtocol.VLESS, 443, "tcp", "tls",
                noteFa = "TLS روی 443 فقط یک پیشنهاد سازگاری است، نه تشخیص قطعی یا تضمین اتصال."
            )
            SetupGoal.CLOUDFLARE_CDN -> SmartConfigSuggestion(
                ConfigProtocol.VLESS, 443, "ws", "tls", host = "", path = "/",
                noteFa = "دامنه باید پشت Proxy کلودفلر باشد و سرور origin نیز WS/TLS سازگار داشته باشد. SNI و Host را از دامنه واقعی خودت بگیر."
            )
            SetupGoal.UDP_SPEED -> SmartConfigSuggestion(
                ConfigProtocol.HYSTERIA2, 443, "udp", "tls", alpn = "h3",
                noteFa = "Hysteria2 از UDP استفاده می‌کند؛ اگر UDP مسدود باشد، این پیشنهاد مناسب نیست."
            )
        }

    /** A domain can be reused as a TLS host hint, but never as REALITY pbk/shortId. */
    fun looksLikeHostname(value: String): Boolean {
        val candidate = value.trim().trimEnd('.')
        if (candidate.isBlank() || candidate.contains(' ') || candidate.contains('/') ||
            candidate.contains(':') || candidate.startsWith("[") || candidate.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))
        ) return false
        val labels = candidate.split('.')
        return labels.size >= 2 && labels.all { label ->
            label.isNotBlank() && label.length <= 63 &&
                label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
    }
}
