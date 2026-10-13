package com.spdcon.app.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigGeneratorTest {
    private fun profile(blockAdult: Boolean) = ConfigProfile(
        name = "test",
        protocol = ConfigProtocol.VLESS,
        server = "example.com",
        port = 443,
        credential = "00000000-0000-0000-0000-000000000001",
        network = "tcp",
        security = "reality",
        parameters = mapOf(
            "blockAdult" to blockAdult.toString(),
            "sni" to "example.com",
            "pbk" to "public-key",
            "sid" to "abcd"
        )
    )

    private val reality = RealitySettings(
        serverName = "example.com",
        publicKey = "public-key",
        shortId = "abcd",
        fingerprint = "chrome",
        spiderX = "/"
    )

    @Test
    fun adultFilterAddsDomainRoutingAndBlackhole() {
        val json = XrayConfigGenerator.generateVlessRealityOutbound(profile(true), reality).getOrThrow()
        val root = JSONObject(json)
        assertTrue(root.getJSONArray("outbounds").toString().contains("blackhole"))
        val rule = root.getJSONObject("routing").getJSONArray("rules").getJSONObject(0)
        assertEquals("geosite:category-porn", rule.getJSONArray("domain").getString(0))
        assertEquals("block", rule.getString("outboundTag"))
    }

    @Test
    fun adultFilterIsAbsentByDefault() {
        val root = JSONObject(XrayConfigGenerator.generateVlessRealityOutbound(profile(false), reality).getOrThrow())
        assertTrue(!root.has("routing"))
        assertEquals(1, root.getJSONArray("outbounds").length())
    }
}
