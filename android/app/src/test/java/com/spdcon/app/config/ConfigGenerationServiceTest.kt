package com.spdcon.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigGenerationServiceTest {
    private val userId = "00000000-0000-0000-0000-000000000123"

    @Test
    fun realityCreatesTcpAndXhttpUsingOneStableUserId() {
        val report = ConfigGenerationService.generateForUser(
            userId,
            "Amin",
            listOf(
                ConfigInboundSpec(
                    id = "reality-1",
                    name = "Reality",
                    kind = ConfigInboundKind.REALITY,
                    endpoint = "node.example.com",
                    sni = "www.example.com",
                    publicKey = "server-public-key",
                    shortId = "a1b2",
                    realityTransports = listOf("tcp", "xhttp"),
                    path = "/transport",
                    xhttpMode = "stream-up"
                )
            )
        ).getOrThrow()

        assertEquals(2, report.configs.size)
        assertEquals(userId, report.stableUserId)
        assertTrue(report.configs.all { it.profile.credential == userId })
        assertTrue(report.configs.any { it.profile.network == "tcp" && it.profile.security == "reality" })
        val xhttp = report.configs.single { it.profile.network == "xhttp" }
        assertEquals("/transport", xhttp.profile.parameters["path"])
        assertEquals("stream-up", xhttp.profile.parameters["mode"])
        assertTrue(xhttp.shareLink.contains("pbk=server-public-key"))
        assertTrue(xhttp.shareLink.contains("sid=a1b2"))
    }

    @Test
    fun tlsRelayBuildsWsAndXhttpAndKeepsDomainForSniOnIpVariants() {
        val report = ConfigGenerationService.generateForUser(
            userId,
            "Amin",
            listOf(
                ConfigInboundSpec(
                    id = "relay",
                    name = "Panel Relay",
                    kind = ConfigInboundKind.TLS_RELAY,
                    endpoint = "panel.example.com",
                    sni = "panel.example.com",
                    alternateAddresses = listOf("203.0.113.10"),
                    path = "/incorrect-shared-path"
                )
            )
        ).getOrThrow()

        assertEquals(4, report.configs.size)
        val ipVariant = report.configs.first { it.profile.server == "203.0.113.10" }
        assertEquals("panel.example.com", ipVariant.profile.parameters["sni"])
        assertEquals("panel.example.com", ipVariant.profile.parameters["host"])
        assertEquals("/ws/$userId", report.configs.first { it.profile.network == "ws" }.profile.parameters["path"])
        assertTrue(ipVariant.profile.parameters["path"]!!.contains(userId))
        assertTrue(report.configs.any { it.profile.network == "ws" })
        assertTrue(report.configs.any { it.profile.network == "xhttp" })
    }

    @Test
    fun tlsRelaySupportsIndependentCustomWebSocketPathTemplate() {
        val report = ConfigGenerationService.generateForUser(
            userId,
            "Amin",
            listOf(
                ConfigInboundSpec(
                    id = "relay-ws-path",
                    name = "Custom WS",
                    kind = ConfigInboundKind.TLS_RELAY,
                    endpoint = "relay.example.com",
                    sni = "relay.example.com",
                    wsPath = "/de/ws/{uuid}",
                    path = "/xhttp/{uuid}"
                )
            )
        ).getOrThrow()
        val ws = report.configs.single { it.profile.network == "ws" }
        val xhttp = report.configs.single { it.profile.network == "xhttp" }
        assertEquals("/de/ws/$userId", ws.profile.parameters["path"])
        assertEquals("/xhttp/$userId", xhttp.profile.parameters["path"])
    }

    @Test
    fun tlsRelayRejectsWebSocketPathWithoutUserPlaceholder() {
        val result = ConfigGenerationService.generateForUser(
            userId,
            "Amin",
            listOf(
                ConfigInboundSpec(
                    id = "relay-bad-ws-path",
                    name = "Bad WS",
                    kind = ConfigInboundKind.TLS_RELAY,
                    endpoint = "relay.example.com",
                    sni = "relay.example.com",
                    wsPath = "/shared"
                )
            )
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun tlsRelayHonorsCustomXhttpPathTemplateAndKeepsUserIdInPath() {
        val report = ConfigGenerationService.generateForUser(
            userId,
            "Amin",
            listOf(
                ConfigInboundSpec(
                    id = "relay-custom-path",
                    name = "Custom Relay",
                    kind = ConfigInboundKind.TLS_RELAY,
                    endpoint = "relay.example.com",
                    sni = "relay.example.com",
                    path = "/custom/{mode}/{uuid}",
                    xhttpMode = "stream-one"
                )
            )
        ).getOrThrow()
        val xhttp = report.configs.single { it.profile.network == "xhttp" }
        assertEquals("/custom/stream-one/$userId", xhttp.profile.parameters["path"])
    }

    @Test
    fun workerRoutesExpandStableUserIdAndOptionalKnownIp() {
        val report = ConfigGenerationService.generateForUser(
            userId,
            "Amin",
            listOf(
                ConfigInboundSpec(
                    id = "worker",
                    name = "CF Worker",
                    kind = ConfigInboundKind.CLOUDFLARE_WORKER,
                    endpoint = "spd.example.workers.dev",
                    host = "spd.example.workers.dev",
                    workerRoutes = listOf(
                        WorkerRoute("default", "/ws/{uuid}"),
                        WorkerRoute("DE", "/de/ws/{uuid}")
                    ),
                    alternateAddresses = listOf("198.51.100.12")
                )
            )
        ).getOrThrow()

        assertEquals(4, report.configs.size)
        assertTrue(report.configs.any { it.profile.parameters["path"] == "/ws/$userId" })
        assertTrue(report.configs.any { it.profile.parameters["path"] == "/de/ws/$userId" })
        assertTrue(report.configs.all { it.profile.credential == userId })
    }

    @Test
    fun rejectsRealityWithInvalidShortIdAndDoesNotInventKeys() {
        val result = ConfigGenerationService.generateForUser(
            userId,
            "Amin",
            listOf(
                ConfigInboundSpec(
                    id = "reality",
                    name = "Reality",
                    kind = ConfigInboundKind.REALITY,
                    endpoint = "node.example.com",
                    sni = "www.example.com",
                    publicKey = "",
                    shortId = "xyz"
                )
            )
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun adultFilterPolicyIsRecordedInGeneratedProfiles() {
        val report = ConfigGenerationService.generateForUser(
            userId,
            "Amin",
            listOf(
                ConfigInboundSpec(
                    id = "worker",
                    name = "Worker",
                    kind = ConfigInboundKind.CLOUDFLARE_WORKER,
                    endpoint = "spd.example.workers.dev",
                    host = "spd.example.workers.dev"
                )
            ),
            blockAdultSites = true
        ).getOrThrow()
        assertEquals("true", report.configs.single().profile.parameters["blockAdult"])
        // A share link cannot itself enforce a routing policy; the client JSON/runtime must apply it.
        assertFalse(report.configs.single().shareLink.contains("blockAdult"))
    }
    @Test
    fun acceptsEmptyRealityShortIdWhenServerExplicitlyConfiguresIt() {
        val report = ConfigGenerationService.generateForUser(
            userId,
            "Amin",
            listOf(
                ConfigInboundSpec(
                    id = "reality",
                    name = "Reality",
                    kind = ConfigInboundKind.REALITY,
                    endpoint = "node.example.com",
                    sni = "www.example.com",
                    publicKey = "server-public-key",
                    shortId = "",
                    realityTransports = listOf("tcp")
                )
            )
        ).getOrThrow()
        assertEquals("", report.configs.single().profile.parameters["sid"])
    }

    @Test
    fun rejectsOddLengthRealityShortId() {
        val result = ConfigGenerationService.generateForUser(
            userId,
            "Amin",
            listOf(
                ConfigInboundSpec(
                    id = "reality",
                    name = "Reality",
                    kind = ConfigInboundKind.REALITY,
                    endpoint = "node.example.com",
                    sni = "www.example.com",
                    publicKey = "server-public-key",
                    shortId = "abc",
                    realityTransports = listOf("tcp")
                )
            )
        )
        assertTrue(result.isFailure)
    }

}
