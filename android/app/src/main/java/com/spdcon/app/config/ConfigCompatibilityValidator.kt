package com.spdcon.app.config

/**
 * Shared, protocol-aware compatibility checks for the local config builder.
 *
 * This validates combinations that can be known from a profile alone. It cannot
 * prove that a remote server is configured with matching credentials or transports.
 */
internal object ConfigCompatibilityValidator {
    private val xrayNetworks = setOf("tcp", "raw", "ws", "websocket", "grpc", "xhttp", "splithttp")

    fun validate(profile: ConfigProfile): List<String> = buildList {
        val protocol = profile.protocol
        val network = profile.network.lowercase()
        val security = profile.security.lowercase()
        val parameters = profile.parameters

        if (protocol == ConfigProtocol.SHADOWSOCKS) {
            if (security != "none") add("Shadowsocks export must use protocol-native encryption with security=none")
            return@buildList
        }

        if (protocol == ConfigProtocol.HYSTERIA2 || protocol == ConfigProtocol.TUIC) {
            if (network != "udp") add("$protocol requires UDP transport")
            if (security != "tls") add("$protocol requires TLS security")
            if (security == "reality") add("$protocol cannot use Xray REALITY settings")
            return@buildList
        }

        if (network !in xrayNetworks) {
            add("Unsupported transport for this protocol: ${profile.network}")
            return@buildList
        }

        if (security == "reality") {
            if (protocol != ConfigProtocol.VLESS) add("REALITY export is currently supported only for VLESS")
            if (network !in setOf("tcp", "raw", "xhttp", "splithttp", "grpc")) {
                add("Xray REALITY requires RAW/TCP, XHTTP or gRPC transport; WS is incompatible")
            }
            val fingerprint = parameters["fp"] ?: parameters["fingerprint"]
            if (fingerprint.equals("unsafe", ignoreCase = true)) {
                add("REALITY does not support the unsafe/native TLS fingerprint")
            }
        }

        val flow = parameters["flow"].orEmpty()
        if (flow.isNotBlank()) {
            if (protocol != ConfigProtocol.VLESS) {
                add("VLESS flow settings cannot be used with $protocol")
            }
            if (!flow.equals("xtls-rprx-vision", ignoreCase = true)) {
                add("Unsupported VLESS flow: $flow")
            }
            if (security != "reality" || network !in setOf("tcp", "raw")) {
                add("XTLS Vision flow requires VLESS with REALITY over RAW/TCP")
            }
        }

        val fingerprint = (parameters["fp"] ?: parameters["fingerprint"]).orEmpty()
        if (fingerprint.isNotBlank() &&
            (fingerprint.length > 64 || fingerprint.any { it.isWhitespace() || it.isISOControl() })) {
            add("TLS fingerprint must be a single valid fingerprint token")
        }

        val mode = parameters["mode"]
        if (!mode.isNullOrBlank() && network in setOf("xhttp", "splithttp") &&
            mode !in setOf("auto", "packet-up", "stream-up", "stream-one")) {
            add("Unsupported XHTTP mode: $mode")
        }

        val mux = parameters["muxConcurrency"] ?: parameters["mux"]
        if (!mux.isNullOrBlank()) {
            val muxValue = mux.toIntOrNull()
            if (muxValue == null) {
                add("Mux concurrency must be an integer")
            } else if (muxValue !in 0..1024) {
                add("Mux concurrency must be between 0 and 1024")
            }
        }
    }
}
