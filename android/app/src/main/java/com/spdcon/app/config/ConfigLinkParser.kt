package com.spdcon.app.config

import java.util.Base64
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Parses a deliberately small, explicit subset of common share links.
 * Imported content is untrusted: callers must show validation errors before saving or exporting.
 */
object ConfigLinkParser {
    private const val MAX_INPUT_LENGTH = 32_768

    fun parse(raw: String): Result<ConfigProfile> = runCatching {
        require(raw.length <= MAX_INPUT_LENGTH) { "Configuration link is too large" }
        val input = raw.trim()
        require(input.isNotEmpty()) { "Configuration link is empty" }

        when {
            input.startsWith("vless://", ignoreCase = true) -> parseUri(input, ConfigProtocol.VLESS)
            input.startsWith("trojan://", ignoreCase = true) -> parseUri(input, ConfigProtocol.TROJAN)
            input.startsWith("vmess://", ignoreCase = true) -> parseVmess(input)
            input.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(input)
            else -> throw IllegalArgumentException("Unsupported link scheme. Supported now: VLESS, VMess, Trojan and Shadowsocks")
        }
    }

    private fun parseUri(input: String, protocol: ConfigProtocol): ConfigProfile {
        val uri = URI(input)
        val server = uri.host ?: throw IllegalArgumentException("Server hostname is missing")
        val port = uri.port
        require(port in 1..65535) { "A valid server port is required" }
        val credential = uri.rawUserInfo
            ?.let { decodePercentEncoded(it) }
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Credential is missing")

        val params = parseQuery(uri.rawQuery)
        val name = uri.rawFragment
            ?.let { decodePercentEncoded(it) }
            ?.ifBlank { null }
            ?: "$protocol • $server"
        val network = params["type"] ?: params["network"] ?: "tcp"
        val security = params["security"] ?: "none"

        return ConfigProfile(
            name = name,
            protocol = protocol,
            server = server,
            port = port,
            credential = credential,
            network = network,
            security = security,
            remark = name,
            parameters = params
        ).also { profile ->
            val errors = profile.validate()
            require(errors.isEmpty()) { errors.joinToString("; ") }
        }
    }

    private fun parseShadowsocks(input: String): ConfigProfile {
        val withoutScheme = input.substring(5)
        val beforeFragment = withoutScheme.substringBefore("#")
        val payload = beforeFragment.substringBefore("?")
        val query = beforeFragment.substringAfter("?", "").takeIf { it.isNotBlank() }
        require(payload.isNotBlank()) { "Shadowsocks credentials are missing" }

        fun decodeBase64(value: String): String? {
            val normalized = value.replace('-', '+').replace('_', '/')
            val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
            return runCatching { String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8) }.getOrNull()
        }

        val methodPassword: String
        val serverPart: String
        if (payload.contains("@")) {
            val userInfo = payload.substringBeforeLast("@")
            serverPart = payload.substringAfterLast("@")
            methodPassword = decodeBase64(userInfo)?.takeIf { it.contains(":") } ?: userInfo
        } else {
            val decoded = decodeBase64(payload)
                ?: throw IllegalArgumentException("Shadowsocks payload is not valid Base64")
            require(decoded.contains("@")) {
                "Shadowsocks link must contain method:password@host:port or SIP002 userinfo@host:port"
            }
            methodPassword = decoded.substringBeforeLast("@")
            serverPart = decoded.substringAfterLast("@")
        }

        val server: String
        val portText: String
        if (serverPart.startsWith("[")) {
            val closingBracket = serverPart.indexOf(']')
            require(closingBracket > 1 && serverPart.getOrNull(closingBracket + 1) == ':') {
                "Shadowsocks IPv6 address must use [address]:port"
            }
            server = serverPart.substring(1, closingBracket)
            portText = serverPart.substring(closingBracket + 2)
        } else {
            val colon = serverPart.lastIndexOf(':')
            require(colon > 0) { "Shadowsocks server port is missing" }
            server = serverPart.substring(0, colon)
            portText = serverPart.substring(colon + 1)
        }
        val port = portText.toIntOrNull() ?: -1
        val methodColon = methodPassword.indexOf(':')
        require(methodColon > 0 && methodColon < methodPassword.lastIndex) {
            "Shadowsocks method or password is missing"
        }
        val method = methodPassword.substring(0, methodColon)
        val password = methodPassword.substring(methodColon + 1)
        val remark = withoutScheme.substringAfter("#", "").takeIf { it.isNotBlank() }
            ?.let { decodePercentEncoded(it) }
        val parameters = buildMap {
            put("method", method)
            parseQuery(query).forEach { (key, value) -> put(key, value) }
        }
        val profile = ConfigProfile(
            name = remark ?: "Shadowsocks • $server",
            protocol = ConfigProtocol.SHADOWSOCKS,
            server = server,
            port = port,
            credential = password,
            network = "tcp",
            security = "none",
            remark = remark,
            parameters = parameters
        )
        val errors = profile.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return profile
    }

    private fun decodePercentEncoded(value: String): String =
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())

    private fun decodeBase64Payload(value: String): ByteArray {
        val normalized = value.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return Base64.getDecoder().decode(padded)
    }

    private fun parseVmess(input: String): ConfigProfile {
        val encoded = input.substringAfter("vmess://", missingDelimiterValue = "")
        require(encoded.isNotBlank()) { "VMess payload is missing" }
        val decoded = try {
            String(decodeBase64Payload(encoded), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("VMess payload is not valid Base64")
        }
        require(decoded.length <= MAX_INPUT_LENGTH) { "VMess payload is too large" }
        val json = try {
            JSONObject(decoded)
        } catch (_: Exception) {
            throw IllegalArgumentException("VMess payload is not valid JSON")
        }

        val server = json.optString("add").trim()
        val port = json.optString("port").toIntOrNull() ?: json.optInt("port", -1)
        val id = json.optString("id").trim()
        val name = json.optString("ps").ifBlank { "VMess • $server" }
        val network = json.optString("net").ifBlank { "tcp" }
        val security = json.optString("tls").ifBlank { "none" }

        val profile = ConfigProfile(
            name = name,
            protocol = ConfigProtocol.VMESS,
            server = server,
            port = port,
            credential = id,
            network = network,
            security = security,
            remark = name,
            parameters = buildMap {
                put("aid", json.optString("aid", "0"))
                put("scy", json.optString("scy", "auto"))
                put("host", json.optString("host"))
                put("path", json.optString("path"))
                put("tls", security)
                put("net", network)
            }.filterValues { it.isNotBlank() }
        )
        val errors = profile.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return profile
    }

    private fun parseQuery(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        return query.split("&")
            .filter { it.isNotBlank() }
            .associate { part ->
                val key = part.substringBefore("=").let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
                val value = part.substringAfter("=", "").let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
                key to value
            }
    }
}
