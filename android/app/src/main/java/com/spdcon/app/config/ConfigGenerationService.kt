package com.spdcon.app.config

import java.util.UUID

enum class ConfigInboundKind {
    REALITY,
    TLS_RELAY,
    CLOUDFLARE_WORKER
}

data class WorkerRoute(
    val label: String,
    /** Must be the actual route served by the deployed Worker. Use {uuid} as the user ID placeholder. */
    val pathTemplate: String
)

data class ConfigInboundSpec(
    val id: String,
    val name: String,
    val kind: ConfigInboundKind,
    val endpoint: String,
    val port: Int = 443,
    val sni: String = "",
    val host: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val fingerprint: String = "chrome",
    val spiderX: String = "/",
    val realityTransports: List<String> = listOf("tcp", "xhttp"),
    val xhttpMode: String = "stream-up",
    val path: String = "/",
    val alpn: String = "h2,http/1.1",
    val workerRoutes: List<WorkerRoute> = listOf(WorkerRoute("WS", "/ws/{uuid}")),
    /** Optional IPs already verified by an operator/scanner. This class does not scan the Internet. */
    val alternateAddresses: List<String> = emptyList()
)

data class GeneratedConfig(
    val inboundId: String,
    val label: String,
    val profile: ConfigProfile,
    val shareLink: String
)

data class ConfigGenerationReport(
    val stableUserId: String,
    val configs: List<GeneratedConfig>,
    val warnings: List<String>
)

/**
 * Creates client profiles from explicitly configured, server-backed inbounds.
 *
 * This is a profile/link generator, not a server provisioner, IP scanner, relay, or DPI test.
 * All paths, keys, ports and routes must match the deployed server/Worker configuration.
 */
object ConfigGenerationService {
    private val allowedRealityTransports = setOf("tcp", "xhttp")
    private val allowedXhttpModes = setOf("stream-up", "packet-up", "stream-one")

    fun newStableUserId(): String = UUID.randomUUID().toString()

    fun generateForUser(
        userId: String,
        displayName: String,
        inbounds: List<ConfigInboundSpec>,
        blockAdultSites: Boolean = false
    ): Result<ConfigGenerationReport> = runCatching {
        val stableId = UUID.fromString(userId).toString()
        require(displayName.isNotBlank()) { "User name is required" }
        require(inbounds.isNotEmpty()) { "At least one enabled inbound is required" }

        val configs = mutableListOf<GeneratedConfig>()
        val warnings = mutableListOf<String>()
        inbounds.forEach { inbound ->
            require(inbound.id.isNotBlank()) { "Inbound ID is required" }
            require(inbound.endpoint.isNotBlank() && !inbound.endpoint.any { it.isWhitespace() }) {
                "Inbound ${inbound.name}: endpoint is invalid"
            }
            require(inbound.port in 1..65535) { "Inbound ${inbound.name}: port is invalid" }
            val built = when (inbound.kind) {
                ConfigInboundKind.REALITY -> buildReality(inbound, stableId, displayName, blockAdultSites)
                ConfigInboundKind.TLS_RELAY -> buildTlsRelay(inbound, stableId, displayName, blockAdultSites)
                ConfigInboundKind.CLOUDFLARE_WORKER -> buildWorker(inbound, stableId, displayName, blockAdultSites)
            }
            configs += built
            if (inbound.alternateAddresses.isNotEmpty() && inbound.kind == ConfigInboundKind.REALITY) {
                warnings += "${inbound.name}: alternate IPs are ignored for REALITY unless each address is explicitly configured on the server."
            }
        }
        require(configs.isNotEmpty()) { "No valid client configurations could be generated" }
        ConfigGenerationReport(
            stableUserId = stableId,
            configs = configs,
            warnings = warnings.distinct()
        )
    }

    private fun buildReality(
        inbound: ConfigInboundSpec,
        userId: String,
        name: String,
        blockAdult: Boolean
    ): List<GeneratedConfig> {
        require(inbound.sni.isNotBlank()) { "${inbound.name}: REALITY SNI/serverName is required" }
        require(inbound.publicKey.isNotBlank()) { "${inbound.name}: server public key (pbk) is required" }
        require(inbound.shortId.matches(Regex("^(?:[0-9a-fA-F]{2}){0,8}$"))) {
            "${inbound.name}: shortId must be 0-16 hexadecimal characters with even length"
        }
        require(inbound.realityTransports.isNotEmpty()) { "${inbound.name}: select at least one REALITY transport" }
        require(inbound.realityTransports.all { it.lowercase() in allowedRealityTransports }) {
            "${inbound.name}: REALITY supports only TCP or XHTTP in this generator"
        }
        if (inbound.realityTransports.any { it.equals("xhttp", true) }) {
            require(inbound.xhttpMode in allowedXhttpModes) { "${inbound.name}: invalid XHTTP mode" }
        }
        return inbound.realityTransports.distinctBy { it.lowercase() }.map { transport ->
            val isXhttp = transport.equals("xhttp", true)
            val params = commonParameters(inbound, blockAdult).toMutableMap().apply {
                put("sni", inbound.sni)
                put("pbk", inbound.publicKey)
                put("sid", inbound.shortId)
                put("fp", inbound.fingerprint.ifBlank { "chrome" })
                put("spx", inbound.spiderX.ifBlank { "/" })
                if (isXhttp) {
                    put("path", inbound.path.ifBlank { "/" })
                    put("mode", inbound.xhttpMode)
                }
            }
            create(
                inbound, name, userId,
                label = if (isXhttp) "REALITY XHTTP" else "REALITY TCP",
                endpoint = inbound.endpoint,
                port = inbound.port,
                network = if (isXhttp) "xhttp" else "tcp",
                security = "reality",
                parameters = params
            )
        }
    }

    private fun buildTlsRelay(
        inbound: ConfigInboundSpec,
        userId: String,
        name: String,
        blockAdult: Boolean
    ): List<GeneratedConfig> {
        val tlsName = inbound.sni.ifBlank { inbound.host }
        require(tlsName.isNotBlank()) { "${inbound.name}: TLS relay needs its real domain for SNI" }
        val host = inbound.host.ifBlank { tlsName }
        require(inbound.xhttpMode in allowedXhttpModes) { "${inbound.name}: invalid XHTTP mode" }
        val configuredXhttpPath = inbound.path.trim()
        val xhttpTemplate = if (configuredXhttpPath.isBlank() || configuredXhttpPath == "/") {
            "/xhttp-siz10/{mode}/{uuid}"
        } else {
            configuredXhttpPath
        }
        require(xhttpTemplate.startsWith("/") && !xhttpTemplate.contains("..") &&
            !xhttpTemplate.any { it.isISOControl() }) {
            "${inbound.name}: TLS Relay XHTTP path must be an absolute safe path"
        }
        val xhttpPath = if (xhttpTemplate.contains("{uuid}")) {
            xhttpTemplate.replace("{mode}", inbound.xhttpMode).replace("{uuid}", userId)
        } else {
            xhttpTemplate.replace("{mode}", inbound.xhttpMode).trimEnd('/') + "/$userId"
        }
        val paths = listOf(
            "WS" to "/ws/$userId",
            "XHTTP" to xhttpPath
        )
        val endpoints = (listOf(inbound.endpoint) + inbound.alternateAddresses)
            .map(String::trim).filter(String::isNotBlank).distinct()
        return endpoints.flatMap { endpoint ->
            paths.map { (transport, path) ->
                val network = transport.lowercase()
                val params = commonParameters(inbound, blockAdult).toMutableMap().apply {
                    put("host", host)
                    put("sni", tlsName)
                    put("fp", inbound.fingerprint.ifBlank { "chrome" })
                    put("alpn", inbound.alpn)
                    put("path", path)
                    if (transport == "XHTTP") put("mode", inbound.xhttpMode)
                    if (endpoint != inbound.endpoint) put("ipVariant", "true")
                }
                create(
                    inbound, name, userId,
                    label = "TLS Relay $transport" + if (endpoint != inbound.endpoint) " (alternate IP)" else "",
                    endpoint = endpoint,
                    port = inbound.port,
                    network = network,
                    security = "tls",
                    parameters = params
                )
            }
        }
    }

    private fun buildWorker(
        inbound: ConfigInboundSpec,
        userId: String,
        name: String,
        blockAdult: Boolean
    ): List<GeneratedConfig> {
        require(inbound.sni.isNotBlank() || inbound.host.isNotBlank()) {
            "${inbound.name}: Worker domain is required for Host/SNI"
        }
        require(inbound.workerRoutes.isNotEmpty()) { "${inbound.name}: at least one deployed Worker route is required" }
        val host = inbound.host.ifBlank { inbound.sni.ifBlank { inbound.endpoint } }
        val sni = inbound.sni.ifBlank { host }
        val endpoints = (listOf(inbound.endpoint) + inbound.alternateAddresses)
            .map(String::trim).filter(String::isNotBlank).distinct()
        return endpoints.flatMap { endpoint ->
            inbound.workerRoutes.map { route ->
                require(route.pathTemplate.startsWith("/") && !route.pathTemplate.contains("..")) {
                    "${inbound.name}: Worker path must be an absolute safe path"
                }
                require(route.pathTemplate.contains("{uuid}")) {
                    "${inbound.name}: Worker route must include the {uuid} placeholder"
                }
                val params = commonParameters(inbound, blockAdult).toMutableMap().apply {
                    put("host", host)
                    put("sni", sni)
                    put("fp", inbound.fingerprint.ifBlank { "chrome" })
                    put("path", route.pathTemplate.replace("{uuid}", userId))
                    put("workerRoute", route.label)
                    if (endpoint != inbound.endpoint) put("ipVariant", "true")
                }
                create(
                    inbound, name, userId,
                    label = "Worker ${route.label}" + if (endpoint != inbound.endpoint) " (alternate IP)" else "",
                    endpoint = endpoint,
                    port = inbound.port,
                    network = "ws",
                    security = "tls",
                    parameters = params
                )
            }
        }
    }

    private fun commonParameters(inbound: ConfigInboundSpec, blockAdult: Boolean): Map<String, String> =
        buildMap {
            put("encryption", "none")
            put("blockAdult", blockAdult.toString())
        }

    private fun create(
        inbound: ConfigInboundSpec,
        name: String,
        userId: String,
        label: String,
        endpoint: String,
        port: Int,
        network: String,
        security: String,
        parameters: Map<String, String>
    ): GeneratedConfig {
        val profile = ConfigProfile(
            name = "$name • ${inbound.name} • $label",
            protocol = ConfigProtocol.VLESS,
            server = endpoint,
            port = port,
            credential = userId,
            network = network,
            security = security,
            remark = "$name • $label",
            parameters = parameters
        )
        val errors = profile.validate()
        require(errors.isEmpty()) { "${inbound.name}/$label: ${errors.joinToString("; ")}" }
        val link = ConfigLinkSerializer.serialize(profile).getOrThrow()
        return GeneratedConfig(inbound.id, label, profile, link)
    }
}
