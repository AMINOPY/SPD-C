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
