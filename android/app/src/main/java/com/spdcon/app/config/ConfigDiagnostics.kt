package com.spdcon.app.config

enum class DiagnosticLevel { INFO, WARNING }

data class ConfigDiagnostic(
    val level: DiagnosticLevel,
    val message: String
)

/**
 * Local preflight checks only. These checks cannot prove that a remote node is reachable,
 * that a CDN DNS record is proxied, or that a carrier will pass the connection.
 */
object ConfigDiagnostics {
    private val commonCloudflarePorts = setOf(80, 443, 8080, 8880, 2052, 2082, 2086, 2095, 2053, 2083, 2087, 2096, 8443)

    fun inspect(profile: ConfigProfile, cdnEnabled: Boolean = false): List<ConfigDiagnostic> = buildList {
        val params = profile.parameters
        val network = profile.network.lowercase()
        val security = profile.security.lowercase()

        if (security == "tls" && params["sni"].isNullOrBlank()) {
            add(ConfigDiagnostic(DiagnosticLevel.WARNING, "SNI خالی است؛ اگر سرور به serverName نیاز دارد، آن را از تنظیمات نود وارد کن."))
        }
        if (network == "ws" && security != "tls") {
            add(ConfigDiagnostic(DiagnosticLevel.WARNING, "WebSocket بدون TLS رمزنگاری لایه انتقال ندارد؛ فقط اگر سرور عمداً همین‌طور تنظیم شده ادامه بده."))
        }
        if (network == "grpc" && params["serviceName"].isNullOrBlank()) {
            add(ConfigDiagnostic(DiagnosticLevel.WARNING, "نام سرویس gRPC خالی است؛ باید با تنظیمات سمت سرور مطابقت داشته باشد."))
        }
        if (security == "reality") {
            if (params["sni"].isNullOrBlank()) add(ConfigDiagnostic(DiagnosticLevel.WARNING, "REALITY به SNI/serverName دقیق نود نیاز دارد."))
            if (params["pbk"].isNullOrBlank()) add(ConfigDiagnostic(DiagnosticLevel.WARNING, "کلید عمومی REALITY (pbk) وارد نشده است."))
            if (params["sid"].isNullOrBlank()) add(ConfigDiagnostic(DiagnosticLevel.WARNING, "shortId مخصوص همان نود وارد نشده است."))
        }
        if (profile.protocol == ConfigProtocol.HYSTERIA2 || profile.protocol == ConfigProtocol.TUIC) {
            val alpn = params["alpn"].orEmpty().split(',').map { it.trim() }
            if (alpn.isNotEmpty() && "h3" !in alpn) {
                add(ConfigDiagnostic(DiagnosticLevel.WARNING, "برای این پروتکل UDP، ALPN معمولاً باید با پشتیبانی سرور و کلاینت هماهنگ باشد؛ مقدار h3 را بررسی کن."))
            }
            add(ConfigDiagnostic(DiagnosticLevel.INFO, "Hysteria2/TUIC به UDP قابل‌دسترسی در سمت سرور نیاز دارند؛ CDN پروکسی معمولی Cloudflare جایگزین مسیر UDP نیست."))
        }
        if (params["allowInsecure"] == "1" || params["allow_insecure"] == "1" || params["insecure"] == "1") {
            add(ConfigDiagnostic(DiagnosticLevel.WARNING, "بررسی گواهی TLS غیرفعال شده؛ این کار امنیت را کاهش می‌دهد و فقط برای عیب‌یابی موقت مناسب است."))
        }
        if (cdnEnabled) {
            if (profile.protocol != ConfigProtocol.VLESS || network != "ws" || security != "tls") {
                add(ConfigDiagnostic(DiagnosticLevel.WARNING, "قالب رایج CDN این برنامه VLESS + WS + TLS است؛ تنظیم فعلی با آن هماهنگ نیست."))
            }
            if (params["host"].isNullOrBlank()) add(ConfigDiagnostic(DiagnosticLevel.WARNING, "Host خالی است؛ دامنه باید با تنظیمات مبدأ و Cloudflare هماهنگ باشد."))
            if (params["sni"].isNullOrBlank()) add(ConfigDiagnostic(DiagnosticLevel.WARNING, "SNI خالی است؛ دامنه TLS باید با گواهی و مبدأ سازگار باشد."))
            if (profile.port !in commonCloudflarePorts) {
                add(ConfigDiagnostic(DiagnosticLevel.WARNING, "پورت انتخابی در فهرست رایج پورت‌های HTTP پروکسی Cloudflare نیست؛ پورت مجاز و نوع پروکسی را بررسی کن."))
            }
            add(ConfigDiagnostic(DiagnosticLevel.INFO, "این بررسی محلی است؛ وضعیت Proxy رکورد DNS، پورت و دسترسی مبدأ هنوز زنده بررسی نشده است."))
        }
        if (isEmpty()) add(ConfigDiagnostic(DiagnosticLevel.INFO, "بررسی ساختاری اولیه انجام شد؛ اتصال واقعی و عبور از DPI تأیید نشده است."))
    }

    fun format(profile: ConfigProfile, cdnEnabled: Boolean = false): String =
        inspect(profile, cdnEnabled).joinToString("\n") {
            (if (it.level == DiagnosticLevel.WARNING) "⚠️ " else "ℹ️ ") + it.message
        }
}
