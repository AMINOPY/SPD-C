package com.spdcon.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanIpScannerTest {
    @Test fun parsesAndDeduplicatesIps() {
        assertEquals(listOf("104.16.0.1", "104.16.0.2"),
            CleanIpScanner.parseCandidates("104.16.0.1, 104.16.0.2\n104.16.0.1"))
    }
    @Test fun expandsSmallCidrAndRange() {
        assertEquals(listOf("192.0.2.0", "192.0.2.1", "192.0.2.2", "192.0.2.3"),
            CleanIpScanner.parseCandidates("192.0.2.0/30"))
        assertEquals(listOf("192.0.2.4", "192.0.2.5", "192.0.2.6"),
            CleanIpScanner.parseCandidates("192.0.2.4-192.0.2.6"))
    }
    @Test fun capsLargeRangesAndRejectsBroadCidrs() {
        assertEquals(256, CleanIpScanner.parseCandidates("192.0.0.0/16").size)
        assertTrue(runCatching { CleanIpScanner.parseCandidates("10.0.0.0/8") }.isFailure)
    }
    @Test fun rejectsInvalidAddressesAndPorts() {
        assertTrue(runCatching { CleanIpScanner.parseCandidates("999.1.1.1") }.isFailure)
        assertTrue(runCatching { CleanIpScanner.scan(listOf("104.16.0.1"), 22, "example.com") }.isFailure)
    }
    @Test fun validatesSniAsHostnameNotUrl() {
        assertTrue(CleanIpScanner.isValidSni("cdn.example.com"))
        assertFalse(CleanIpScanner.isValidSni("https://cdn.example.com/path"))
        assertFalse(CleanIpScanner.isValidSni("bad domain.example"))
    }
}
