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

    @Test
    fun realityOutboundUsesCurrentPublicKeyFieldAndNormalizesTcpToRaw() {
        val root = JSONObject(
            XrayConfigGenerator.generateVlessRealityOutbound(profile(false), reality).getOrThrow()
        )
        val outbound = root.getJSONArray("outbounds").getJSONObject(0)
        val stream = outbound.getJSONObject("streamSettings")
        val settings = stream.getJSONObject("realitySettings")

        assertEquals("vless", outbound.getString("protocol"))
        assertEquals("raw", stream.getString("network"))
        assertEquals("reality", stream.getString("security"))
        assertEquals("public-key", settings.getString("password"))
        assertEquals("example.com", settings.getString("serverName"))
        assertEquals("abcd", settings.getString("shortId"))
        assertEquals("chrome", settings.getString("fingerprint"))
    }

    @Test
    fun realityOutboundRejectsUnsupportedTransportInsteadOfGuessing() {
        val unsupported = profile(false).copy(network = "ws")
        val result = XrayConfigGenerator.generateVlessRealityOutbound(unsupported, reality)
        assertTrue(result.isFailure)
    }

    @Test
    fun realityOutboundIncludesFlowOnlyWhenConfigured() {
        val vision = reality.copy(flow = "xtls-rprx-vision")
        val root = JSONObject(
            XrayConfigGenerator.generateVlessRealityOutbound(profile(false), vision).getOrThrow()
        )
        val user = root.getJSONArray("outbounds")
            .getJSONObject(0)
            .getJSONObject("settings")
            .getJSONArray("vnext")
            .getJSONObject(0)
            .getJSONArray("users")
            .getJSONObject(0)
        assertEquals("xtls-rprx-vision", user.getString("flow"))
    }
}
