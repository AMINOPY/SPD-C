package com.spdcon.app.config

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.json.JSONObject

object ConfigLinkSerializer {
    fun serialize(profile: ConfigProfile): Result<String> = runCatching {
        val errors = profile.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        when (profile.protocol) {
            ConfigProtocol.VLESS -> serializeUriProfile(profile, "vless")
            ConfigProtocol.TROJAN -> serializeUriProfile(profile, "trojan")
            ConfigProtocol.SHADOWSOCKS -> serializeShadowsocks(profile)
            ConfigProtocol.VMESS -> serializeVmess(profile)
        }
    }

    private fun serializeUriProfile(profile: ConfigProfile, scheme: String): String {
        val query = linkedMapOf<String, String>()
        query["type"] = profile.network
        query["security"] = profile.security
        profile.parameters
            .filterKeys { it !in setOf("type", "network", "security") }
            .toSortedMap()
            .forEach { (key, value) -> if (value.isNotBlank()) query[key] = value }
        val queryText = query.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val remark = profile.remark ?: profile.name
        return "$scheme://${encodeUserInfo(profile.credential)}@${profile.server}:${profile.port}?$queryText#${encode(remark)}"
    }

    private fun serializeShadowsocks(profile: ConfigProfile): String {
        val method = profile.parameters["method"]
            ?: throw IllegalArgumentException("Shadowsocks encryption method is required")
        val userInfo = "$method:${profile.credential}@${profile.server}:${profile.port}"
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(userInfo.toByteArray(StandardCharsets.UTF_8))
        val remark = profile.remark ?: profile.name
        return "ss://$encoded#${encode(remark)}"
    }

    private fun serializeVmess(profile: ConfigProfile): String {
        val json = JSONObject()
            .put("v", "2")
            .put("ps", profile.remark ?: profile.name)
            .put("add", profile.server)
            .put("port", profile.port.toString())
            .put("id", profile.credential)
            .put("aid", profile.parameters["aid"] ?: "0")
            .put("scy", profile.parameters["scy"] ?: "auto")
            .put("net", profile.parameters["net"] ?: profile.network)
            .put("type", profile.parameters["type"] ?: "none")
            .put("host", profile.parameters["host"] ?: "")
            .put("path", profile.parameters["path"] ?: "")
            .put("tls", profile.parameters["tls"] ?: if (profile.security == "tls") "tls" else "")
        val encoded = Base64.getEncoder()
            .encodeToString(json.toString().toByteArray(StandardCharsets.UTF_8))
        return "vmess://$encoded"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun encodeUserInfo(value: String): String =
        encode(value).replace("%3A", ":")
}
