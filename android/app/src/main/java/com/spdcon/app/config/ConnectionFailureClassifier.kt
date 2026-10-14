package com.spdcon.app.config

enum class ConnectionFailureCategory {
    CONFIGURATION,
    DNS_RESOLUTION,
    NODE_UNREACHABLE,
    CORE_INITIALIZATION,
    NETWORK_OR_FILTERING,
    UNKNOWN
}

data class ClassifiedConnectionFailure(
    val category: ConnectionFailureCategory,
    val titleFa: String,
    val nextStepFa: String
)

/**
 * Best-effort classification of a failed live probe. Error text varies by Android,
 * libXray and network, so ambiguous cases stay UNKNOWN instead of claiming DPI blocking.
 */
object ConnectionFailureClassifier {
    fun classify(message: String, phase: String? = null): ClassifiedConnectionFailure {
        val text = message.lowercase()
        val phaseText = phase.orEmpty().lowercase()
        return when {
            phaseText.contains("config") || text.contains("invalid config") ||
                text.contains("rejected the generated configuration") ||
                text.contains("requires ") || text.contains("profile validation") ||
                text.contains("unsupported transport") || text.contains("public key") ||
                text.contains("shortid") || text.contains("server name") ->
                ClassifiedConnectionFailure(
                    ConnectionFailureCategory.CONFIGURATION,
                    "احتمال مشکل در تنظیمات کانفیگ",
                    "آدرس، پورت، پروتکل، مسیر، SNI و پارامترهای سمت سرور را با هم تطبیق بده."
                )
            text.contains("unknownhost") || text.contains("unable to resolve") ||
                text.contains("name or service not known") || text.contains("no address associated") ||
                text.contains("dns resolution") ->
                ClassifiedConnectionFailure(
                    ConnectionFailureCategory.DNS_RESOLUTION,
                    "احتمال مشکل DNS",
                    "دامنه را با DNS دیگری بررسی کن و در صورت امکان آدرس IP و دامنهٔ TLS را جداگانه آزمایش کن."
                )
            text.contains("connection refused") || text.contains("connect timed out") ||
                text.contains("connection timed out") || text.contains("network is unreachable") ||
                text.contains("no route to host") || text.contains("tcp timeout") ->
                ClassifiedConnectionFailure(
                    ConnectionFailureCategory.NODE_UNREACHABLE,
                    "نود یا پورت در دسترس نیست",
                    "آدرس و پورت نود، وضعیت سرور و دسترسی TCP/UDP را بررسی کن؛ این نتیجه به‌تنهایی علت فیلترینگ را ثابت نمی‌کند."
                )
            phaseText.contains("core") || text.contains("xray-core rejected") ||
                text.contains("could not start") || text.contains("libxray") ||
                text.contains("failed to create") || text.contains("config loader") ->
                ClassifiedConnectionFailure(
                    ConnectionFailureCategory.CORE_INITIALIZATION,
                    "مشکل در هستهٔ اتصال",
                    "نسخهٔ هسته، پشتیبانی پروتکل و جزئیات خطای Xray را بررسی کن."
                )
            text.contains("handshake") || text.contains("eof") ||
                text.contains("unexpected end") || text.contains("reset by peer") ||
                text.contains("tls alert") || text.contains("context deadline exceeded") ->
                ClassifiedConnectionFailure(
                    ConnectionFailureCategory.NETWORK_OR_FILTERING,
                    "شکست در مسیر شبکه یا مذاکرهٔ اتصال",
                    "TLS/REALITY و پارامترهای سرور را بررسی و روی یک شبکهٔ دیگر هم آزمایش کن؛ DPI فقط یکی از علت‌های ممکن است."
                )
            else -> ClassifiedConnectionFailure(
                ConnectionFailureCategory.UNKNOWN,
                "علت اتصال مشخص نشد",
                "متن خطا را نگه دار و DNS، دسترسی نود، تنظیمات کانفیگ و هسته را جداگانه آزمایش کن."
            )
        }
    }
}
