package com.spdcon.app.ui

import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

data class CountryIpSample(
    val countryCode: String,
    val prefixCount: Int,
    val ips: List<String>
)

/**
 * Samples IPv4 addresses from daily-updated country CIDRs published by ipverse.
 * The feed describes registration/delegation, not guaranteed physical location or VPN quality.
 */
object CountryIpRangeSource {
    const val MAX_SAMPLES = 256
    private const val MAX_PREFIX_LINES = 150_000
    private const val MAX_DOWNLOAD_CHARS = 8 * 1024 * 1024
    private val random = SecureRandom()

    fun fetchSample(countryCode: String, maxSamples: Int = MAX_SAMPLES): CountryIpSample {
        val code = countryCode.trim().lowercase()
        require(code.matches(Regex("^[a-z]{2}$"))) { "Choose a valid two-letter ISO country code" }
        require(maxSamples in 1..MAX_SAMPLES) { "Sample count must be between 1 and $MAX_SAMPLES" }

        val url = URL("https://raw.githubusercontent.com/ipverse/country-ip-blocks/master/country/$code/ipv4-aggregated.txt")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 18_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "SpdCon-IP-Scanner/1.0")
        }
        val prefixes = mutableListOf<Cidr>()
        try {
            val status = connection.responseCode
            require(status == HttpURLConnection.HTTP_OK) {
                "Country IP source returned HTTP $status for ${code.uppercase()}"
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                var totalChars = 0
                var lineCount = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    totalChars += line.length + 1
                    lineCount++
                    require(totalChars <= MAX_DOWNLOAD_CHARS) { "Country IP list exceeded the safe download limit" }
                    require(lineCount <= MAX_PREFIX_LINES) { "Country IP list contains too many ranges" }
                    val token = line.substringBefore('#').trim()
                    if (token.isNotEmpty()) parseCidr(token)?.let(prefixes::add)
                }
            }
        } finally {
            connection.disconnect()
        }
        require(prefixes.isNotEmpty()) { "No IPv4 ranges were found for ${code.uppercase()}" }

        val selected = linkedSetOf<String>()
        // Spread the sample over the full registered prefix list instead of biasing the first ranges.
        val attempts = minOf(prefixes.size, maxSamples * 4)
        for (i in 0 until attempts) {
            val index = if (attempts <= 1) 0 else ((i.toLong() * (prefixes.size - 1)) / (attempts - 1)).toInt()
            val ip = prefixes[index].randomPublicAddress() ?: continue
            selected += ip
            if (selected.size >= maxSamples) break
        }
        // If reserved/special prefixes were skipped, try remaining ranges until the sample is filled.
        if (selected.size < maxSamples) {
            for (range in prefixes.shuffled()) {
                range.randomPublicAddress()?.let(selected::add)
                if (selected.size >= maxSamples) break
            }
        }
        require(selected.isNotEmpty()) { "No public IPv4 sample could be generated for ${code.uppercase()}" }
        return CountryIpSample(code.uppercase(), prefixes.size, selected.toList())
    }

    private data class Cidr(val network: Long, val prefix: Int) {
        fun randomPublicAddress(): String? {
            val size = 1L shl (32 - prefix)
            val firstOffset = if (size > 2) 1L else 0L
            val lastOffset = if (size > 2) size - 2 else size - 1
            if (lastOffset < firstOffset) return null
            val span = lastOffset - firstOffset + 1
            val offset = firstOffset + java.lang.Long.remainderUnsigned(random.nextLong(), span)
            val value = network + offset
            if (!isPublicIpv4(value)) return null
            return listOf(24, 16, 8, 0).joinToString(".") { ((value shr it) and 255).toString() }
        }
    }

    private fun parseCidr(raw: String): Cidr? {
        val pieces = raw.split('/', limit = 2)
        val address = pieces[0].trim().split('.')
        if (address.size != 4 || address.any { it.isEmpty() || it.length > 3 || it.any { ch -> !ch.isDigit() } }) return null
        val octets = address.map { it.toIntOrNull() ?: return null }
        if (octets.any { it !in 0..255 }) return null
        val prefix = if (pieces.size == 1) 32 else pieces[1].toIntOrNull() ?: return null
        if (prefix !in 0..32) return null
        val value = octets.fold(0L) { acc, octet -> (acc shl 8) or octet.toLong() }
        val mask = if (prefix == 0) 0L else (0xFFFF_FFFFL shl (32 - prefix)) and 0xFFFF_FFFFL
        return Cidr(value and mask, prefix)
    }

    private fun isPublicIpv4(ip: Long): Boolean {
        val a = ((ip shr 24) and 255).toInt()
        val b = ((ip shr 16) and 255).toInt()
        val c = ((ip shr 8) and 255).toInt()
        return when {
            a == 0 || a == 10 || a == 127 || a >= 224 -> false
            a == 100 && b in 64..127 -> false
            a == 169 && b == 254 -> false
            a == 172 && b in 16..31 -> false
            a == 192 && b == 168 -> false
            a == 192 && b == 0 && c == 0 -> false
            a == 192 && b == 0 && c == 2 -> false
            a == 192 && b == 88 && c == 99 -> false
            a == 198 && b in 18..19 -> false
            a == 198 && b == 51 && c == 100 -> false
            a == 203 && b == 0 && c == 113 -> false
            a == 255 -> false
            else -> true
        }
    }
}
