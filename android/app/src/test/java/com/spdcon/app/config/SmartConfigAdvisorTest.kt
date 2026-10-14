package com.spdcon.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartConfigAdvisorTest {
    @Test
    fun cloudflareSuggestionUsesWebSocketTlsOnSupportedDefaultPort() {
        val suggestion = SmartConfigAdvisor.suggest(SetupGoal.CLOUDFLARE_CDN)
        assertEquals(ConfigProtocol.VLESS, suggestion.protocol)
        assertEquals(443, suggestion.port)
        assertEquals("ws", suggestion.network)
        assertEquals("tls", suggestion.security)
        assertEquals("/", suggestion.path)
    }

    @Test
    fun udpSuggestionUsesHysteria2AndHttp3Alpn() {
        val suggestion = SmartConfigAdvisor.suggest(SetupGoal.UDP_SPEED)
        assertEquals(ConfigProtocol.HYSTERIA2, suggestion.protocol)
        assertEquals("udp", suggestion.network)
        assertEquals("h3", suggestion.alpn)
    }

    @Test
    fun advisorDoesNotPretendToGenerateRealityServerSecrets() {
        val suggestion = SmartConfigAdvisor.suggest(SetupGoal.STANDARD)
        assertFalse(suggestion.protocol == ConfigProtocol.VLESS && suggestion.security == "reality")
    }

    @Test
    fun hostnameHintRejectsIpAddressAndUrlPath() {
        assertTrue(SmartConfigAdvisor.looksLikeHostname("edge.example.com"))
        assertFalse(SmartConfigAdvisor.looksLikeHostname("192.0.2.1"))
        assertFalse(SmartConfigAdvisor.looksLikeHostname("https://edge.example.com/path"))
    }
}
