package com.spdcon.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramWebProxyLinkBuilderTest {
    @Test
    fun buildsTelegramProxyLinkAndEncodesValues() {
        val link = TelegramWebProxyLinkBuilder.build("proxy.example.com", "ee0123456789abcdef").getOrThrow()
        assertEquals(
            "https://t.me/proxy?server=proxy.example.com&secret=ee0123456789abcdef",
            link
        )
    }

    @Test
    fun rejectsMissingOrMalformedInputs() {
        assertTrue(TelegramWebProxyLinkBuilder.build("", "secret").isFailure)
        assertTrue(TelegramWebProxyLinkBuilder.build("https://proxy.example.com", "secret").isFailure)
        assertTrue(TelegramWebProxyLinkBuilder.build("proxy.example.com", "").isFailure)
    }

    @Test
    fun encodesReservedCharactersInSecret() {
        val link = TelegramWebProxyLinkBuilder.build("proxy.example.com", "a+b/c").getOrThrow()
        assertTrue(link.endsWith("secret=a%2Bb%2Fc"))
    }
}
