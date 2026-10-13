package com.spdcon.app.config

data class RuntimeCompatibility(
    val supported: Boolean,
    val runtimeName: String,
    val message: String,
    val fallbackTarget: ConfigExportTarget? = null
)

/**
 * Explicit capability gate for the bundled runtime. Export compatibility is not
 * treated as runtime compatibility: this APK bundles Xray/libXray, not sing-box.
 */
object ConfigRuntimeCompatibility {
    private val xrayProtocols = setOf(
        ConfigProtocol.VLESS,
        ConfigProtocol.VMESS,
        ConfigProtocol.TROJAN,
        ConfigProtocol.SHADOWSOCKS
    )

    fun inspectForBundledRuntime(profile: ConfigProfile): RuntimeCompatibility {
        val validationErrors = profile.validate()
        if (validationErrors.isNotEmpty()) {
            return RuntimeCompatibility(
                supported = false,
                runtimeName = "Xray-core",
                message = "Profile validation failed: ${validationErrors.joinToString("; ")}"
            )
        }
        if (profile.protocol !in xrayProtocols) {
            return RuntimeCompatibility(
                supported = false,
                runtimeName = "Xray-core",
                message = "${profile.protocol} can be exported for a compatible client, but this app's bundled runtime is Xray-core and cannot run it. Export to Hiddify or a compatible sing-box client instead.",
                fallbackTarget = ConfigExportTarget.HIDDIFY
            )
        }
        if (profile.security.equals("reality", ignoreCase = true) && profile.protocol != ConfigProtocol.VLESS) {
            return RuntimeCompatibility(
                supported = false,
                runtimeName = "Xray-core",
                message = "REALITY requires VLESS for this runtime."
            )
        }
        return RuntimeCompatibility(
            supported = true,
            runtimeName = "Xray-core",
            message = "Profile is compatible with the bundled Xray runtime. Server reachability still needs a live probe."
        )
    }

    fun requireBundledRuntimeSupport(profile: ConfigProfile) {
        val result = inspectForBundledRuntime(profile)
        require(result.supported) { result.message }
    }
}
