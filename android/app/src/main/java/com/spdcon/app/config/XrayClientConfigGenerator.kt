package com.spdcon.app.config

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a complete Xray client JSON document from an imported/saved profile.
 *
 * Xray-core does not implement Hysteria2 or TUIC; those profiles are rejected instead
 * of emitting a misleading, unusable Xray document. This creates a config file only:
 * it does not provision the server or start a VPN connection.
 */
object XrayClientConfigGenerator {
    private val supportedProtocols = setOf(
        ConfigProtocol.VLESS,
        ConfigProtocol.VMESS,
        ConfigProtocol.TROJAN,
        ConfigProtocol.SHADOWSOCKS
    )

    fun supports(profile: ConfigProfile): Boolean = profile.protocol in supportedProtocols

    fun generate(profile: ConfigProfile): Result<String> = runCatching {
        val errors = profile.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        require(supports(profile)) {
            "${profile.protocol} requires its own compatible core; Xray-core JSON supports VLESS, VMess, Trojan and Shadowsocks here"
        }

        val security = profile.security.lowercase()
        require(security in setOf("none", "tls", "reality")) { "Unsupported security: ${profile.security}" }
        require(security != "reality" || profile.protocol == ConfigProtocol.VLESS) {
            "REALITY is supported only for VLESS"
        }

        val outbound = JSONObject()
            .put("tag", "proxy")
            .put("protocol", when (profile.protocol) {
                ConfigProtocol.VLESS -> "vless"
                ConfigProtocol.VMESS -> "vmess"
                ConfigProtocol.TROJAN -> "trojan"
                ConfigProtocol.SHADOWSOCKS -> "shadowsocks"
                else -> error("Unsupported Xray protocol")
            })

        val endpoint = JSONObject().put("address", profile.server).put("port", profile.port)
        val user = when (profile.protocol) {
            ConfigProtocol.VLESS -> JSONObject()
                .put("id", profile.credential)
                .put("encryption", profile.parameters["encryption"] ?: "none")
                .apply { profile.parameters["flow"]?.takeIf { it.isNotBlank() }?.let { put("flow", it) } }
            ConfigProtocol.VMESS -> JSONObject()
                .put("id", profile.credential)
                .put("alterId", profile.parameters["aid"]?.toIntOrNull() ?: 0)
                .put("security", profile.parameters["scy"] ?: "auto")
            ConfigProtocol.TROJAN -> JSONObject().put("password", profile.credential)
            ConfigProtocol.SHADOWSOCKS -> JSONObject()
                .put("method", profile.parameters["method"] ?: "chacha20-ietf-poly1305")
                .put("password", profile.credential)
            else -> error("Unsupported Xray protocol")
        }

        when (profile.protocol) {
            ConfigProtocol.VLESS, ConfigProtocol.VMESS -> outbound.put(
                "settings",
                JSONObject().put("vnext", JSONArray().put(
                    endpoint.put("users", JSONArray().put(user))
                ))
            )
            ConfigProtocol.TROJAN, ConfigProtocol.SHADOWSOCKS -> outbound.put(
                "settings",
                JSONObject().put("servers", JSONArray().put(
                    endpoint.put("users", JSONArray().put(user))
                ))
            )
            else -> error("Unsupported Xray protocol")
        }

        if (profile.protocol != ConfigProtocol.SHADOWSOCKS) {
            val stream = JSONObject()
                .put("network", normalizeNetwork(profile.network))
                .put("security", security)
            when (security) {
                "tls" -> {
                    val tls = JSONObject()
                    profile.parameters["sni"]?.takeIf { it.isNotBlank() }?.let { tls.put("serverName", it) }
                    profile.parameters["alpn"]?.takeIf { it.isNotBlank() }?.let {
                        tls.put("alpn", JSONArray(it.split(",").map(String::trim).filter(String::isNotBlank))
                    }
                    if (profile.parameters["allowInsecure"] == "1" ||
                        profile.parameters["insecure"] == "1" ||
                        profile.parameters["allow_insecure"] == "1") {
                        tls.put("allowInsecure", true)
                    }
                    profile.parameters["fp"]?.takeIf { it.isNotBlank() }?.let { tls.put("fingerprint", it) }
                    stream.put("tlsSettings", tls)
                }
                "reality" -> {
                    val reality = profile.toRealitySettings()
                    val realityErrors = reality.validate()
                    require(realityErrors.isEmpty()) { realityErrors.joinToString("; ") }
                    stream.put("realitySettings", JSONObject()
                        .put("serverName", reality.serverName)
                        .put("password", reality.publicKey)
                        .put("shortId", reality.shortId)
                        .put("fingerprint", reality.fingerprint)
                        .put("spiderX", reality.spiderX))
                }
            }
            val transport = transportSettings(profile)
            if (transport != null) stream.put(transport.first, transport.second)
            outbound.put("streamSettings", stream)
        }

        val root = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("outbounds", JSONArray().put(outbound).put(
                JSONObject().put("tag", "direct").put("protocol", "freedom")
            ).put(
                JSONObject().put("tag", "block").put("protocol", "blackhole")
            ))
            .put("routing", JSONObject().put("domainStrategy", "AsIs").put(
                "rules", JSONArray().put(
                    JSONObject().put("type", "field")
                        .put("outboundTag", "direct")
                        .put("network", "udp")
                        .put("port", "0-65535")
                )
            ))

        if (profile.parameters["blockAdult"].equals("true", ignoreCase = true)) {
            root.getJSONObject("routing").getJSONArray("rules").put(
                JSONObject().put("type", "field")
                    .put("domain", JSONArray().put("geosite:category-porn"))
                    .put("outboundTag", "block")
            )
        }
        root.toString(2)
    }

    private fun normalizeNetwork(network: String): String = when (network.lowercase()) {
        "tcp", "raw" -> "raw"
        "ws", "websocket" -> "ws"
        "grpc" -> "grpc"
        "xhttp", "splithttp" -> "xhttp"
        else -> throw IllegalArgumentException("Unsupported Xray transport: $network")
    }

    private fun transportSettings(profile: ConfigProfile): Pair<String, JSONObject>? {
        val network = normalizeNetwork(profile.network)
        val parameters = profile.parameters
        return when (network) {
            "raw" -> {
                val headerType = parameters["headerType"] ?: parameters["type"]
                if (headerType.isNullOrBlank() || headerType == "none" || headerType == "tcp") null
                else "tcpSettings" to JSONObject().put("header", JSONObject().put("type", headerType))
            }
            "ws" -> {
                val headers = JSONObject()
                parameters["host"]?.takeIf { it.isNotBlank() }?.let { headers.put("Host", it) }
                "wsSettings" to JSONObject()
                    .put("path", parameters["path"] ?: "/")
                    .put("headers", headers)
            }
            "grpc" -> "grpcSettings" to JSONObject()
                .put("serviceName", parameters["serviceName"] ?: parameters["service-name"] ?: "")
                .put("multiMode", parameters["mode"].equals("multi", ignoreCase = true))
            "xhttp" -> "xhttpSettings" to JSONObject()
                .put("path", parameters["path"] ?: "/")
                .put("mode", parameters["mode"] ?: "auto")
        }
    }
}
