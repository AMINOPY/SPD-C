package com.spdcon.app.config

import org.json.JSONArray
import org.json.JSONObject

/**
 * Generates a sing-box client configuration from the shared local profile model.
 *
 * This is a file/config generator only: it does not start a tunnel or verify the
 * remote endpoint. Unsupported transport combinations fail explicitly.
 */
object SingBoxConfigGenerator {
    fun supports(profile: ConfigProfile): Boolean =
        profile.protocol in setOf(
            ConfigProtocol.VLESS,
            ConfigProtocol.VMESS,
            ConfigProtocol.TROJAN,
            ConfigProtocol.SHADOWSOCKS,
            ConfigProtocol.HYSTERIA2,
            ConfigProtocol.TUIC
        ) && profile.network.lowercase() !in setOf("xhttp", "splithttp")

    fun generate(profile: ConfigProfile): Result<String> = runCatching {
        val errors = profile.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        require(supports(profile)) {
            "This sing-box exporter does not yet support XHTTP/SplitHTTP transport"
        }

        val outbound = JSONObject()
            .put("tag", "proxy")
            .put("server", profile.server.removePrefix("[").removeSuffix("]"))
            .put("server_port", profile.port)

        when (profile.protocol) {
            ConfigProtocol.VLESS -> {
                outbound.put("type", "vless").put("uuid", profile.credential)
                profile.parameters["flow"]?.takeIf { it.isNotBlank() }?.let { outbound.put("flow", it) }
            }
            ConfigProtocol.VMESS -> {
                outbound.put("type", "vmess")
                    .put("uuid", profile.credential)
                    .put("alter_id", profile.parameters["aid"]?.toIntOrNull() ?: 0)
                    .put("security", profile.parameters["scy"] ?: "auto")
            }
            ConfigProtocol.TROJAN -> outbound.put("type", "trojan").put("password", profile.credential)
            ConfigProtocol.SHADOWSOCKS -> outbound.put("type", "shadowsocks")
                .put("method", profile.parameters["method"] ?: "chacha20-ietf-poly1305")
                .put("password", profile.credential)
            ConfigProtocol.HYSTERIA2 -> outbound.put("type", "hysteria2").put("password", profile.credential)
            ConfigProtocol.TUIC -> outbound.put("type", "tuic")
                .put("uuid", profile.credential)
                .put("password", profile.parameters["password"] ?: error("TUIC password is required"))
                .put("congestion_control", profile.parameters["congestion_control"] ?: "bbr")
                .put("udp_relay_mode", profile.parameters["udp_relay_mode"] ?: "native")
        }

        if (profile.protocol != ConfigProtocol.SHADOWSOCKS) {
            val security = profile.security.lowercase()
            val tls = JSONObject().put("enabled", security == "tls" || security == "reality")
            if (security == "tls" || security == "reality") {
                profile.parameters["sni"]?.takeIf { it.isNotBlank() }?.let { tls.put("server_name", it) }
                profile.parameters["alpn"]?.takeIf { it.isNotBlank() }?.let { value ->
                    tls.put("alpn", JSONArray(value.split(",").map(String::trim).filter(String::isNotBlank)))
                }
                val fingerprint = (profile.parameters["fp"] ?: profile.parameters["fingerprint"])
                    ?.takeIf { it.isNotBlank() }
                if (fingerprint != null) {
                    tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", fingerprint))
                }
                if (security == "reality") {
                    val publicKey = profile.parameters["pbk"]
                        ?: error("REALITY public key (pbk) is required")
                    val shortId = profile.parameters["sid"]
                        ?: error("REALITY short ID (sid) is required; an empty value is allowed")
                    tls.put("reality", JSONObject()
                        .put("enabled", true)
                        .put("public_key", publicKey)
                        .put("short_id", shortId))
                }
            }
            outbound.put("tls", tls)
        } else if (profile.parameters["plugin"]?.isNotBlank() == true) {
            outbound.put("plugin", profile.parameters["plugin"])
            profile.parameters["plugin_opts"]?.takeIf { it.isNotBlank() }?.let {
                outbound.put("plugin_opts", it)
            }
        }

        when (profile.network.lowercase()) {
            "ws", "websocket" -> {
                val transport = JSONObject()
                    .put("type", "ws")
                    .put("path", profile.parameters["path"] ?: "/")
                profile.parameters["host"]?.takeIf { it.isNotBlank() }?.let {
                    transport.put("headers", JSONObject().put("Host", it))
                }
                outbound.put("transport", transport)
            }
            "grpc" -> outbound.put("transport", JSONObject()
                .put("type", "grpc")
                .put("service_name", profile.parameters["serviceName"]
                    ?: profile.parameters["service-name"] ?: ""))
            "tcp", "raw", "udp" -> Unit
            else -> error("Unsupported sing-box transport: ${profile.network}")
        }

        val root = JSONObject()
            .put("log", JSONObject().put("level", "warn"))
            .put("inbounds", JSONArray().put(
                JSONObject()
                    .put("type", "mixed")
                    .put("tag", "mixed-in")
                    .put("listen", "127.0.0.1")
                    .put("listen_port", 2080)
            ))
            .put("outbounds", JSONArray()
                .put(outbound)
                .put(JSONObject().put("type", "direct").put("tag", "direct"))
                .put(JSONObject().put("type", "block").put("tag", "block"))
            )
            .put("route", JSONObject().put("final", "proxy"))

        // Do not translate the Xray geosite:category-porn option into a partial
        // suffix list. A full sing-box rule-set mapping is required for parity.
        root.toString(2)
    }
}
