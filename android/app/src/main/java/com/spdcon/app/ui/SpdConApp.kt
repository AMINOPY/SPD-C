package com.spdcon.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.staticCompositionLocalOf
import com.spdcon.app.config.ConfigLibraryStore
import com.spdcon.app.config.ConfigLinkSerializer
import com.spdcon.app.config.ConfigProfile
import com.spdcon.app.config.ConfigProtocol
import com.spdcon.app.ui.theme.DeepNavy
import com.spdcon.app.ui.theme.NeonBlue
import com.spdcon.app.ui.theme.NeonCyan
import com.spdcon.app.ui.theme.SpdConTheme
import com.spdcon.app.ui.theme.SuccessGreen
import com.spdcon.app.ui.theme.SurfaceGlass
import com.spdcon.app.ui.theme.SurfaceGlassLight
import com.spdcon.app.ui.theme.TextSecondary
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.spdcon.app.config.XrayCoreTester
import com.spdcon.app.vpn.SpdConVpnService

private val screenItems = listOf(
    Triple("home", "Home", Icons.Outlined.Home to Icons.Filled.Home),
    Triple("library", "Library", Icons.Outlined.Folder to Icons.Filled.Folder),
    Triple("create", "Create", Icons.Outlined.AddCircle to Icons.Filled.AddCircle),
    Triple("tools", "Tools", Icons.Outlined.Build to Icons.Filled.Build),
    Triple("more", "More", Icons.Outlined.MoreHoriz to Icons.Filled.MoreHoriz)
)

@Composable
fun SpdConApp(onOpenAdvancedPanel: () -> Unit = {}) {
    val context = LocalContext.current
    val languagePrefs = remember(context) { context.getSharedPreferences("spdcon-language", Context.MODE_PRIVATE) }
    var appLanguage by remember {
        mutableStateOf(AppLanguage.fromCode(languagePrefs.getString("language", "en") ?: "en"))
    }
    val setAppLanguage: (AppLanguage) -> Unit = { next ->
        appLanguage = next
        languagePrefs.edit().putString("language", next.code).apply()
    }
    val clipboard = LocalClipboardManager.current
    val store = remember(context) { ConfigLibraryStore(context) }
    var profiles by remember { mutableStateOf(store.loadAll()) }
    var selected by remember { mutableStateOf("home") }
    var scannerServerIp by remember { mutableStateOf<String?>(null) }
    var scannerSni by remember { mutableStateOf<String?>(null) }
    var generatedLink by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var selectedProfileName by remember { mutableStateOf<String?>(null) }
    val generatedQr = remember(generatedLink) {
        generatedLink?.let { com.spdcon.app.createQrBitmap(it)?.asImageBitmap() }
    }
    val bothProtocolsSavedMessage = tr("Both VLESS and Trojan WebSocket profiles were saved. The QR dialog shows the first link; both are available in Library.")
    var pendingVpnProfile by remember { mutableStateOf<ConfigProfile?>(null) }
    var vpnState by remember { mutableStateOf("disconnected") }
    LaunchedEffect(context) {
        while (true) {
            vpnState = context.getSharedPreferences(SpdConVpnService.PREFS, Context.MODE_PRIVATE)
                .getString("status", "disconnected") ?: "disconnected"
            delay(1_000)
        }
    }

    fun beginVpn(profile: ConfigProfile) {
        if (profile.protocol == ConfigProtocol.TUIC) {
            dialogMessage = "TUIC is not supported by the bundled Xray-core. Choose VLESS, VMess, Trojan, Shadowsocks or Hysteria2."
            return
        }
        ConfigLinkSerializer.serialize(profile).fold(
            onSuccess = { link ->
                val serviceIntent = Intent(context, SpdConVpnService::class.java)
                    .setAction(SpdConVpnService.ACTION_START)
                    .putExtra(SpdConVpnService.EXTRA_LINK, link)
                    .putExtra(SpdConVpnService.EXTRA_NAME, profile.name)
                runCatching { context.startForegroundService(serviceIntent) }
                    .onSuccess { dialogMessage = "VPN startup requested. Android will show the active VPN notification; actual server reachability still needs verification." }
                    .onFailure { dialogMessage = "Could not start VPN service: ${it.message}" }
            },
            onFailure = { dialogMessage = "Invalid config: ${it.message}" }
        )
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val profile = pendingVpnProfile
        pendingVpnProfile = null
        if (result.resultCode == Activity.RESULT_OK && profile != null) {
            beginVpn(profile)
        } else {
            dialogMessage = "VPN permission was not granted."
        }
    }

    fun requestVpn(profile: ConfigProfile) {
        val permissionIntent = VpnService.prepare(context)
        if (permissionIntent == null) {
            beginVpn(profile)
        } else {
            pendingVpnProfile = profile
            vpnPermissionLauncher.launch(permissionIntent)
        }
    }

    fun disconnectVpn() {
        runCatching {
            context.startService(Intent(context, SpdConVpnService::class.java).setAction(SpdConVpnService.ACTION_STOP))
        }.onSuccess {
            dialogMessage = "Disconnect requested. Check the VPN notification for the final status."
        }.onFailure {
            dialogMessage = "Could not request disconnect: ${it.message}"
        }
    }

    fun reloadProfiles() { profiles = store.loadAll() }
    fun openProfile(profile: ConfigProfile) {
        ConfigLinkSerializer.serialize(profile).fold(
            onSuccess = { generatedLink = it },
            onFailure = { dialogMessage = it.message ?: "Could not serialize this configuration." }
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAppLanguage provides appLanguage,
        LocalSetAppLanguage provides setAppLanguage,
        androidx.compose.ui.platform.LocalLayoutDirection provides appLanguage.layoutDirection
    ) {
    SpdConTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF071226), DeepNavy, Color(0xFF020611)))
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("SPD-C", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text(tr("Secure • Private • Connected"), fontSize = 12.sp, color = TextSecondary)
                    }
                    Row(
                        modifier = Modifier.clip(CircleShape).background(Color(0xFF0C2A2B)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape)
                            .background(if (vpnState == "connected") SuccessGreen else TextSecondary))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            tr(when (vpnState) {
                                "connected" -> "VPN connected"
                                "connecting" -> "Connecting…"
                                "disconnecting" -> "Disconnecting…"
                                "running-unverified" -> "VPN needs check"
                                "error" -> "VPN error"
                                else -> "VPN off"
                            }),
                            color = if (vpnState == "connected") SuccessGreen else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selected) {
                        "home" -> HomePage(
                            configCount = profiles.size,
                            favoriteCount = profiles.count { it.favorite },
                            onCreate = { selected = "create" },
                            onLibrary = { selected = "library" },
                            onScan = onOpenAdvancedPanel,
                            onTools = { selected = "tools" }
                        )
                        "library" -> LibraryPage(
                            profiles = profiles,
                            onFavorite = { profile ->
                                store.setFavorite(profile, !profile.favorite)
                                reloadProfiles()
                            },
                            onOpen = ::openProfile,
                            onConnect = ::requestVpn,
                            onDisconnect = ::disconnectVpn,
                            onDelete = { profile ->
                                store.delete(profile)
                                reloadProfiles()
                            }
                        )
                        "create" -> CreatePage(
                            existingProfiles = profiles,
                            scannerServerIp = scannerServerIp,
                            scannerSni = scannerSni,
                            onGenerate = { generatedProfiles ->
                                val errors = generatedProfiles.flatMap { profile ->
                                    profile.validate().map { "${profile.name}: $it" }
                                }
                                if (errors.isNotEmpty()) {
                                    dialogMessage = errors.joinToString("\n")
                                } else {
                                    val links = generatedProfiles.map { profile ->
                                        ConfigLinkSerializer.serialize(profile).map { profile to it }
                                    }
                                    val failure = links.firstOrNull { it.isFailure }
                                    if (failure != null) {
                                        dialogMessage = failure.exceptionOrNull()?.message ?: "Could not generate the link."
                                    } else {
                                        val pairs = links.map { it.getOrThrow() }
                                        val saveFailure = pairs.firstOrNull { (profile, _) -> store.save(profile).isFailure }
                                        if (saveFailure != null) {
                                            dialogMessage = store.save(saveFailure.first).exceptionOrNull()?.message ?: "Could not save configuration."
                                        } else {
                                            reloadProfiles()
                                            generatedLink = pairs.first().second
                                            selected = "library"
                                            if (pairs.size > 1) {
                                                dialogMessage = bothProtocolsSavedMessage
                                            }
                                        }
                                    }
                                }
                            }
                        )
                        "tools" -> ToolsPage(onOpenAdvancedPanel, onOpenScanner = { selected = "scanner" })
                        "scanner" -> CleanIpScannerPage(
                            onBack = { selected = "tools" },
                            onUseIp = { ip, domain ->
                                scannerServerIp = ip
                                scannerSni = domain
                                selected = "create"
                            }
                        )
                        else -> MorePage(onOpenAdvancedPanel)
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(horizontal = 18.dp, vertical = 18.dp)
                    .fillMaxWidth()
                    .height(72.dp)
                    .shadow(20.dp, RoundedCornerShape(27.dp), ambientColor = NeonBlue.copy(alpha = 0.25f))
                    .clip(RoundedCornerShape(27.dp))
                    .background(Brush.verticalGradient(listOf(SurfaceGlassLight, SurfaceGlass)))
                    .border(1.dp, NeonCyan.copy(alpha = 0.24f), RoundedCornerShape(27.dp))
                    .padding(horizontal = 5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                screenItems.forEach { (route, label, icons) ->
                    val localizedLabel = tr(label)
                    val active = selected == route
                    Column(
                        modifier = Modifier.clip(RoundedCornerShape(17.dp))
                            .background(if (active) NeonBlue.copy(alpha = 0.20f) else Color.Transparent)
                            .clickable { selected = route }
                            .padding(horizontal = 13.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(if (active) icons.second else icons.first, contentDescription = localizedLabel,
                            tint = if (active) NeonCyan else TextSecondary, modifier = Modifier.size(22.dp))
                        Text(localizedLabel, color = if (active) NeonCyan else TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }

    }
    if (generatedLink != null) {
        AlertDialog(
            onDismissRequest = { generatedLink = null },
            title = { Text(tr("Configuration ready")) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(170.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(tr("Saved locally. Scan the QR code or copy the link into your VPN client:"), color = TextSecondary)
                    generatedQr?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Configuration QR code",
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Text(generatedLink.orEmpty(), fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    clipboard.setText(AnnotatedString(generatedLink.orEmpty()))
                    generatedLink = null
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text(tr("Copy link"))
                }
            },
            dismissButton = { TextButton(onClick = { generatedLink = null }) { Text(tr("Close")) } }
        )
    }
    if (dialogMessage != null) {
        AlertDialog(
            onDismissRequest = { dialogMessage = null },
            title = { Text("Spd con") },
            text = { Text(dialogMessage.orEmpty()) },
            confirmButton = { TextButton(onClick = { dialogMessage = null }) { Text("OK") } }
        )
    }
    if (selectedProfileName != null) {
        AlertDialog(
            onDismissRequest = { selectedProfileName = null },
            title = { Text(selectedProfileName.orEmpty()) },
            text = { Text(tr("Configuration details")) },
            confirmButton = { TextButton(onClick = { selectedProfileName = null }) { Text(tr("Close")) } }
        )
    }
}

@Composable
private fun HomePage(
    configCount: Int,
    favoriteCount: Int,
    onCreate: () -> Unit,
    onLibrary: () -> Unit,
    onScan: () -> Unit,
    onTools: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(78.dp).clip(CircleShape).background(Color(0xFF0C2743)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(43.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(tr("VPN not connected"), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(tr("Your configurations stay on this device until you export or sync them."),
                    color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    MiniStat(tr("Local"), tr("Storage"), Icons.Filled.Folder)
                    MiniStat(tr("Ready"), tr("Builder"), Icons.Filled.AddCircle)
                    MiniStat(tr("Private"), tr("By default"), Icons.Filled.Security)
                }
            }
        }

        Text(tr("Quick Actions"), color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ActionCard(tr("New Config"), Icons.Filled.Add, Modifier.weight(1f), onCreate)
            ActionCard(tr("Library"), Icons.Filled.Folder, Modifier.weight(1f), onLibrary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ActionCard(tr("QR / Import"), Icons.Filled.QrCodeScanner, Modifier.weight(1f), onScan)
            ActionCard(tr("Diagnostics"), Icons.Filled.Analytics, Modifier.weight(1f), onTools)
        }

        Text(tr("Overview"), color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("$configCount", tr("Configs"), Modifier.weight(1f))
            StatCard("$favoriteCount", tr("Favorites"), Modifier.weight(1f))
            StatCard(tr("Local"), tr("Sync"), Modifier.weight(1f))
        }
    }
}

@Composable
private fun CreatePage(
    existingProfiles: List<ConfigProfile>,
    scannerServerIp: String?,
    scannerSni: String?,
    onGenerate: (List<ConfigProfile>) -> Unit
) {
    val template = existingProfiles.firstOrNull()
    var step by remember { mutableStateOf(1) }
    var selectedProtocol by remember(template) {
        mutableStateOf(when {
            template != null && template.protocol == ConfigProtocol.TROJAN && template.network.equals("ws", true) && template.security.equals("tls", true) -> "Trojan + WS + TLS"
            template != null && template.protocol == ConfigProtocol.VLESS && template.network.equals("ws", true) && template.security.equals("tls", true) -> "VLESS + WS + TLS"
            template != null && template.protocol == ConfigProtocol.VLESS && template.security.equals("reality", true) -> "VLESS + Reality"
            template?.protocol == ConfigProtocol.TROJAN -> "Trojan"
            template?.protocol == ConfigProtocol.SHADOWSOCKS -> "Shadowsocks"
            else -> "VLESS + WS + TLS"
        })
    }
    var name by remember { mutableStateOf("") }
    var server by remember(scannerServerIp, template) { mutableStateOf(scannerServerIp ?: template?.server.orEmpty()) }
    var port by remember(template) { mutableStateOf(template?.port?.toString() ?: "443") }
    var credential by remember { mutableStateOf("") }
    var sni by remember(scannerSni, template) { mutableStateOf(scannerSni ?: template?.parameters?.get("sni").orEmpty()) }
    var host by remember(template) { mutableStateOf(template?.parameters?.get("host").orEmpty()) }
    var wsPath by remember(template) { mutableStateOf(template?.parameters?.get("path") ?: "/ws") }
    var generateBothWebSocket by remember { mutableStateOf(true) }
    var publicKey by remember(template) { mutableStateOf(template?.parameters?.get("pbk").orEmpty()) }
    var shortId by remember(template) { mutableStateOf(template?.parameters?.get("sid").orEmpty()) }
    var editServerSettings by remember(template) { mutableStateOf(template == null) }

    fun quickGenerateFromTemplate() {
        val sourceProfile = existingProfiles.firstOrNull { it.server == server && it.port == (port.toIntOrNull() ?: -1) }
            ?: template ?: return
        val protocol = when {
            selectedProtocol.startsWith("Trojan") -> ConfigProtocol.TROJAN
            else -> ConfigProtocol.VLESS
        }
        val websocket = selectedProtocol.contains("+ WS")
        val reality = selectedProtocol == "VLESS + Reality"
        val generatedCredential = if (protocol == ConfigProtocol.VLESS) UUID.randomUUID().toString()
            else UUID.randomUUID().toString().replace("-", "")
        val parameters = sourceProfile.parameters.toMutableMap()
        if (sni.isNotBlank()) parameters["sni"] = sni.trim()
        if (reality) {
            parameters["pbk"] = publicKey.trim().ifBlank { sourceProfile.parameters["pbk"].orEmpty() }
            parameters["sid"] = shortId.trim().ifBlank { sourceProfile.parameters["sid"].orEmpty() }
        } else {
            parameters.remove("pbk")
            parameters.remove("sid")
            parameters.remove("flow")
        }
        if (websocket) {
            parameters["path"] = wsPath.trim().ifBlank { "/ws" }
            if (host.isNotBlank()) parameters["host"] = host.trim()
        } else {
            parameters.remove("path")
            parameters.remove("host")
        }
        val profileName = name.trim().ifBlank { "Spd con config" }
        val baseProfile = sourceProfile.copy(
            name = profileName,
            server = server.trim(),
            port = port.toIntOrNull() ?: sourceProfile.port,
            protocol = protocol,
            credential = generatedCredential,
            network = if (websocket) "ws" else "tcp",
            security = if (reality) "reality" else "tls",
            parameters = parameters,
            remark = profileName
        )
        val generated = mutableListOf(baseProfile)
        if (websocket && generateBothWebSocket) {
            val otherProtocol = if (protocol == ConfigProtocol.VLESS) ConfigProtocol.TROJAN else ConfigProtocol.VLESS
            val otherCredential = if (otherProtocol == ConfigProtocol.VLESS) UUID.randomUUID().toString()
                else UUID.randomUUID().toString().replace("-", "")
            generated += baseProfile.copy(
                name = profileName + " (" + otherProtocol.name + ")",
                protocol = otherProtocol,
                credential = otherCredential,
                remark = profileName + " (" + otherProtocol.name + ")"
            )
        }
        onGenerate(generated)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text(tr("Create Config"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(tr("Build a valid share link and save it to your local library."), color = TextSecondary, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..3).forEach { n ->
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape)
                    .background(if (n <= step) NeonCyan else Color(0xFF203149)))
            }
        }
        Text(tr("Step {step} of 3").replace("{step}", step.toString()), color = TextSecondary, fontSize = 12.sp)

        when (step) {
            1 -> {
                Text(tr("Choose protocol"), style = MaterialTheme.typography.titleMedium)
                listOf("VLESS + WS + TLS", "Trojan + WS + TLS", "VLESS + TLS", "VLESS + Reality", "Trojan", "Shadowsocks", "Hysteria2", "TUIC").forEach { option ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selectedProtocol = option },
                        cornerRadius = 16.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(option, fontWeight = FontWeight.SemiBold)
                                Text(tr(protocolHint(option)), color = TextSecondary, fontSize = 12.sp)
                            }
                            if (selectedProtocol == option) Text(tr("SELECTED"), color = NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                NeonButton(tr("Continue"), { step = 2 }, Modifier.fillMaxWidth())
            }
            2 -> {
                GlassField(name, { name = it }, tr("Configuration name"))
                if (server.isNotBlank() && !editServerSettings) {
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                        Text(tr("Using saved server template"), fontWeight = FontWeight.SemiBold)
                        Text("${server}:${port}", color = TextSecondary, fontSize = 12.sp)
                        TextButton(onClick = { editServerSettings = true }) { Text(tr("Change server settings")) }
                    }
                    Text(tr("Only the name and protocol are needed when reusing this saved server template."),
                        color = TextSecondary, fontSize = 12.sp)
                }
                if (editServerSettings || server.isBlank() || (selectedProtocol == "VLESS + Reality" && sni.isBlank())) {
                    GlassField(server, { server = it }, tr("Server hostname / IP"))
                    GlassField(port, { port = it.filter(Char::isDigit).take(5) }, tr("Port"), numeric = true)
                    GlassField(credential, { credential = it }, tr("Credential (leave blank to generate)"))
                    if (selectedProtocol != "Shadowsocks") {
                        GlassField(sni, { sni = it }, if (selectedProtocol == "VLESS + Reality") tr("REALITY SNI / serverName") else tr("TLS SNI (optional)"))
                    }
                }
                if (selectedProtocol.contains("+ WS") && (editServerSettings || template == null)) {
                    GlassField(host, { host = it }, tr("WebSocket Host header (optional)"))
                    GlassField(wsPath, { wsPath = it }, tr("WebSocket path"))
                    androidx.compose.material3.Checkbox(
                        checked = generateBothWebSocket,
                        onCheckedChange = { generateBothWebSocket = it }
                    )
                    Text(tr("Generate VLESS and Trojan together"), color = TextSecondary, fontSize = 12.sp)
                    Text(tr("Use the same deployed WebSocket route and TLS domain for both protocols."),
                        color = TextSecondary, fontSize = 12.sp)
                }
                if (selectedProtocol == "VLESS + Reality") {
                    if (editServerSettings || publicKey.isBlank()) {
                        GlassField(publicKey, { publicKey = it }, tr("REALITY public key (pbk)"))
                        GlassField(shortId, { shortId = it }, tr("REALITY short ID (0–16 hex chars; may be empty)"))
                    } else {
                        Text(tr("REALITY keys reused from the saved server template."), color = TextSecondary, fontSize = 12.sp)
                    }
                    Text(tr("Use the public key and short ID configured on your server. They cannot be guessed by the app."),
                        color = TextSecondary, fontSize = 12.sp)
                }
                if (template != null && !editServerSettings &&
                    selectedProtocol in listOf("VLESS + WS + TLS", "Trojan + WS + TLS", "VLESS + TLS", "VLESS + Reality", "Trojan")) {
                    NeonButton(tr("Quick Generate & Save"), { quickGenerateFromTemplate() }, Modifier.fillMaxWidth())
                    Text(tr("Use the saved server settings, generate a new credential, and save immediately."),
                        color = TextSecondary, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { step = 1 }, modifier = Modifier.weight(1f)) { Text(tr("Back")) }
                    NeonButton(tr("Review"), { step = 3 }, Modifier.weight(1f))
                }
            }
            else -> {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    ReviewLine(tr("Protocol"), tr(selectedProtocol))
                    ReviewLine(tr("Name"), name.ifBlank { tr("Spd con config") })
                    ReviewLine(tr("Server"), "${server.ifBlank { "—" }}:${port.ifBlank { "443" }}")
                    ReviewLine(tr("Credential"), tr(if (credential.isBlank()) "Generated automatically" else "Provided manually"))
                    if (selectedProtocol == "VLESS + Reality") {
                        ReviewLine(tr("SNI"), sni)
                        ReviewLine(tr("Public key"), tr(if (publicKey.isBlank()) "Required" else "Provided"))
                        ReviewLine(tr("Short ID"), if (shortId.isBlank()) tr("(empty)") else shortId)
                    }
                    if (selectedProtocol.contains("+ WS")) {
                        ReviewLine(tr("WebSocket path"), wsPath)
                        ReviewLine(tr("WebSocket Host header"), host.ifBlank { tr("Optional") })
                        ReviewLine(tr("Dual protocol"), tr(if (generateBothWebSocket) "VLESS and Trojan" else "Single protocol"))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { step = 2 }, modifier = Modifier.weight(1f)) { Text(tr("Back")) }
                    NeonButton(tr("Generate & Save"), {
                        val protocol = when {
                            selectedProtocol.startsWith("Trojan") -> ConfigProtocol.TROJAN
                            selectedProtocol == "Shadowsocks" -> ConfigProtocol.SHADOWSOCKS
                            selectedProtocol == "Hysteria2" -> ConfigProtocol.HYSTERIA2
                            selectedProtocol == "TUIC" -> ConfigProtocol.TUIC
                            else -> ConfigProtocol.VLESS
                        }
                        val generatedCredential = credential.trim().ifBlank {
                            if (protocol == ConfigProtocol.VLESS || protocol == ConfigProtocol.VMESS || protocol == ConfigProtocol.TUIC) {
                                UUID.randomUUID().toString()
                            } else UUID.randomUUID().toString().replace("-", "")
                        }
                        val reality = selectedProtocol == "VLESS + Reality"
                        val websocket = selectedProtocol.contains("+ WS")
                        val security = when {
                            selectedProtocol == "Shadowsocks" -> "none"
                            selectedProtocol == "Hysteria2" || selectedProtocol == "TUIC" -> "tls"
                            reality -> "reality"
                            else -> "tls"
                        }
                        val parameters = buildMap {
                            if (selectedProtocol != "Shadowsocks" && sni.isNotBlank()) put("sni", sni.trim())
                            if (reality) {
                                put("sni", sni.trim())
                                put("pbk", publicKey.trim())
                                put("sid", shortId.trim())
                            }
                            if (websocket) {
                                put("path", wsPath.trim())
                                if (host.isNotBlank()) put("host", host.trim())
                            }
                            if (protocol == ConfigProtocol.SHADOWSOCKS) put("method", "chacha20-ietf-poly1305")
                            if (protocol == ConfigProtocol.TUIC) put("password", generatedCredential)
                        }
                        val profileName = name.trim().ifBlank { "Spd con config" }
                        val baseProfile = ConfigProfile(
                            name = profileName,
                            protocol = protocol,
                            server = server.trim(),
                            port = port.toIntOrNull() ?: 0,
                            credential = generatedCredential,
                            network = when {
                                protocol == ConfigProtocol.HYSTERIA2 || protocol == ConfigProtocol.TUIC -> "udp"
                                websocket -> "ws"
                                else -> "tcp"
                            },
                            security = security,
                            parameters = parameters,
                            remark = profileName
                        )
                        val profiles = mutableListOf(baseProfile)
                        if (websocket && generateBothWebSocket) {
                            val otherProtocol = if (protocol == ConfigProtocol.VLESS) ConfigProtocol.TROJAN else ConfigProtocol.VLESS
                            val otherCredential = if (otherProtocol == ConfigProtocol.VLESS) UUID.randomUUID().toString()
                                else UUID.randomUUID().toString().replace("-", "")
                            profiles += baseProfile.copy(
                                name = "$profileName (${otherProtocol.name})",
                                protocol = otherProtocol,
                                credential = otherCredential,
                                remark = "$profileName (${otherProtocol.name})"
                            )
                        }
                        onGenerate(profiles)
                    }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LibraryPage(
    profiles: List<ConfigProfile>,
    onFavorite: (ConfigProfile) -> Unit,
    onOpen: (ConfigProfile) -> Unit,
    onConnect: (ConfigProfile) -> Unit,
    onDisconnect: () -> Unit,
    onDelete: (ConfigProfile) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var testingProfileName by remember { mutableStateOf<String?>(null) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var vpnStatus by remember { mutableStateOf("disconnected • VPN is off") }
    LaunchedEffect(Unit) {
        while (true) {
            val prefs = context.getSharedPreferences(SpdConVpnService.PREFS, Context.MODE_PRIVATE)
            val state = prefs.getString("status", "disconnected") ?: "disconnected"
            val detail = prefs.getString("detail", "") ?: ""
            vpnStatus = if (detail.isBlank()) state else "$state • $detail"
            delay(1_000)
        }
    }
    val filtered = profiles.filter { profile ->
        search.isBlank() || profile.name.contains(search, true) ||
            profile.server.contains(search, true) || profile.protocol.name.contains(search, true) ||
            profile.tags.any { it.contains(search, true) }
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 112.dp)) {
        Text(tr("Library"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(tr("Saved configurations on this device"), color = TextSecondary, fontSize = 13.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.weight(1f)) { Text(tr("Disconnect VPN")) }
        }
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            Text(tr("VPN status"), fontWeight = FontWeight.SemiBold)
            Text(vpnStatus, color = if (vpnStatus.startsWith("connected")) NeonCyan else TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        GlassField(search, { search = it }, tr("Search configs, servers, tags"))
        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(tr(if (profiles.isEmpty()) "No configurations yet" else "No matches found"),
                    fontWeight = FontWeight.SemiBold)
                Text(tr("Create a config to see it here. Nothing is prefilled with fake servers."),
                    color = TextSecondary, fontSize = 12.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                items(filtered, key = { profileId(it) }) { profile ->
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 19.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f).clickable { onOpen(profile) }) {
                                Text(profile.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Spacer(Modifier.height(3.dp))
                                Text("${profile.server}:${profile.port}", color = TextSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(5.dp))
                                Text("${profile.protocol.name} • ${profile.network.uppercase()} • ${profile.security.uppercase()}",
                                    color = NeonCyan, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                androidx.compose.material3.IconButton(onClick = { onFavorite(profile) }) {
                                    Icon(if (profile.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = tr("Toggle favorite"),
                                        tint = if (profile.favorite) NeonCyan else TextSecondary)
                                }
                                TextButton(onClick = { onDelete(profile) }) { Text(tr("Delete"), color = Color(0xFFFF6C82), fontSize = 11.sp) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onConnect(profile) },
                            enabled = profile.protocol != ConfigProtocol.TUIC && testingProfileName == null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (profile.protocol == ConfigProtocol.TUIC) tr("TUIC needs a compatible core") else tr("Connect through Android VPN"))
                        }
                        OutlinedButton(
                            onClick = {
                                if (testingProfileName == null) {
                                    testingProfileName = profile.name
                                    testMessage = null
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            runCatching { XrayCoreTester.testProfile(profile) }
                                        }
                                        testingProfileName = null
                                        testMessage = result.fold(
                                            onSuccess = { test ->
                                                if (test.connected) {
                                                    "PASS • ${test.coreVersion} • ${test.latencyMs ?: 0} ms\n${test.message}"
                                                } else {
                                                    "CONFIG VALID • NETWORK FAILED • ${test.coreVersion}\n${test.message}"
                                                }
                                            },
                                            onFailure = { failure ->
                                                "Xray test failed: ${failure.message ?: failure.javaClass.simpleName}"
                                            }
                                        )
                                    }
                                }
                            },
                            enabled = testingProfileName == null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (testingProfileName == profile.name) tr("Testing with Xray-core…") else tr("Test with real Xray-core"))
                        }
                    }
                }
            }
        }
    }
    if (testMessage != null) {
        AlertDialog(
            onDismissRequest = { testMessage = null },
            title = { Text(tr("Xray-core test result")) },
            text = { Text(testMessage.orEmpty()) },
            confirmButton = { TextButton(onClick = { testMessage = null }) { Text(tr("Close")) } }
        )
    }
}

@Composable
private fun ToolsPage(onOpenAdvancedPanel: () -> Unit, onOpenScanner: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text(tr("Tools"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(tr("Advanced utilities and server-side helpers"), color = TextSecondary, fontSize = 13.sp)
        ToolCard(tr("Clean IP Scanner"), tr("Find responsive TLS edge IPs for your domain"), Icons.Filled.Analytics, onOpenScanner)
        ToolCard(tr("Backhaul Generator"), tr("Build tunnel server / client configuration"), Icons.Filled.Build, onOpenAdvancedPanel)
        ToolCard(tr("REALITY & node settings"), tr("Open the full configuration panel"), Icons.Filled.Security, onOpenAdvancedPanel)
        ToolCard(tr("Cloudflare / Telegram proxy"), tr("Open existing proxy tools"), Icons.Filled.Settings, onOpenAdvancedPanel)
        ToolCard(tr("QR import & diagnostics"), tr("Use the full import and diagnostic panel"), Icons.Filled.QrCodeScanner, onOpenAdvancedPanel)
    }
}

@Composable
private fun MorePage(onOpenAdvancedPanel: () -> Unit) {
    val language = LocalAppLanguage.current
    val setLanguage = LocalSetAppLanguage.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text(tr("More"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(tr("Settings and advanced features"), color = TextSecondary, fontSize = 13.sp)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(tr("Local-first storage"), fontWeight = FontWeight.SemiBold)
            Text(tr("Credentials are stored by the existing encrypted local library. Cloud sync remains optional."),
                color = TextSecondary, fontSize = 12.sp)
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(tr("Language"), fontWeight = FontWeight.SemiBold)
            Text(tr("Choose the app language. Persian and Arabic use right-to-left layout."),
                color = TextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AppLanguage.entries.forEach { option ->
                    FilterChip(
                        selected = language == option,
                        onClick = { setLanguage(option) },
                        label = { Text(option.nativeLabel) }
                    )
                }
            }
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(tr("Appearance"), fontWeight = FontWeight.SemiBold)
            Text(tr("SPD-C Neon Glass • dark navy • cyan glow"), color = TextSecondary, fontSize = 12.sp)
        }
        GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenAdvancedPanel) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr("Advanced / legacy panel"), fontWeight = FontWeight.SemiBold)
                    Text(tr("Nodes, Cloud sync, Backhaul, Telegram proxy and extended settings"),
                        color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
        Text("SPD-C 0.2.0", color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun MiniStat(value: String, label: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun ActionCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassCard(modifier = modifier.height(96.dp), cornerRadius = 20.dp, onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, cornerRadius = 18.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NeonCyan)
            Text(label, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ToolCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun GlassField(value: String, onValueChange: (String) -> Unit, label: String, numeric: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (numeric) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonCyan, unfocusedBorderColor = Color(0x5900B4FF),
            focusedContainerColor = SurfaceGlass, unfocusedContainerColor = SurfaceGlass,
            cursorColor = NeonCyan
        )
    )
}

@Composable
private fun ReviewLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        Text(value.ifBlank { "—" }, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier.shadow(17.dp, shape, ambientColor = NeonBlue.copy(alpha = 0.16f), spotColor = NeonBlue.copy(alpha = 0.28f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(SurfaceGlassLight, SurfaceGlass)))
            .border(1.dp, Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.35f), NeonBlue.copy(alpha = 0.12f), Color.Transparent)), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(17.dp),
        content = content
    )
}

@Composable
private fun NeonButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.height(52.dp)
            .shadow(if (enabled) 12.dp else 0.dp, RoundedCornerShape(16.dp), ambientColor = NeonBlue.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = Color.Black)
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

@Composable
private fun protocolHint(protocol: String): String = tr(when (protocol) {
    "VLESS + WS + TLS" -> "VLESS over WebSocket with TLS; requires a matching server/Worker route"
    "Trojan + WS + TLS" -> "Trojan over WebSocket with TLS; requires a matching server/Worker route"
    "VLESS + TLS" -> "TLS transport security"
    "VLESS + Reality" -> "Requires the server public key and SNI"
    "Trojan" -> "Password-based TLS proxy"
    "Shadowsocks" -> "Encrypted proxy with a cipher method"
    "Hysteria2" -> "QUIC-based UDP transport"
    "TUIC" -> "QUIC transport with a separate password"
    else -> "VPN configuration"
})

private fun profileId(profile: ConfigProfile): String =
    "${profile.protocol.name}|${profile.server}|${profile.port}|${profile.name}"
