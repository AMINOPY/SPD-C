package com.spdcon.app.config

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import org.bouncycastle.math.ec.rfc7748.X25519

/**
 * Offline-only material generator.
 *
 * All values are created in-process with the operating system's SecureRandom.
 * This class performs no network calls and writes nothing to disk. Callers decide
 * whether to display, copy, or share the returned values; they should not persist
 * the returned private key unless they explicitly choose a secure server setup flow.
 *
 * A generated key pair/UUID does not provision a remote server by itself. A usable
 * REALITY link requires the corresponding private key and UUID to be configured on
 * the destination server and a real serverName/destination chosen by its operator.
 */
object OfflineConfigBuilderEngine {
    private val random = SecureRandom()

    data class RealityKeyPair(
        /** URL-safe Base64 without padding, suitable for Xray REALITY privateKey. */
        val privateKey: String,
        /** URL-safe Base64 without padding, suitable for Xray REALITY publicKey/pbk. */
        val publicKey: String
    )

    data class GeneratedMaterial(
        val clientUuid: String,
        val realityShortId: String,
        val realityKeyPair: RealityKeyPair
    )

    /**
     * Validates and serializes any currently supported protocol using local code only.
     * No API/server call and no library/database write occurs here.
     */
    fun buildShareLink(profile: ConfigProfile): Result<String> = runCatching {
        val errors = profile.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        ConfigLinkSerializer.serialize(profile).getOrThrow()
    }

    /** Creates a UUID v4 using SecureRandom without persisting it. */
    fun generateUuid(): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        return UUID(buffer.long, buffer.long).toString()
    }

    /** Generates X25519 keys locally for server-side REALITY setup. */
    fun generateRealityKeyPair(): RealityKeyPair {
        val privateBytes = ByteArray(X25519.POINT_SIZE)
        val publicBytes = ByteArray(X25519.POINT_SIZE)
        try {
            X25519.generatePrivateKey(random, privateBytes)
            X25519.generatePublicKey(privateBytes, 0, publicBytes, 0)
            val encoder = Base64.getUrlEncoder().withoutPadding()
            return RealityKeyPair(
                privateKey = encoder.encodeToString(privateBytes),
                publicKey = encoder.encodeToString(publicBytes)
            )
        } finally {
            privateBytes.fill(0)
            publicBytes.fill(0)
        }
    }

    /** REALITY short IDs are random even-length hex, between 0 and 16 chars. */
    fun generateRealityShortId(byteLength: Int = 8): String {
        require(byteLength in 0..8) { "REALITY short ID must be between 0 and 8 bytes" }
        val bytes = ByteArray(byteLength).also(random::nextBytes)
        return try {
            bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        } finally {
            bytes.fill(0)
        }
    }

    fun generateMaterial(): GeneratedMaterial = GeneratedMaterial(
        clientUuid = generateUuid(),
        realityShortId = generateRealityShortId(),
        realityKeyPair = generateRealityKeyPair()
    )

    /**
     * Builds a VLESS + REALITY share link entirely in memory.
     * The caller must supply the actual server address and server-configured SNI.
     * The link's UUID/public key must match the server configuration before it connects.
     */
    fun buildVlessRealityLink(
        server: String,
        port: Int,
        uuid: String,
        serverName: String,
        publicKey: String,
        shortId: String,
        fingerprint: String = "chrome",
        flow: String = "",
        name: String = "Spd con"
    ): String {
        require(server.isNotBlank() && !server.any { it.isWhitespace() || it in "/?#@" }) {
            "A server hostname or IP is required"
        }
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        require(runCatching { UUID.fromString(uuid) }.isSuccess) { "A valid UUID is required" }
        require(serverName.isNotBlank() && !serverName.any { it.isWhitespace() || it in "/?#@" }) {
            "REALITY serverName/SNI must be a hostname configured on the server"
        }
        require(publicKey.isNotBlank()) { "REALITY public key is required" }
        require(shortId.matches(Regex("^(?:[0-9a-fA-F]{2}){0,8}$"))) {
            "REALITY short ID must be 0-16 hexadecimal characters with even length"
        }
        require(fingerprint.matches(Regex("^[a-zA-Z0-9._-]{1,32}$"))) { "Invalid fingerprint token" }

        fun enc(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

        val host = if (server.contains(':') && !server.startsWith("[")) "[$server]" else server
        val query = buildList {
            add("encryption=none")
            add("security=reality")
            add("sni=${enc(serverName)}")
            add("fp=${enc(fingerprint)}")
            add("pbk=${enc(publicKey)}")
            add("sid=${enc(shortId)}")
            add("type=tcp")
            if (flow.isNotBlank()) add("flow=${enc(flow)}")
        }.joinToString("&")
        return "vless://$uuid@$host:$port?$query#${enc(name)}"
    }
}
