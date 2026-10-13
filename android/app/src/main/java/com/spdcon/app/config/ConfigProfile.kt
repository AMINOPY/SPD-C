package com.spdcon.app.config

enum class ConfigProtocol { VLESS, VMESS, TROJAN, SHADOWSOCKS, HYSTERIA2, TUIC }

data class ConfigProfile(
    val name: String,
    val protocol: ConfigProtocol,
    val server: String,
    val port: Int,
    val credential: String,
    val network: String = "tcp",
    val security: String = "none",
    val remark: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false
) {
    fun validate(): List<String> = buildList {
        if (name.isBlank()) add("Name is required")
        if (server.isBlank() ||
            server.any { it.isWhitespace() || it.isISOControl() || it in "/?#@&=%" } ||
            (server.startsWith("[") != server.endsWith("]")) ||
            (server.contains(":") && !server.startsWith("[") && server.count { it == ':' } < 2)) {
            add("Server must be a hostname or IP address, without URL paths, credentials or query parameters")
        }
        if (port !in 1..65535) add("Port must be between 1 and 65535")
        if (credential.isBlank()) add("Credential is required")
        if (network.isBlank()) {
            add("Network type is required")
        } else {
            val normalizedNetwork = network.lowercase()
            val supportedNetworks = setOf("tcp", "raw", "ws", "websocket", "grpc", "xhttp", "splithttp")
            if (protocol == ConfigProtocol.HYSTERIA2 || protocol == ConfigProtocol.TUIC) {
                if (normalizedNetwork != "udp") add("$protocol requires UDP transport")
            } else if (protocol != ConfigProtocol.SHADOWSOCKS && normalizedNetwork !in supportedNetworks) {
                add("Unsupported transport for Xray config export: $network")
            }
        }
        if (security.isBlank()) {
            add("Security type is required")
        } else {
            val normalizedSecurity = security.lowercase()
            if (normalizedSecurity !in setOf("none", "tls", "reality")) {
                add("Unsupported security type: $security")
            }
            if ((protocol == ConfigProtocol.HYSTERIA2 || protocol == ConfigProtocol.TUIC) &&
                normalizedSecurity != "tls") {
                add("$protocol requires TLS security")
            }
            if (protocol == ConfigProtocol.SHADOWSOCKS && normalizedSecurity != "none") {
                add("Shadowsocks profiles must use none security in this Xray exporter")
            }
            if (normalizedSecurity == "reality" &&
                network.lowercase() !in setOf("tcp", "raw", "xhttp", "splithttp", "grpc")) {
                add("REALITY supports RAW/TCP, XHTTP or gRPC transport")
            }
        }
        val path = parameters["path"]
        if (path != null && network.lowercase() in setOf("ws", "websocket", "xhttp", "splithttp")) {
            if (!path.startsWith("/") || path.contains("..") || path.any { it.isISOControl() }) {
                add("Transport path must be an absolute safe path")
            }
        }
        val hostHeader = parameters["host"]
        if (!hostHeader.isNullOrBlank() && hostHeader.any { it.isWhitespace() || it.isISOControl() }) {
            add("Transport Host must not contain whitespace or control characters")
        }
        val sni = parameters["sni"]
        if (!sni.isNullOrBlank() &&
            (sni.any { it.isWhitespace() || it.isISOControl() || it in "/?#@" } ||
                sni.startsWith(".") || sni.endsWith("."))) {
            add("TLS SNI/serverName must be a hostname or IP, not a URL")
        }
        val alpn = parameters["alpn"]
        if (!alpn.isNullOrBlank() && alpn.split(",").any { token ->
                token.trim().isEmpty() || token.trim().any { it.isWhitespace() || it.isISOControl() }
            }) {
            add("ALPN must be a comma-separated list of protocol tokens")
        }
        val headerType = parameters["headerType"] ?: parameters["type"]
        if (!headerType.isNullOrBlank() &&
            headerType.lowercase() !in setOf("none", "tcp", "http")) {
            add("TCP header type must be none or http")
        }
        val serviceName = parameters["serviceName"] ?: parameters["service-name"]
        if (!serviceName.isNullOrBlank() && serviceName.any { it.isISOControl() || it == '/' || it == '?' || it == '#' }) {
            add("gRPC service name contains unsupported characters")
        }
        if (security.equals("reality", ignoreCase = true)) {
            if (protocol != ConfigProtocol.VLESS) add("REALITY is supported here only for VLESS profiles")
            if (parameters["sni"].isNullOrBlank()) add("REALITY requires the server's configured SNI/serverName")
            if (parameters["pbk"].isNullOrBlank()) add("REALITY requires the public key (pbk) from the server")
            val shortId = parameters["sid"]
            if (shortId == null) {
                add("REALITY requires the server's shortId field (it may be empty)")
            } else if (!shortId.matches(Regex("^(?:[0-9a-fA-F]{2}){0,8}$"))) {
                add("REALITY shortId must be 0-16 hexadecimal characters with even length")
            }
        }
        if (protocol == ConfigProtocol.TUIC && parameters["password"].isNullOrBlank()) {
            add("TUIC requires its separate password")
        }
    }
}
