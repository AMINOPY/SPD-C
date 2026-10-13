package com.spdcon.app.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedTransportSettingsTest {
    @Test
    fun carrierPresetsUseValidFragmentRanges() {
        NetworkPreset.entries.forEach { preset ->
            assertTrue("${preset.name} should have valid ranges", preset.fragment.validate().isEmpty())
        }
    }

    @Test
    fun realityRejectsOddLengthShortId() {
        val settings = RealitySettings(
            serverName = "example.com",
            publicKey = "public-key",
            shortId = "abc",
            fingerprint = "chrome"
        )
        assertFalse(settings.validate().isEmpty())
    }

    @Test
    fun realityAcceptsEvenHexShortId() {
        val settings = RealitySettings(
            serverName = "example.com",
            publicKey = "public-key",
            shortId = "a1b2c3",
            fingerprint = "chrome",
            spiderX = "/"
        )
        assertTrue(settings.validate().isEmpty())
    }

    @Test
    fun fragmentRejectsReversedRange() {
        val settings = FragmentSettings(enabled = true, length = "200-100", interval = "10-20")
        assertFalse(settings.validate().isEmpty())
    }
}
