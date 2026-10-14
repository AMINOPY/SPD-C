package com.spdcon.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsResolverScannerTest {
    @Test fun validatesIpv4Candidates() {
        assertTrue(DnsResolverScanner.isIpv4("1.1.1.1"))
        assertFalse(DnsResolverScanner.isIpv4("256.1.1.1"))
        assertFalse(DnsResolverScanner.isIpv4("1.1.1"))
    }

    @Test fun validatesDnsDomain() {
        assertTrue(DnsResolverScanner.isValidDomain("resolver.example.com"))
        assertFalse(DnsResolverScanner.isValidDomain("https://example.com"))
        assertFalse(DnsResolverScanner.isValidDomain("bad..example.com"))
        assertFalse(DnsResolverScanner.isValidDomain("example.com/path"))
    }

    @Test fun defaultsAndLimitsAreBounded() {
        assertTrue(DnsResolverScanner.DEFAULT_RESOLVERS.isNotEmpty())
        assertTrue(DnsResolverScanner.DEFAULT_RESOLVERS.size <= DnsResolverScanner.MAX_CANDIDATES)
        assertTrue(DnsResolverScanner.MAX_CANDIDATES <= 256)
    }
}
