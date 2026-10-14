package com.spdcon.app.config

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Small HTTPS client for optional Cloudflare/Config Hub sync.
 * All calls must run on Dispatchers.IO; passwords are never stored by this class.
 */
object CloudSyncClient {
    data class Session(val token: String, val username: String)
    data class SyncSummary(val uploaded: Int, val skipped: Int, val downloaded: Int)
    data class RemoteNode(
        val id: String, val name: String, val address: String, val port: Int,
        val country: String, val protocol: String, val network: String, val security: String,
        val sni: String, val publicKey: String, val shortId: String, val fingerprint: String,
        val enabled: Boolean = true
    )

    fun register(baseUrl: String, username: String, password: String) {
        request(baseUrl, "/api/v1/auth/register", "POST", body = JSONObject()
            .put("username", username).put("password", password))
    }

    fun login(baseUrl: String, username: String, password: String): Session {
        val response = request(baseUrl, "/api/v1/auth/login", "POST", body = JSONObject()
            .put("username", username).put("password", password))
        val token = response.optString("token")
        require(token.length == 64) { "پاسخ ورود معتبر نیست." }
        return Session(token, response.optJSONObject("user")?.optString("username") ?: username)
    }

    fun upload(baseUrl: String, token: String, profiles: List<ConfigProfile>): SyncSummary {
        var uploaded = 0
        var skipped = 0
        profiles.forEach { profile ->
            val payload = JSONObject()
                .put("name", profile.name)
                .put("protocol", profile.protocol.name.lowercase())
                .put("profile", profileToJson(profile))
            try {
                request(baseUrl, "/api/v1/configs", "POST", token, payload)
                uploaded++
            } catch (e: CloudApiException) {
                if (e.statusCode == 409) skipped++ else throw e
            }
        }
        return SyncSummary(uploaded = uploaded, skipped = skipped, downloaded = 0)
    }

    fun download(baseUrl: String, token: String, store: ConfigLibraryStore): SyncSummary {
        val response = request(baseUrl, "/api/v1/configs", "GET", token)
        val configs = response.optJSONArray("configs") ?: JSONArray()
        var downloaded = 0
        var skipped = 0
        for (index in 0 until configs.length()) {
            val item = configs.optJSONObject(index) ?: continue
            val profileJson = item.optJSONObject("profile") ?: continue
            val profile = runCatching { profileFromJson(profileJson) }.getOrNull()
            if (profile == null) {
                skipped++
            } else {
                val saved = store.save(profile)
                if (saved.isSuccess) downloaded++ else skipped++
            }
        }
        return SyncSummary(uploaded = 0, skipped = skipped, downloaded = downloaded)
    }

    fun listNodes(baseUrl: String, token: String, admin: Boolean = true): List<RemoteNode> {
        val path = if (admin) "/api/v1/admin/nodes" else "/api/v1/nodes"
        val response = request(baseUrl, path, "GET", token)
        val nodes = response.optJSONArray("nodes") ?: JSONArray()
        return buildList {
            for (index in 0 until nodes.length()) {
                val item = nodes.optJSONObject(index) ?: continue
                add(RemoteNode(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    address = item.optString("address"),
                    port = item.optInt("port", 443),
                    country = item.optString("country", "auto"),
                    protocol = item.optString("protocol", "vless"),
                    network = item.optString("network", "tcp"),
                    security = item.optString("security", "tls"),
                    sni = item.optString("sni"),
                    publicKey = item.optString("publicKey", item.optString("public_key")),
                    shortId = item.optString("shortId", item.optString("short_id")),
                    fingerprint = item.optString("fingerprint", "chrome"),
                    enabled = item.optBoolean("enabled", true)
                ))
            }
        }
    }

    fun createNode(baseUrl: String, token: String, node: RemoteNode): RemoteNode {
        val response = request(baseUrl, "/api/v1/admin/nodes", "POST", token, JSONObject()
            .put("name", node.name).put("address", node.address).put("port", node.port)
            .put("country", node.country).put("protocol", node.protocol).put("network", node.network)
            .put("security", node.security).put("sni", node.sni).put("publicKey", node.publicKey)
            .put("shortId", node.shortId).put("fingerprint", node.fingerprint))
        val item = response.optJSONObject("node") ?: throw IllegalStateException("پاسخ ثبت نود معتبر نیست.")
        return RemoteNode(
            id = item.optString("id"), name = item.optString("name"), address = item.optString("address"),
            port = item.optInt("port", 443), country = item.optString("country", "auto"),
            protocol = item.optString("protocol", "vless"), network = item.optString("network", "tcp"),
            security = item.optString("security", "tls"), sni = item.optString("sni"),
            publicKey = item.optString("publicKey"), shortId = item.optString("shortId"),
            fingerprint = item.optString("fingerprint", "chrome"), enabled = item.optBoolean("enabled", true)
        )
    }

    fun setNodeEnabled(baseUrl: String, token: String, nodeId: String, enabled: Boolean) {
        request(baseUrl, "/api/v1/admin/nodes/$nodeId", "PATCH", token, JSONObject().put("enabled", enabled))
    }

    fun deleteNode(baseUrl: String, token: String, nodeId: String) {
        request(baseUrl, "/api/v1/admin/nodes/$nodeId", "DELETE", token)
    }

    fun logout(baseUrl: String, token: String) {
        request(baseUrl, "/api/v1/auth/logout", "POST", token, JSONObject())
    }

    fun createSubscription(baseUrl: String, token: String): String {
        val response = request(baseUrl, "/api/v1/subscriptions", "POST", token, JSONObject())
        return response.optString("url").takeIf { it.startsWith("https://") }
            ?: throw IllegalStateException("لینک اشتراک امن از سرور دریافت نشد.")
    }

    private fun profileToJson(profile: ConfigProfile) = JSONObject()
        .put("name", profile.name)
        .put("protocol", profile.protocol.name)
        .put("server", profile.server)
        .put("port", profile.port)
        .put("credential", profile.credential)
        .put("network", profile.network)
        .put("security", profile.security)
        .put("remark", profile.remark)
        .put("parameters", JSONObject(profile.parameters))
        .put("tags", JSONArray(profile.tags))
        .put("favorite", profile.favorite)

    private fun profileFromJson(json: JSONObject): ConfigProfile {
        val params = json.optJSONObject("parameters") ?: JSONObject()
        val parameters = buildMap {
            val keys = params.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, params.optString(key))
            }
        }
        val tagArray = json.optJSONArray("tags") ?: JSONArray()
        val tags = buildList {
            for (index in 0 until tagArray.length()) {
                tagArray.optString(index).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
            }
        }
        val profile = ConfigProfile(
            name = json.optString("name"),
            protocol = ConfigProtocol.valueOf(json.optString("protocol").uppercase()),
            server = json.optString("server"),
            port = json.optInt("port", -1),
            credential = json.optString("credential"),
            network = json.optString("network", "tcp"),
            security = json.optString("security", "none"),
            remark = json.optString("remark").takeIf { it.isNotBlank() && it != "null" },
            parameters = parameters,
            tags = tags,
            favorite = json.optBoolean("favorite", false)
        )
        require(profile.validate().isEmpty()) { "پروفایل دریافتی معتبر نیست." }
        return profile
    }

    private fun request(
        baseUrl: String,
        path: String,
        method: String,
        token: String? = null,
        body: JSONObject? = null
    ): JSONObject {
        val base = baseUrl.trim().trimEnd('/')
        require(base.startsWith("https://") || base.startsWith("http://10.0.2.2") || base.startsWith("http://127.0.0.1")) {
            "برای همگام‌سازی آنلاین باید آدرس HTTPS وارد شود."
        }
        val connection = URL(base + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
            }.orEmpty()
            val parsed = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (status !in 200..299) {
                throw CloudApiException(status, parsed.optString("error").ifBlank { "خطای سرور ($status)" })
            }
            return parsed
        } finally {
            connection.disconnect()
        }
    }

    class CloudApiException(val statusCode: Int, message: String) : IllegalStateException(message)
}
