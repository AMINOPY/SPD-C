package com.spdcon.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionFailureClassifierTest {
    @Test
    fun identifiesDnsResolutionFailure() {
        val result = ConnectionFailureClassifier.classify("java.net.UnknownHostException: node.example.com")
        assertEquals(ConnectionFailureCategory.DNS_RESOLUTION, result.category)
        assertTrue(result.nextStepFa.contains("DNS"))
    }

    @Test
    fun identifiesUnreachableNodeWithoutClaimingFiltering() {
        val result = ConnectionFailureClassifier.classify("Connection refused")
        assertEquals(ConnectionFailureCategory.NODE_UNREACHABLE, result.category)
        assertTrue(result.nextStepFa.contains("ثابت"))
    }

    @Test
    fun labelsHandshakeFailuresAsNetworkOrFilteringPossible() {
        val result = ConnectionFailureClassifier.classify("TLS handshake EOF")
        assertEquals(ConnectionFailureCategory.NETWORK_OR_FILTERING, result.category)
        assertTrue(result.nextStepFa.contains("یکی از علت‌های ممکن"))
    }

    @Test
    fun leavesAmbiguousErrorsUnknown() {
        val result = ConnectionFailureClassifier.classify("something unexpected")
        assertEquals(ConnectionFailureCategory.UNKNOWN, result.category)
    }

    @Test
    fun explicitConfigPhaseTakesPrecedence() {
        val result = ConnectionFailureClassifier.classify("invalid config", "config")
        assertEquals(ConnectionFailureCategory.CONFIGURATION, result.category)
    }
}
