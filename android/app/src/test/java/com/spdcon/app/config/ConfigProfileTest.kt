package com.spdcon.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigProfileTest {
    @Test
    fun validProfileHasNoValidationErrors() {
        val profile = ConfigProfile(
            name = "Home",
            protocol = ConfigProtocol.VLESS,
            server = "example.com",
            port = 443,
            credential = "00000000-0000-0000-0000-000000000000"
        )
        assertTrue(profile.validate().isEmpty())
    }

    @Test
    fun rejectsUrlInsteadOfBareServerAddress() {
        val profile = ConfigProfile(
            name = "Injected URL",
            protocol = ConfigProtocol.VLESS,
            server = "https://example.com/path?x=1",
            port = 443,
            credential = "00000000-0000-0000-0000-000000000000"
        )
        assertTrue(profile.validate().any { it.startsWith("Server must be a hostname") })
    }

    @Test
    fun acceptsBracketedIpv6Server() {
        val profile = ConfigProfile(
            name = "IPv6",
            protocol = ConfigProtocol.VLESS,
            server = "[2001:db8::1]",
            port = 443,
            credential = "00000000-0000-0000-0000-000000000000"
        )
        assertTrue(profile.validate().isEmpty())
    }

    @Test
    fun invalidPortIsReported() {
        val profile = ConfigProfile(
            name = "Bad",
            protocol = ConfigProtocol.TROJAN,
            server = "example.com",
            port = 70000,
            credential = "secret"
        )
        assertEquals(listOf("Port must be between 1 and 65535"), profile.validate())
    }
}
