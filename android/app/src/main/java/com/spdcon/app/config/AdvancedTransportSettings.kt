package com.spdcon.app.config

/**
 * Carrier presets are starting points for user-controlled testing, not guaranteed bypass recipes.
 * Network behavior varies by region, APN, time, and Xray version.
 */
enum class NetworkPreset(val titleFa: String, val fragment: FragmentSettings) {
    MANUAL("دستی", FragmentSettings(enabled = false)),
    HAMRAHE_AVVAL("همراه اول", FragmentSettings(enabled = true, packets = "tlshello", length = "80-160", interval = "10-20")),
    IRANCELL("ایرانسل", FragmentSettings(enabled = true, packets = "tlshello", length = "100-180", interval = "10-25")),
    NATIONAL_NETWORK("نت ملی / اتصال محدود", FragmentSettings(enabled = true, packets = "tlshello", length = "80-140", interval = "0"));

    companion object {
        fun fromId(id: String?): NetworkPreset =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: MANUAL
    }
}

data class FragmentSettings(
    val enabled: Boolean = false,
    val packets: String = "tlshello",
    val length: String = "100-200",
    val interval: String = "10-20"
) {
    fun validate(): List<String> = buildList {
        if (packets !in setOf("tlshello", "1-3")) add("Fragment packets must be tlshello or 1-3")
        if (!isValidIntRange(length, allowZero = false)) add("Fragment length must be a positive integer or range, e.g. 100-200")
        if (!isValidIntRange(interval, allowZero = true)) add("Fragment interval must be a non-negative integer or range, e.g. 10-20")
    }

    fun toXrayFreedomSettings(): Map<String, Any>? {
        if (!enabled || validate().isNotEmpty()) return null
        return mapOf("fragment" to mapOf("packets" to packets, "length" to length, "interval" to interval))
    }
}

data class RealitySettings(
    val serverName: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val fingerprint: String = "chrome",
    val spiderX: String = "/",
    val flow: String = ""
) {
    fun validate(): List<String> = buildList {
        if (serverName.isBlank()) add("REALITY serverName (SNI) is required")
        if (publicKey.isBlank()) add("REALITY public key (pbk) is required")
        if (shortId.length > 16 || shortId.length % 2 != 0 || shortId.any { it !in '0'..'9' && it.lowercaseChar() !in 'a'..'f' }) {
            add("REALITY shortId must be 0-16 hexadecimal characters with an even length")
        }
        if (fingerprint.isBlank() || fingerprint.equals("unsafe", ignoreCase = true)) {
            add("REALITY requires a supported TLS fingerprint; unsafe is not supported")
        }
        if (spiderX.isNotEmpty() && !spiderX.startsWith("/")) add("REALITY spiderX should start with /")
        if (flow.isNotEmpty() && flow != "xtls-rprx-vision") add("Unsupported REALITY flow; use empty or xtls-rprx-vision")
    }
}

private fun isValidIntRange(value: String, allowZero: Boolean): Boolean {
    val parts = value.split("-")
    if (parts.size !in 1..2 || parts.any { it.isBlank() || it.toIntOrNull() == null }) return false
    val numbers = parts.map { it.toInt() }
    if (numbers.any { if (allowZero) it < 0 else it <= 0 }) return false
    return numbers.size == 1 || numbers[0] <= numbers[1]
}


/** Builds client-side REALITY values from the common share-link query keys. */
fun ConfigProfile.toRealitySettings(): RealitySettings = RealitySettings(
    serverName = parameters["sni"].orEmpty(),
    publicKey = parameters["pbk"] ?: parameters["publicKey"].orEmpty(),
    shortId = parameters["sid"] ?: parameters["shortId"].orEmpty(),
    fingerprint = parameters["fp"] ?: parameters["fingerprint"] ?: "chrome",
    spiderX = parameters["spx"] ?: parameters["spiderX"] ?: "/",
    flow = parameters["flow"].orEmpty()
)
