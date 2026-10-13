package com.spdcon.app.config

import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineConfigBuilderEngineTest {
    @Test
    fun shareLinkSerializationUsesOnlyLocalProfileData() {
        val profile = ConfigProfile(
            name = "offline",
            protocol = ConfigProtocol.VLESS,
            server = "example.com",
            port = 443,
            credential = "00000000-0000-4000-8000-000000000001",
            network = "ws",
            security = "tls",
            parameters = mapOf("sni" to "example.com", "path" to "/ws")
        )
        val link = OfflineConfigBuilderEngine.buildShareLink(profile).getOrThrow()
        assertTrue(link.startsWith("vless://"))
        assertTrue(link.contains("example.com"))
        assertTrue(link.contains("security=tls"))
    }

    @Test
    fun generatedUuidIsValidV4AndNotPersistedByEngine() {
        val first = OfflineConfigBuilderEngine.generateUuid()
        val second = OfflineConfigBuilderEngine.generateUuid()
        assertEquals(4, UUID.fromString(first).version())
        assertNotEquals(first, second)
    }

    @Test
    fun generatedRealityKeysHaveExpectedX25519LengthAndDistinctValues() {
        val keys = OfflineConfigBuilderEngine.generateRealityKeyPair()
        val decoder = Base64.getUrlDecoder()
        assertEquals(32, decoder.decode(keys.privateKey).size)
        assertEquals(32, decoder.decode(keys.publicKey).size)
        assertNotEquals(keys.privateKey, keys.publicKey)
    }

    @Test
    fun generatedShortIdIsValidHex() {
        val shortId = OfflineConfigBuilderEngine.generateRealityShortId()
        assertEquals(16, shortId.length)
        assertTrue(shortId.matches(Regex("^[0-9a-f]{16}$")))
    }

    @Test
    fun realityLinkIsBuiltLocallyWithEncodedParametersAndIpv6Support() {
        val link = OfflineConfigBuilderEngine.buildVlessRealityLink(
            server = "2001:db8::1",
            port = 443,
            uuid = "00000000-0000-4000-8000-000000000001",
            serverName = "example.com",
            publicKey = "public_key-123",
            shortId = "a1b2",
            flow = "xtls-rprx-vision",
            name = "offline test"
        )
        assertTrue(link.startsWith("vless://00000000-0000-4000-8000-000000000001@[2001:db8::1]:443?"))
        assertTrue(link.contains("sni=example.com"))
        assertTrue(link.contains("pbk=public_key-123"))
        assertTrue(link.endsWith("#offline%20test"))
    }

    @Test
    fun invalidShortIdIsRejected() {
        val result = runCatching {
            OfflineConfigBuilderEngine.buildVlessRealityLink(
                server = "example.com",
                port = 443,
                uuid = "00000000-0000-4000-8000-000000000001",
                serverName = "example.com",
                publicKey = "public",
                shortId = "xyz"
            )
        }
        assertTrue(result.isFailure)
    }
}
