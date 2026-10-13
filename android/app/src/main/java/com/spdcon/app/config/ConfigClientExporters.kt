package com.spdcon.app.config

import java.nio.charset.StandardCharsets
import java.util.Base64

enum class ConfigExportTarget { V2RAYNG, HIDDIFY }

data class ConfigExportResult(
    val target: ConfigExportTarget,
    val fileName: String,
    val mimeType: String,
    val text: String,
    val profileCount: Int
)

/**
 * Produces client-importable share links/subscription text. This does not install a
 * client, publish a subscription URL, provision a server, or test connectivity.
 */
object ConfigClientExporters {
    private val v2rayNgProtocols = setOf(
        ConfigProtocol.VLESS,
        ConfigProtocol.VMESS,
        ConfigProtocol.TROJAN,
        ConfigProtocol.SHADOWSOCKS
    )

    fun supports(target: ConfigExportTarget, profile: ConfigProfile): Boolean = when (target) {
        ConfigExportTarget.V2RAYNG -> profile.protocol in v2rayNgProtocols
        ConfigExportTarget.HIDDIFY -> profile.protocol in ConfigProtocol.entries
    }

    fun export(target: ConfigExportTarget, profiles: List<ConfigProfile>, base64Subscription: Boolean = false): Result<ConfigExportResult> = runCatching {
        require(profiles.isNotEmpty()) { "At least one profile is required for export" }
        require(profiles.size <= 500) { "Export is limited to 500 profiles at a time" }
        val uniqueProfiles = profiles.distinct()
        require(uniqueProfiles.isNotEmpty()) { "No unique profiles to export" }

        val unsupported = uniqueProfiles.filterNot { supports(target, it) }
        require(unsupported.isEmpty()) {
            val protocols = unsupported.map { it.protocol.name }.distinct().joinToString(", ")
            when (target) {
                ConfigExportTarget.V2RAYNG -> "v2rayNG export does not support these selected protocols: $protocols"
                ConfigExportTarget.HIDDIFY -> "Hiddify export does not support these selected protocols: $protocols"
            }
        }

        val links = uniqueProfiles.map { profile ->
            ConfigLinkSerializer.serialize(profile).getOrElse {
                throw IllegalArgumentException("${profile.name}: ${it.message ?: "invalid profile"}")
            }
        }
        val plainText = links.joinToString("\n")
        val output = if (base64Subscription) {
            Base64.getEncoder().encodeToString(plainText.toByteArray(StandardCharsets.UTF_8))
        } else {
            plainText
        }
        ConfigExportResult(
            target = target,
            fileName = when (target) {
                ConfigExportTarget.V2RAYNG -> "spdcon-v2rayng.txt"
                ConfigExportTarget.HIDDIFY -> "spdcon-hiddify-subscription.txt"
            },
            mimeType = "text/plain; charset=utf-8",
            text = output,
            profileCount = uniqueProfiles.size
        )
    }

    fun exportSingle(target: ConfigExportTarget, profile: ConfigProfile): Result<ConfigExportResult> =
        export(target, listOf(profile), base64Subscription = false)
}
