package com.spdcon.app.config

import org.json.JSONArray
import org.json.JSONObject

/**
 * Generates a client-side Xray JSON fragment for review/export.
 * It does not install or run Xray, and does not guarantee reachability on any carrier.
 */
object XrayConfigGenerator {
    fun generateVlessRealityOutbound(
        profile: ConfigProfile,
        reality: RealitySettings,
        fragment: FragmentSettings = FragmentSettings()
    ): Result<String> = runCatching {
        require(profile.protocol == ConfigProtocol.VLESS) { "REALITY outbound generator currently supports VLESS only" }
        require(profile.validate().isEmpty()) { profile.validate().joinToString("; ") }
        require(profile.security.equals("reality", ignoreCase = true)) { "Profile security must be REALITY" }
        require(reality.validate().isEmpty()) { reality.validate().joinToString("; ") }
        require(fragment.validate().isEmpty()) { fragment.validate().joinToString("; ") }

        val user = JSONObject()
            .put("id", profile.credential)
            .put("encryption", "none")
        if (reality.flow.isNotBlank()) user.put("flow", reality.flow)

        val vnext = JSONObject()
            .put("address", profile.server)
            .put("port", profile.port)
            .put("users", JSONArray().put(user))

        val realitySettings = JSONObject()
            .put("fingerprint", reality.fingerprint)
            .put("serverName", reality.serverName)
            .put("password", reality.publicKey)
            .put("shortId", reality.shortId)
            .put("spiderX", reality.spiderX)

        val realityNetwork = when (profile.network.lowercase()) {
            "tcp" -> "raw" // Share links commonly spell Xray RAW transport as type=tcp.
            else -> profile.network.lowercase()
        }
        require(realityNetwork in setOf("raw", "xhttp", "grpc")) {
            "Xray REALITY supports RAW/TCP, XHTTP or gRPC transport; got: ${profile.network}"
        }

        val streamSettings = JSONObject()
            .put("network", realityNetwork)
            .put("security", "reality")
            .put("realitySettings", realitySettings)

        val vlessOutbound = JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", streamSettings)

        val root = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("outbounds", JSONArray().put(vlessOutbound))

        val fragmentSettings = fragment.toXrayFreedomSettings()
        if (fragmentSettings != null) {
            val fragmentObject = JSONObject()
                .put("packets", fragment.packets)
                .put("length", fragment.length)
                .put("interval", fragment.interval)
            root.getJSONArray("outbounds").put(
                JSONObject()
                    .put("tag", "fragment-direct")
                    .put("protocol", "freedom")
                    .put("settings", JSONObject().put("fragment", fragmentObject))
            )
        }

        root.toString(2)
    }
}
