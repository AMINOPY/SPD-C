package com.spdcon.app.config

import android.util.Base64
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
            else -> throw IllegalArgumentException("Unsupported link scheme. Supported now: VLESS, VMess and Trojan")
        }
    }

    private fun parseUri(input: String, protocol: ConfigProtocol): ConfigProfile {
        val uri = URI(input)
        val server = uri.host ?: throw IllegalArgumentException("Server hostname is missing")
        val port = uri.port
        require(port in 1..65535) { "A valid server port is required" }
        val credential = uri.rawUserInfo
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Credential is missing")

        val params = parseQuery(uri.rawQuery)
        val name = uri.rawFragment
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
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

    private fun parseVmess(input: String): ConfigProfile {
        val encoded = input.substringAfter("vmess://", missingDelimiterValue = "")
        require(encoded.isNotBlank()) { "VMess payload is missing" }
        val decoded = try {
            String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8)
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
