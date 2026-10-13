package com.spdcon.app.config

import libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject

/**
 * Uses the official XTLS/libXray native bridge, not a Kotlin-only schema approximation.
 * It constructs a disposable Xray instance, then probes the serialized outbound through Xray.
 * It does not start Android's VpnService or route device-wide traffic.
 */
data class XrayCoreTestResult(
    val coreVersion: String,
    val configConstructed: Boolean,
    val connected: Boolean,
    val latencyMs: Long?,
    val message: String
)

object XrayCoreTester {
    private const val API_VERSION = 3
    private const val TEST_URL = "https://cp.cloudflare.com/"
    private const val TIMEOUT_SECONDS = 8

    fun testProfile(profile: ConfigProfile): XrayCoreTestResult {
        val validationErrors = profile.validate()
        require(validationErrors.isEmpty()) { validationErrors.joinToString("; ") }

        val shareLink = ConfigLinkSerializer.serialize(profile).getOrElse {
            throw IllegalArgumentException(it.message ?: "Could not serialize this profile")
        }

        // Let libXray parse the actual share link into an outbound accepted by its bundled core.
        val conversion = invoke(
            method = "convertShareLinksToXrayJson",
            payload = JSONObject().put("text", shareLink)
        )
        ensureSuccess(conversion, "Xray could not convert this profile")
        val outbounds = conversion.optJSONObject("data")?.optJSONArray("outbounds")
            ?: throw IllegalStateException("Xray returned no outbound for this profile")
        require(outbounds.length() > 0) { "Xray did not accept this profile's protocol or parameters" }

        val xrayJson = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("outbounds", outbounds)
            .toString()

        // This invokes Xray-core's config loader and constructs/closes a real temporary core instance.
        val constructed = invoke(
            method = "testXray",
            payload = JSONObject().put("xrayJson", xrayJson)
        )
        ensureSuccess(constructed, "Xray-core rejected the generated configuration")

        // Then send a real HTTPS HEAD request through the selected outbound via Xray-core.
        val ping = invoke(
            method = "pingBatch",
            payload = JSONObject()
                .put("configs", JSONArray().put(JSONObject().put("xrayJson", xrayJson)))
                .put("timeout", TIMEOUT_SECONDS)
                .put("url", TEST_URL)
        )
        ensureSuccess(ping, "Xray connectivity probe could not start")
        val data = ping.optJSONObject("data")
            ?: throw IllegalStateException("Xray connectivity probe returned no result")
        val item = firstResultObject(data)
            ?: throw IllegalStateException("Xray connectivity probe returned an empty result")
        val delay = item.optLong("delay", 10_000L)
        val connected = item.optBoolean("success", delay in 0 until 10_000L)
        val coreVersion = runCatching { LibXray.xrayVersion().trim() }
            .getOrDefault("Xray-core")
            .ifBlank { "Xray-core" }

        return if (connected) {
            XrayCoreTestResult(
                coreVersion = coreVersion,
                configConstructed = true,
                connected = true,
                latencyMs = delay,
                message = "Xray-core accepted the config and the HTTPS probe succeeded."
            )
        } else {
            val detail = item.optString("error").take(240).ifBlank {
                if (delay == 11_000L) "Connection timed out" else "Outbound request failed"
            }
            XrayCoreTestResult(
                coreVersion = coreVersion,
                configConstructed = true,
                connected = false,
                latencyMs = null,
                message = "Xray-core accepted the config, but connectivity failed: $detail"
            )
        }
    }

    private fun invoke(method: String, payload: JSONObject): JSONObject {
        val request = JSONObject()
            .put("apiVersion", API_VERSION)
            .put("method", method)
            .put("payload", payload)
            .toString()
        return JSONObject(LibXray.invoke(request))
    }

    private fun ensureSuccess(response: JSONObject, fallback: String) {
        if (!response.optBoolean("success", false)) {
            val error = response.optString("error").take(400).ifBlank { fallback }
            throw IllegalStateException(error)
        }
    }

    private fun firstResultObject(data: JSONObject): JSONObject? {
        val keys = data.keys()
        while (keys.hasNext()) {
            when (val value = data.opt(keys.next())) {
                is JSONArray -> {
                    for (index in 0 until value.length()) {
                        val item = value.optJSONObject(index)
                        if (item != null) return item
                    }
                }
                is JSONObject -> firstResultObject(value)?.let { return it }
            }
        }
        return null
    }
}
