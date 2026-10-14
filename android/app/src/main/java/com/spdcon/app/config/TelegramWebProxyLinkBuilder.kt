package com.spdcon.app.config

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Builds the Telegram MTProxy deep link for an already provisioned Web Proxy inbound.
 *
 * This does not create an MTProxy server or mint a valid MTProxy secret. The hostname and
 * secret must come from the deployed, operator-managed proxy.
 */
object TelegramWebProxyLinkBuilder {
    fun build(hostname: String, secret: String): Result<String> = runCatching {
        val host = hostname.trim()
        val key = secret.trim()
        require(host.isNotBlank() && !host.contains("://") && !host.any { it.isWhitespace() || it == '/' || it == '?' || it == '#' }) {
            "A hostname only (without scheme, port, path or whitespace) is required"
        }
        require(host.contains('.') || host == "localhost") { "Hostname is invalid" }
        require(key.isNotBlank()) { "The provisioned MTProxy secret is required" }
        val encodedHost = URLEncoder.encode(host, StandardCharsets.UTF_8.name()).replace("+", "%20")
        val encodedSecret = URLEncoder.encode(key, StandardCharsets.UTF_8.name()).replace("+", "%20")
        "https://t.me/proxy?server=$encodedHost&secret=$encodedSecret"
    }
}
