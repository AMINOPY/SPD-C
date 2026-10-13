package com.spdcon.app.config

enum class ConfigProtocol { VLESS, VMESS, TROJAN, SHADOWSOCKS }

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
        if (server.isBlank() || server.any { it.isWhitespace() }) add("Server must be a hostname or IP address")
        if (port !in 1..65535) add("Port must be between 1 and 65535")
        if (credential.isBlank()) add("Credential is required")
        if (network.isBlank()) add("Network type is required")
        if (security.isBlank()) add("Security type is required")
    }
}
