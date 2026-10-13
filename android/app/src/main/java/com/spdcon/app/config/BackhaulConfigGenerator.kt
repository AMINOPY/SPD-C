package com.spdcon.app.config

enum class BackhaulRole { SERVER, CLIENT }

data class BackhaulSettings(
    val role: BackhaulRole,
    val transport: String,
    val bindPort: Int? = null,
    val remoteHost: String = "",
    val remotePort: Int? = null,
    val token: String,
    val ports: List<String> = emptyList(),
    val keepalivePeriod: Int? = 75,
    val heartbeat: Int? = 40,
    val muxConnections: Int? = 8,
    val cloudflareProxy: Boolean = false
)

object BackhaulConfigGenerator {
    // These transports are listed by the upstream Musixal/Backhaul README.
    val supportedTransports = setOf("tcp", "tcpmux", "ws", "wss", "wsmux", "wssmux")

    fun validate(settings: BackhaulSettings): List<String> = buildList {
        if (settings.transport !in supportedTransports) add("Unsupported Backhaul transport: ${settings.transport}")
        if (settings.token.length < 16) add("Token must be at least 16 characters")
        if (settings.keepalivePeriod == null || settings.keepalivePeriod !in 1..3600) add("Keepalive must be between 1 and 3600 seconds")
        if (settings.transport.contains("mux")) {
            if (settings.heartbeat == null || settings.heartbeat !in 1..3600) add("Heartbeat must be between 1 and 3600 seconds")
            if (settings.muxConnections == null || settings.muxConnections !in 1..256) add("Mux connections/pool must be between 1 and 256")
        }
        when (settings.role) {
            BackhaulRole.SERVER -> {
                if (settings.bindPort == null || settings.bindPort !in 1..65535) add("Server bind port must be between 1 and 65535")
                if (settings.ports.isEmpty()) add("At least one forwarded port is required")
                settings.ports.forEach { port ->
                    val parsedPort = port.toIntOrNull()
                    if (parsedPort == null || parsedPort !in 1..65535) add("Only numeric destination ports from 1 to 65535 are supported in this first version")
                }
            }
            BackhaulRole.CLIENT -> {
                if (settings.remoteHost.isBlank() || settings.remoteHost.any { it.isWhitespace() || it == '/' || it == '@' }) add("Enter a hostname or IP address without scheme/path")
                if (settings.remotePort == null || settings.remotePort !in 1..65535) add("Remote port must be between 1 and 65535")
                if (settings.cloudflareProxy && !settings.remoteHost.contains('.')) add("Cloudflare-proxied setup requires a domain name")
                if (settings.cloudflareProxy && settings.transport !in setOf("ws", "wsmux")) add("For this Cloudflare HTTP/WebSocket template, select ws or wsmux and verify current Cloudflare port/transport support")
                if (settings.cloudflareProxy && settings.remotePort !in setOf(80, 8080, 8880, 2052, 2082, 2086, 2095, 443, 2053, 2083, 2087, 2096, 8443)) add("Port is not in the common Cloudflare proxied HTTP/HTTPS port list; verify current Cloudflare documentation")
            }
        }
    }

    fun generate(settings: BackhaulSettings): Result<String> {
        val errors = validate(settings)
        if (errors.isNotEmpty()) return Result.failure(IllegalArgumentException(errors.joinToString("\n")))
        val token = tomlString(settings.token)
        val transport = tomlString(settings.transport)
        val keepalive = settings.keepalivePeriod!!
        val lines = when (settings.role) {
            BackhaulRole.SERVER -> buildList {
                add("[server]")
                add("bind_addr = \":${settings.bindPort}\"")
                add("transport = $transport")
                add("accept_udp = false")
                add("token = $token")
                add("keepalive_period = $keepalive")
                add("nodelay = true")
                add("channel_size = 2048")
                if (settings.transport.contains("mux")) {
                    add("heartbeat = ${settings.heartbeat}")
                    add("mux_con = ${settings.muxConnections}")
                    add("mux_version = 2")
                    add("mux_framesize = 32768")
                    add("mux_recievebuffer = 4194304")
                    add("mux_streambuffer = 2000000")
                }
                add("sniffer = false")
                add("web_port = 0")
                add("log_level = \"info\"")
                add("ports = [")
                settings.ports.forEach { add("    ${tomlString(it)},") }
                add("]")
            }
            BackhaulRole.CLIENT -> buildList {
                add("[client]")
                add("remote_addr = \"${settings.remoteHost}:${settings.remotePort}\"")
                add("transport = $transport")
                add("token = $token")
                add("connection_pool = ${settings.muxConnections ?: 8}")
                add("aggressive_pool = false")
                add("keepalive_period = $keepalive")
                add("nodelay = true")
                add("retry_interval = 3")
                add("dial_timeout = 10")
                if (settings.transport.contains("mux")) {
                    add("mux_version = 2")
                    add("mux_framesize = 32768")
                    add("mux_recievebuffer = 4194304")
                    add("mux_streambuffer = 2000000")
                }
                add("sniffer = false")
                add("web_port = 0")
                add("log_level = \"info\"")
                if (settings.cloudflareProxy) {
                    add("")
                    add("# Cloudflare proxy is enabled for this domain in DNS.")
                    add("# Confirm this port is currently supported by Cloudflare and that the origin accepts WebSocket.")
                }
            }
        }
        return Result.success(lines.joinToString("\n") + "\n")
    }

    private fun tomlString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
}
