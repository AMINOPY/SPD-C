package com.spdcon.app.ui

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ScanHistorySummary(
    val timestampMs: Long,
    val mode: String,
    val source: String,
    val countryCode: String,
    val total: Int,
    val successful: Int
)

/** Stores bounded, complete per-candidate results for the most recent 30 scans. */
object ScanHistoryStore {
    private const val KEY = "scan_history_v1"
    private const val MAX_RUNS = 30

    fun record(
        prefs: SharedPreferences,
        mode: String,
        source: String,
        countryCode: String,
        tlsResults: List<CleanIpResult>,
        dnsResults: List<DnsResolverResult>
    ) {
        val results = JSONArray()
        if (mode == "dns") {
            dnsResults.forEach { result ->
                results.put(JSONObject()
                    .put("ip", result.ip)
                    .put("latencyMs", result.latencyMs)
                    .put("score", result.score)
                    .put("maxScore", result.maxScore)
                    .put("status", result.status)
                    .put("transport", result.transport))
            }
        } else {
            tlsResults.forEach { result ->
                results.put(JSONObject()
                    .put("ip", result.ip)
                    .put("latencyMs", result.latencyMs)
                    .put("tlsOk", result.tlsOk)
                    .put("detail", result.detail))
            }
        }
        val entry = JSONObject()
            .put("timestampMs", System.currentTimeMillis())
            .put("mode", mode)
            .put("source", source)
            .put("countryCode", if (source == "country") countryCode.uppercase() else "")
            .put("results", results)
        val old = runCatching { JSONArray(prefs.getString(KEY, "[]") ?: "[]") }.getOrDefault(JSONArray())
        val next = JSONArray().put(entry)
        for (index in 0 until minOf(old.length(), MAX_RUNS - 1)) {
            old.optJSONObject(index)?.let { next.put(it) }
        }
        prefs.edit().putString(KEY, next.toString()).apply()
    }

    fun load(prefs: SharedPreferences): List<ScanHistorySummary> {
        val array = runCatching { JSONArray(prefs.getString(KEY, "[]") ?: "[]") }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val results = entry.optJSONArray("results") ?: JSONArray()
            var successful = 0
            for (resultIndex in 0 until results.length()) {
                val result = results.optJSONObject(resultIndex) ?: continue
                if (entry.optString("mode") == "dns") {
                    if (result.optInt("score", 0) >= 2) successful++
                } else if (result.optBoolean("tlsOk", false)) successful++
            }
            ScanHistorySummary(
                timestampMs = entry.optLong("timestampMs"),
                mode = entry.optString("mode", "tls"),
                source = entry.optString("source", "default"),
                countryCode = entry.optString("countryCode"),
                total = results.length(),
                successful = successful
            )
        }
    }

    fun exportJson(prefs: SharedPreferences): String = prefs.getString(KEY, "[]") ?: "[]"

    fun clear(prefs: SharedPreferences) {
        prefs.edit().remove(KEY).apply()
    }
}
