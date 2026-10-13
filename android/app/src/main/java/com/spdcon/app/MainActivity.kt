package com.spdcon.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.spdcon.app.config.CloudSyncClient
import com.spdcon.app.config.ConfigLibraryStore
import com.spdcon.app.config.CredentialCipher
import com.spdcon.app.config.ConfigLinkParser
import com.spdcon.app.config.ConfigLinkSerializer
import com.spdcon.app.config.ConfigProfile
import com.spdcon.app.config.ConfigGenerationService
import com.spdcon.app.config.TelegramWebProxyLinkBuilder
import com.spdcon.app.config.ConfigInboundKind
import com.spdcon.app.config.ConfigInboundSpec
import com.spdcon.app.config.WorkerRoute
import com.spdcon.app.config.ConfigProtocol
import com.spdcon.app.config.FragmentSettings
import com.spdcon.app.config.NetworkPreset
import com.spdcon.app.config.RealitySettings
import com.spdcon.app.config.XrayConfigGenerator
import com.spdcon.app.config.toRealitySettings
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var showAdvancedPanel by remember { mutableStateOf(false) }
            androidx.activity.compose.BackHandler(enabled = showAdvancedPanel) {
                showAdvancedPanel = false
            }
            if (showAdvancedPanel) {
                com.spdcon.app.ui.theme.SpdConTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        SpdConDashboard()
                    }
                }
            } else {
                com.spdcon.app.ui.SpdConApp(
                    onOpenAdvancedPanel = { showAdvancedPanel = true }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpdConDashboard() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val libraryStore = remember(context) { ConfigLibraryStore(context) }
    var savedProfiles by remember { mutableStateOf(libraryStore.loadAll()) }
    var librarySearch by remember { mutableStateOf("") }
    var tagDrafts by remember { mutableStateOf(emptyMap<String, String>()) }
    val cloudPrefs = remember(context) { context.getSharedPreferences("spdcon-cloud", 0) }
    val coroutineScope = rememberCoroutineScope()
    var cloudBaseUrl by remember { mutableStateOf(cloudPrefs.getString("base_url", "") ?: "") }
    var cloudUsername by remember { mutableStateOf(cloudPrefs.getString("username", "") ?: "") }
    var cloudPassword by remember { mutableStateOf("") }
    var cloudToken by remember {
        mutableStateOf(runCatching {
            cloudPrefs.getString("token_enc", null)?.let { CredentialCipher.decrypt(it) } ?: ""
        }.getOrDefault(""))
    }
    var cloudBusy by remember { mutableStateOf(false) }
    var showCloudSettings by remember { mutableStateOf(false) }
    var selectedSection by remember { mutableStateOf("create") }
    var cloudMessage by remember { mutableStateOf<String?>(null) }
    var cloudSubscription by remember { mutableStateOf<String?>(null) }
    var libraryMessage by remember { mutableStateOf<String?>(null) }
    var manualProtocol by remember { mutableStateOf(ConfigProtocol.VLESS) }
    var manualProtocolMenuExpanded by remember { mutableStateOf(false) }
    var manualName by remember { mutableStateOf("کانفیگ جدید") }
    var multiGenerationKind by remember { mutableStateOf("TLS Relay") }
    var multiGenerationMenuExpanded by remember { mutableStateOf(false) }
    var workerPathDraft by remember { mutableStateOf("/ws/{uuid}") }
    var proxyHostname by remember { mutableStateOf("") }
    var proxySecret by remember { mutableStateOf("") }
    var proxyMessage by remember { mutableStateOf<String?>(null) }
    var multiGenerationMessage by remember { mutableStateOf<String?>(null) }
    var multiGenerationOutput by remember { mutableStateOf<String?>(null) }
    val generationPrefs = remember(context) { context.getSharedPreferences("spdcon-generation", 0) }
    val stableGenerationUserId = remember(context) {
        generationPrefs.getString("stable_user_id", null) ?: java.util.UUID.randomUUID().toString().also {
            generationPrefs.edit().putString("stable_user_id", it).apply()
        }
    }
    var selectedCountry by remember { mutableStateOf("auto") }
    var countryMenuExpanded by remember { mutableStateOf(false) }
    var blockAdultSites by remember { mutableStateOf(false) }
    var manualServer by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("443") }
    var manualCredential by remember { mutableStateOf("") }
    var manualTuicPassword by remember { mutableStateOf("") }
    var manualObfs by remember { mutableStateOf("") }
    var manualObfsPassword by remember { mutableStateOf("") }
    var manualCongestionControl by remember { mutableStateOf("bbr") }
    var manualUdpRelayMode by remember { mutableStateOf("native") }
    var manualNetwork by remember { mutableStateOf("ws") }
    var manualSecurity by remember { mutableStateOf("tls") }
    var manualMethod by remember { mutableStateOf("chacha20-ietf-poly1305") }
    var manualHost by remember { mutableStateOf("") }
    var manualPath by remember { mutableStateOf("/") }
    var manualWsPath by remember { mutableStateOf("/ws/{uuid}") }
    var manualServiceName by remember { mutableStateOf("") }
    var manualSni by remember { mutableStateOf("") }
    var manualAlpn by remember { mutableStateOf("h2,http/1.1") }
    var manualHeaderType by remember { mutableStateOf("none") }
    var manualEncryption by remember { mutableStateOf("none") }
    var manualMode by remember { mutableStateOf("") }
    var manualAllowInsecure by remember { mutableStateOf(false) }
    var manualCdnEnabled by remember { mutableStateOf(false) }
    var showAdvancedBuilder by remember { mutableStateOf(false) }
    var showLinkImport by remember { mutableStateOf(false) }
    var remoteNodes by remember { mutableStateOf(emptyList<CloudSyncClient.RemoteNode>()) }
    var nodeMenuExpanded by remember { mutableStateOf(false) }
    var nodeMessage by remember { mutableStateOf<String?>(null) }
    var nodeNameDraft by remember { mutableStateOf("") }
    var nodeAddressDraft by remember { mutableStateOf("") }
    var nodePortDraft by remember { mutableStateOf("443") }
    var nodeCountryDraft by remember { mutableStateOf("DE") }
    var nodeProtocolDraft by remember { mutableStateOf("vless") }
    var nodeNetworkDraft by remember { mutableStateOf("tcp") }
    var nodeSecurityDraft by remember { mutableStateOf("reality") }
    var nodeSniDraft by remember { mutableStateOf("") }
    var nodePublicKeyDraft by remember { mutableStateOf("") }
    var nodeShortIdDraft by remember { mutableStateOf("") }
    var nodeFingerprintDraft by remember { mutableStateOf("chrome") }
    var shareLink by remember { mutableStateOf("") }
    var parseResult by remember { mutableStateOf<String?>(null) }
    var parsedSummary by remember { mutableStateOf<String?>(null) }
    var parsedProfile by remember { mutableStateOf<ConfigProfile?>(null) }
    var generatedOutput by remember { mutableStateOf<String?>(null) }
    var qrContent by remember { mutableStateOf<String?>(null) }
    var copyContent by remember { mutableStateOf<String?>(null) }
    val qrImage = remember(qrContent) { qrContent?.let { createQrBitmap(it)?.asImageBitmap() } }
    var presetMenuExpanded by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf(NetworkPreset.MANUAL) }
    var fragmentEnabled by remember { mutableStateOf(false) }
    var fragmentPackets by remember { mutableStateOf("tlshello") }
    var fragmentLength by remember { mutableStateOf("100-200") }
    var fragmentInterval by remember { mutableStateOf("10-20") }
    var realityServerName by remember { mutableStateOf("") }
    var realityPublicKey by remember { mutableStateOf("") }
    var realityShortId by remember { mutableStateOf("") }
    var realityFingerprint by remember { mutableStateOf("chrome") }
    var realitySpiderX by remember { mutableStateOf("/") }
    var realityFlow by remember { mutableStateOf("") }
    var showAdvancedReality by remember { mutableStateOf(false) }
    var showAdvancedNetwork by remember { mutableStateOf(false) }
    val visibleProfiles = savedProfiles.filter { profile ->
        val query = librarySearch.trim()
        when {
            query.equals("favorite", ignoreCase = true) || query == "⭐" -> profile.favorite
            query.isEmpty() -> true
            else -> profile.name.contains(query, ignoreCase = true) ||
                profile.server.contains(query, ignoreCase = true) ||
                profile.protocol.name.contains(query, ignoreCase = true) ||
                profile.tags.any { it.contains(query, ignoreCase = true) }
        }
    }

    fun refreshRemoteNodes(adminOnly: Boolean = true) {
        if (cloudBaseUrl.isBlank() || cloudToken.isBlank()) {
            nodeMessage = "ابتدا در بخش ابر وارد حساب Cloudflare شو."
            return
        }
        cloudBusy = true
        coroutineScope.launch {
            var result = runCatching {
                withContext(Dispatchers.IO) { CloudSyncClient.listNodes(cloudBaseUrl, cloudToken, adminOnly) }
            }
            if (result.isFailure && adminOnly) {
                result = runCatching {
                    withContext(Dispatchers.IO) { CloudSyncClient.listNodes(cloudBaseUrl, cloudToken, false) }
                }
            }
            result.onSuccess { remoteNodes = it; nodeMessage = "فهرست نودها به‌روز شد: ${it.size} نود." }
                .onFailure { nodeMessage = "دریافت نودها ناموفق بود: ${it.message}" }
            cloudBusy = false
        }
    }

    fun applyRemoteNode(node: CloudSyncClient.RemoteNode) {
        manualName = node.name
        manualServer = node.address
        manualPort = node.port.toString()
        manualProtocol = runCatching { ConfigProtocol.valueOf(node.protocol.uppercase()) }.getOrDefault(ConfigProtocol.VLESS)
        manualNetwork = if (node.network.equals("raw", true)) "tcp" else node.network
        manualSecurity = node.security
        manualSni = node.sni
        realityServerName = node.sni
        realityPublicKey = node.publicKey
        realityShortId = node.shortId
        realityFingerprint = node.fingerprint
        if (node.country != "auto") selectedCountry = node.country
        selectedSection = "create"
        nodeMenuExpanded = false
        nodeMessage = "تنظیمات از نود انتخابی پر شد؛ UUID/کاربر باید روی سرور هم ثبت شود."
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Spd con", fontWeight = FontWeight.Bold)
                        Text("ساخت و مدیریت کانفیگ", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Spd con", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(when (selectedSection) {
                                "library" -> "کانفیگ‌های ذخیره‌شده‌ات را پیدا و مدیریت کن."
                                "cloud" -> "همگام‌سازی و لینک اشتراک؛ فقط در صورت نیاز."
                                "nodes" -> "نودها را مدیریت کن و با یک انتخاب، فرم ساخت کانفیگ را پر کن."
                                "tools" -> "ابزارهای جانبی و قابلیت‌های در حال توسعه."
                                else -> "ساخت کانفیگ در چند قدم ساده؛ بدون شلوغی تنظیمات."
                            }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("create" to "ساخت", "library" to "ذخیره‌ها", "nodes" to "نودها", "cloud" to "ابر", "tools" to "ابزارها").forEach { (section, label) ->
                            if (selectedSection == section) {
                                Button(onClick = { selectedSection = section; if (section == "nodes" && cloudToken.isNotBlank()) refreshRemoteNodes(true) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)) { Text(label, maxLines = 1) }
                            } else {
                                OutlinedButton(onClick = { selectedSection = section }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)) { Text(label, maxLines = 1) }
                            }
                        }
                    }
                }
            }
            if (selectedSection == "cloud") {
            item {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("همگام‌سازی ابری (اختیاری)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showCloudSettings = !showCloudSettings }) {
                            Text(if (showCloudSettings) "بستن تنظیمات" else "تنظیمات ابری")
                        }
                    }
                    if (showCloudSettings) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("همگام‌سازی ابری اختیاری", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("برای استفاده از این بخش وب‌پنل لازم نیست. آدرس Cloudflare Worker خودت را وارد کن؛ کانفیگ‌ها به‌صورت انتخابی با حساب آنلاین همگام می‌شوند.")
                        OutlinedTextField(
                            value = cloudBaseUrl,
                            onValueChange = { value ->
                                if (value.trimEnd('/') != cloudBaseUrl.trimEnd('/')) {
                                    cloudToken = ""
                                    cloudSubscription = null
                                    cloudPrefs.edit().remove("token_enc").apply()
                                }
                                cloudBaseUrl = value
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("آدرس API (HTTPS)") },
                            placeholder = { Text("https://spd-con-api.example.workers.dev") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = cloudUsername,
                            onValueChange = { cloudUsername = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("نام کاربری") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = cloudPassword,
                            onValueChange = { cloudPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("رمز عبور (حداقل ۱۲ نویسه)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = !cloudBusy && cloudBaseUrl.startsWith("https://") && cloudUsername.isNotBlank() && cloudPassword.length >= 12,
                                onClick = {
                                    cloudBusy = true
                                    coroutineScope.launch {
                                        val result = runCatching {
                                            withContext(Dispatchers.IO) {
                                                CloudSyncClient.register(cloudBaseUrl, cloudUsername, cloudPassword)
                                            }
                                        }
                                        cloudMessage = result.fold(
                                            onSuccess = { "حساب ساخته شد؛ حالا وارد شو." },
                                            onFailure = { "ثبت‌نام ناموفق: ${it.message}" }
                                        )
                                        cloudBusy = false
                                    }
                                }
                            ) { Text("ثبت‌نام") }
                            Button(
                                enabled = !cloudBusy && cloudBaseUrl.startsWith("https://") && cloudUsername.isNotBlank() && cloudPassword.isNotBlank(),
                                onClick = {
                                    cloudBusy = true
                                    coroutineScope.launch {
                                        val result = runCatching {
                                            withContext(Dispatchers.IO) {
                                                CloudSyncClient.login(cloudBaseUrl, cloudUsername, cloudPassword)
                                            }
                                        }
                                        result.onSuccess { session ->
                                            cloudToken = session.token
                                            cloudUsername = session.username
                                            cloudPrefs.edit()
                                                .putString("base_url", cloudBaseUrl.trim())
                                                .putString("username", session.username)
                                                .putString("token_enc", CredentialCipher.encrypt(session.token))
                                                .apply()
                                            cloudPassword = ""
                                        }
                                        cloudMessage = result.fold(
                                            onSuccess = { "ورود انجام شد؛ نشست به‌صورت رمزگذاری‌شده روی دستگاه ذخیره شد." },
                                            onFailure = { "ورود ناموفق: ${it.message}" }
                                        )
                                        cloudBusy = false
                                    }
                                }
                            ) { Text(if (cloudBusy) "در حال انجام…" else "ورود") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = !cloudBusy && cloudToken.isNotBlank(),
                                onClick = {
                                    cloudBusy = true
                                    cloudPrefs.edit().putString("base_url", cloudBaseUrl.trim()).apply()
                                    coroutineScope.launch {
                                        val result = runCatching {
                                            withContext(Dispatchers.IO) {
                                                CloudSyncClient.upload(cloudBaseUrl, cloudToken, libraryStore.loadAll())
                                            }
                                        }
                                        cloudMessage = result.fold(
                                            onSuccess = { "ارسال تمام شد: ${it.uploaded} مورد ارسال شد، ${it.skipped} مورد تکراری/ردشده بود." },
                                            onFailure = { "همگام‌سازی ناموفق: ${it.message}" }
                                        )
                                        cloudBusy = false
                                    }
                                }
                            ) { Text("ارسال به ابر") }
                            OutlinedButton(
                                enabled = !cloudBusy && cloudToken.isNotBlank(),
                                onClick = {
                                    cloudBusy = true
                                    cloudPrefs.edit().putString("base_url", cloudBaseUrl.trim()).apply()
                                    coroutineScope.launch {
                                        val result = runCatching {
                                            withContext(Dispatchers.IO) {
                                                CloudSyncClient.download(cloudBaseUrl, cloudToken, libraryStore)
                                            }
                                        }
                                        result.onSuccess { savedProfiles = libraryStore.loadAll() }
                                        cloudMessage = result.fold(
                                            onSuccess = { "دریافت تمام شد: ${it.downloaded} کانفیگ ذخیره شد؛ ${it.skipped} مورد رد شد." },
                                            onFailure = { "دریافت ناموفق: ${it.message}" }
                                        )
                                        cloudBusy = false
                                    }
                                }
                            ) { Text("دریافت از ابر") }
                        }
                        OutlinedButton(
                            enabled = !cloudBusy && cloudToken.isNotBlank(),
                            onClick = {
                                cloudBusy = true
                                coroutineScope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            CloudSyncClient.createSubscription(cloudBaseUrl, cloudToken)
                                        }
                                    }
                                    cloudSubscription = result.getOrNull()
                                    cloudMessage = result.fold(
                                        onSuccess = { "لینک اشتراک ساخته شد. آن را محرمانه نگه دار." },
                                        onFailure = { "ساخت اشتراک ناموفق: ${it.message}" }
                                    )
                                    cloudBusy = false
                                }
                            }
                        ) { Text("ساخت لینک اشتراک") }
                        cloudSubscription?.let { url ->
                            Text(url, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                clipboardManager.setText(AnnotatedString(url))
                                cloudMessage = "لینک اشتراک کپی شد."
                            }) { Text("کپی لینک اشتراک") }
                        }
                        cloudMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (cloudToken.isNotBlank()) {
                            TextButton(onClick = {
                                val tokenToRevoke = cloudToken
                                val baseToRevoke = cloudBaseUrl
                                cloudToken = ""
                                cloudSubscription = null
                                cloudPrefs.edit().remove("token_enc").apply()
                                cloudBusy = true
                                coroutineScope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            CloudSyncClient.logout(baseToRevoke, tokenToRevoke)
                                        }
                                    }
                                    cloudMessage = if (result.isSuccess) {
                                        "از حساب خارج شدی و نشست سرور باطل شد."
                                    } else {
                                        "نشست محلی حذف شد؛ خروج از سرور تأیید نشد."
                                    }
                                    cloudBusy = false
                                }
                            }) { Text("خروج از حساب ابری") }
                        }
                            }
                        }
                    }
                }
            }

            }
            if (selectedSection == "nodes") {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("مدیریت نودها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("مشخصات نود را یک‌بار ثبت کن؛ بعد در ساخت سریع انتخابش کن تا آدرس، انتقال، امنیت و مقادیر REALITY خودش پر شود.")
                            Text("این بخش تنظیمات نود را نگه می‌دارد؛ نصب Xray یا ثبت خودکار UUID روی سرور هنوز به Node Agent متصل نیاز دارد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(value = nodeNameDraft, onValueChange = { nodeNameDraft = it }, modifier = Modifier.fillMaxWidth(), label = { Text("نام نود") }, singleLine = true)
                            OutlinedTextField(value = nodeAddressDraft, onValueChange = { nodeAddressDraft = it }, modifier = Modifier.fillMaxWidth(), label = { Text("دامنه یا IP نود") }, singleLine = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = nodePortDraft, onValueChange = { nodePortDraft = it.filter(Char::isDigit).take(5) }, modifier = Modifier.weight(1f), label = { Text("پورت") }, singleLine = true)
                                OutlinedTextField(value = nodeCountryDraft, onValueChange = { nodeCountryDraft = it.uppercase().take(2) }, modifier = Modifier.weight(1f), label = { Text("کشور (مثلاً DE)") }, singleLine = true)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = nodeProtocolDraft, onValueChange = { nodeProtocolDraft = it.lowercase() }, modifier = Modifier.weight(1f), label = { Text("پروتکل") }, singleLine = true)
                                OutlinedTextField(value = nodeNetworkDraft, onValueChange = { nodeNetworkDraft = it.lowercase() }, modifier = Modifier.weight(1f), label = { Text("انتقال") }, singleLine = true)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = nodeSecurityDraft, onValueChange = { nodeSecurityDraft = it.lowercase() }, modifier = Modifier.weight(1f), label = { Text("امنیت (reality/tls)") }, singleLine = true)
                                OutlinedTextField(value = nodeFingerprintDraft, onValueChange = { nodeFingerprintDraft = it.lowercase() }, modifier = Modifier.weight(1f), label = { Text("Fingerprint") }, singleLine = true)
                            }
                            OutlinedTextField(value = nodeSniDraft, onValueChange = { nodeSniDraft = it.trim() }, modifier = Modifier.fillMaxWidth(), label = { Text("SNI / serverName") }, singleLine = true)
                            if (nodeSecurityDraft.equals("reality", true)) {
                                OutlinedTextField(value = nodePublicKeyDraft, onValueChange = { nodePublicKeyDraft = it.trim() }, modifier = Modifier.fillMaxWidth(), label = { Text("کلید عمومی REALITY نود") }, singleLine = true)
                                OutlinedTextField(value = nodeShortIdDraft, onValueChange = { nodeShortIdDraft = it.trim() }, modifier = Modifier.fillMaxWidth(), label = { Text("shortId نود") }, singleLine = true)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = !cloudBusy && cloudToken.isNotBlank() && nodeNameDraft.isNotBlank() && nodeAddressDraft.isNotBlank() && (nodeSecurityDraft != "reality" || (nodeSniDraft.isNotBlank() && nodePublicKeyDraft.isNotBlank())), onClick = {
                                    val port = nodePortDraft.toIntOrNull()
                                    if (port == null || port !in 1..65535) {
                                        nodeMessage = "پورت باید بین 1 تا 65535 باشد."
                                    } else {
                                        cloudBusy = true
                                        coroutineScope.launch {
                                            val result = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    CloudSyncClient.createNode(cloudBaseUrl, cloudToken, CloudSyncClient.RemoteNode(
                                                        id = "", name = nodeNameDraft.trim(), address = nodeAddressDraft.trim(), port = port,
                                                        country = nodeCountryDraft.ifBlank { "auto" }, protocol = nodeProtocolDraft,
                                                        network = nodeNetworkDraft, security = nodeSecurityDraft, sni = nodeSniDraft.trim(),
                                                        publicKey = nodePublicKeyDraft.trim(), shortId = nodeShortIdDraft.trim(),
                                                        fingerprint = nodeFingerprintDraft.ifBlank { "chrome" }
                                                    ))
                                                }
                                            }
                                            result.onSuccess { node ->
                                                nodeNameDraft = ""; nodeAddressDraft = ""; nodeSniDraft = ""; nodePublicKeyDraft = ""; nodeShortIdDraft = ""
                                                nodeMessage = "نود ثبت شد: ${node.name}"
                                                refreshRemoteNodes(true)
                                            }.onFailure { nodeMessage = "ثبت نود ناموفق بود: ${it.message}"; cloudBusy = false }
                                        }
                                    }
                                }) { Text(if (cloudBusy) "در حال انجام…" else "ثبت نود") }
                                OutlinedButton(enabled = !cloudBusy && cloudToken.isNotBlank(), onClick = { refreshRemoteNodes(true) }) { Text("تازه‌سازی") }
                            }
                            nodeMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                items(remoteNodes, key = { it.id }) { node ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(node.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${node.address}:${node.port} · ${node.country} · ${node.protocol.uppercase()} · ${node.security}", style = MaterialTheme.typography.bodySmall)
                            if (node.security.equals("reality", true)) Text("SNI: ${node.sni} · shortId: ${node.shortId}", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { applyRemoteNode(node) }) { Text("استفاده در ساخت") }
                                if (cloudToken.isNotBlank()) OutlinedButton(onClick = {
                                    cloudBusy = true
                                    coroutineScope.launch {
                                        val result = runCatching { withContext(Dispatchers.IO) { CloudSyncClient.deleteNode(cloudBaseUrl, cloudToken, node.id) } }
                                        result.onSuccess { nodeMessage = "نود حذف شد"; refreshRemoteNodes(true) }
                                            .onFailure { nodeMessage = "حذف فقط برای مدیر مجاز است: ${it.message}"; cloudBusy = false }
                                    }
                                }) { Text("حذف") }
                            }
                        }
                    }
                }
            }
            if (selectedSection == "create") {
            item {
                OutlinedButton(
                    onClick = { showLinkImport = !showLinkImport },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showLinkImport) "بستن ورود لینک کانفیگ" else "ورود کانفیگ موجود (اختیاری)")
                }
            }
            if (showLinkImport) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("موتور ساخت کانفیگ", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(6.dp))
                        Text("لینک VLESS، VMess، Trojan، Shadowsocks، Hysteria2 یا TUIC را وارد کن تا اطلاعاتش خوانده و بررسی شود.")
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = shareLink,
                            onValueChange = {
                                shareLink = it
                                parseResult = null
                                parsedSummary = null
                                parsedProfile = null
                                generatedOutput = null
                                qrContent = null
                                copyContent = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("لینک کانفیگ") },
                            placeholder = { Text("vless://… یا ss://…") },
                            minLines = 2,
                            maxLines = 4
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val result = ConfigLinkParser.parse(shareLink)
                                if (result.isSuccess) {
                                    val profile = result.getOrThrow()
                                    parsedProfile = profile
                                    parseResult = "لینک خوانده شد؛ این بررسی ساختاری است و اتصال واقعی را تأیید نمی‌کند."
                                    parsedSummary = listOf(
                                        "نام: ${profile.name}",
                                        "پروتکل: ${profile.protocol}",
                                        "سرور: ${profile.server}:${profile.port}",
                                        "انتقال: ${profile.network}",
                                        "امنیت: ${profile.security}",
                                        "بررسی اولیه:",
                                        com.spdcon.app.config.ConfigDiagnostics.format(profile)
                                    ).joinToString("\n")
                                    if (profile.security.equals("reality", true)) {
                                        val reality = profile.toRealitySettings()
                                        realityServerName = reality.serverName
                                        realityPublicKey = reality.publicKey
                                        realityShortId = reality.shortId
                                        realityFingerprint = reality.fingerprint
                                        realitySpiderX = reality.spiderX
                                        realityFlow = reality.flow
                                    }
                                } else {
                                    parseResult = result.exceptionOrNull()?.message ?: "لینک قابل خواندن نیست."
                                    parsedSummary = null
                                    parsedProfile = null
                                }
                                generatedOutput = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("  خواندن و اعتبارسنجی لینک")
                        }
                        parseResult?.let { Text(it, modifier = Modifier.padding(top = 10.dp)) }
                        parsedSummary?.let { summary ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(summary, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ساخت دستی کانفیگ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("ساخت سریع")
                        SmartConfigWizard { choice ->
                            manualProtocol = ConfigProtocol.valueOf(choice.protocol)
                            manualNetwork = choice.transport
                            manualSecurity = choice.security
                            manualPort = choice.port
                            manualAlpn = choice.alpn
                            if (choice.goal == SmartGoal.CLOUDFLARE) {
                                manualCdnEnabled = true
                                manualPath = manualPath.ifBlank { "/" }
                                if (manualServer.isNotBlank()) {
                                    manualHost = manualServer.trim()
                                    manualSni = manualServer.trim()
                                }
                            } else {
                                manualCdnEnabled = false
                            }
                        }
                        OutlinedButton(onClick = { manualProtocolMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("پروتکل: $manualProtocol")
                        }
                        DropdownMenu(expanded = manualProtocolMenuExpanded, onDismissRequest = { manualProtocolMenuExpanded = false }) {
                            ConfigProtocol.entries.forEach { protocol ->
                                DropdownMenuItem(
                                    text = { Text(protocol.name) },
                                    onClick = { manualProtocol = protocol; manualProtocolMenuExpanded = false }
                                )
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("راهنمای سریع اتصال واقعی", fontWeight = FontWeight.Bold)
                                Text("۱) اگر نود آماده داری، از «انتخاب نود ذخیره‌شده» استفاده کن؛ اطلاعات REALITY از همان نود پر می‌شود.")
                                Text("۲) اگر لینک کامل داری، بخش «واردکردن لینک» را باز کن و لینک vless:// یا لینک سازگار را وارد کن تا فیلدها خودکار خوانده شوند.")
                                Text("۳) اگر فقط SNI مثل digikala.com داری، هنوز نود آماده نیست؛ کلید REALITY و shortId از دامنه به دست نمی‌آیند. برای جلوگیری از لینک ظاهراً آماده اما خراب، برنامه این مقادیر را جعل نمی‌کند.")
                                Text("نکته: ساخت لینک با اتصال واقعی فرق دارد؛ سرور باید کاربر/UUID را هم بشناسد.", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { nodeMenuExpanded = true }, modifier = Modifier.weight(1f)) {
                                        Text("انتخاب نود")
                                    }
                                    Button(onClick = { showLinkImport = true; selectedSection = "create" }, modifier = Modifier.weight(1f)) {
                                        Text("واردکردن لینک")
                                    }
                                }
                            }
                        }
                        OutlinedButton(onClick = { nodeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("انتخاب نود ذخیره‌شده (پرکردن خودکار)") }
                        DropdownMenu(expanded = nodeMenuExpanded, onDismissRequest = { nodeMenuExpanded = false }) {
                            remoteNodes.filter { it.enabled }.forEach { node ->
                                DropdownMenuItem(text = { Text("${node.name} · ${node.country}") }, onClick = { applyRemoteNode(node) })
                            }
                            if (remoteNodes.isEmpty()) DropdownMenuItem(text = { Text("نودی نیست؛ از بخش نودها بارگذاری کن") }, onClick = { nodeMenuExpanded = false; selectedSection = "nodes" })
                        }
                        OutlinedTextField(value = manualName, onValueChange = { manualName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("نام کانفیگ") }, singleLine = true)
                        OutlinedTextField(
                            value = manualServer,
                            onValueChange = {
                                manualServer = it
                                if (manualCdnEnabled) {
                                    manualHost = it.trim()
                                    manualSni = it.trim()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("دامنه یا IP سرور") },
                            supportingText = { Text(if (manualCdnEnabled) "در حالت CDN، دامنه‌ای وارد کن که واقعاً در Cloudflare روی Proxy باشد." else "دامنه یا IP واقعی نود را وارد کن.") },
                            singleLine = true
                        )
                        OutlinedTextField(value = manualPort, onValueChange = { manualPort = it }, modifier = Modifier.fillMaxWidth(), label = { Text("پورت") }, singleLine = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = manualCdnEnabled,
                                onCheckedChange = { enabled ->
                                    manualCdnEnabled = enabled
                                    if (enabled) {
                                        manualProtocol = ConfigProtocol.VLESS
                                        manualNetwork = "ws"
                                        manualSecurity = "tls"
                                        manualPort = "443"
                                        manualPath = manualPath.ifBlank { "/" }
                                        manualHost = manualServer.trim()
                                        manualSni = manualServer.trim()
                                    }
                                }
                            )
                            Column {
                                Text("استفاده اختیاری از CDN کلودفلر")
                                Text("دامنه، Host و SNI هماهنگ می‌شوند؛ خود دامنه باید Proxy شده و مبدأ سازگار باشد.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        OutlinedButton(onClick = { countryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("کشور هدف: " + when (selectedCountry) {
                                "US" -> "آمریکا"; "DE" -> "آلمان"; "NL" -> "هلند"; "FR" -> "فرانسه"
                                "GB" -> "بریتانیا"; "TR" -> "ترکیه"; "FI" -> "فنلاند"; "SE" -> "سوئد"
                                "JP" -> "ژاپن"; "SG" -> "سنگاپور"; else -> "خودکار"
                            })
                        }
                        DropdownMenu(expanded = countryMenuExpanded, onDismissRequest = { countryMenuExpanded = false }) {
                            listOf("auto" to "خودکار", "US" to "آمریکا", "DE" to "آلمان", "NL" to "هلند", "FR" to "فرانسه", "GB" to "بریتانیا", "TR" to "ترکیه", "FI" to "فنلاند", "SE" to "سوئد", "JP" to "ژاپن", "SG" to "سنگاپور").forEach { (code, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { selectedCountry = code; countryMenuExpanded = false })
                            }
                        }
                        Text("انتخاب کشور فعلاً فقط یک ترجیح است؛ اسکن و انتخاب خودکار IP هنوز فعال نیست.", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = blockAdultSites, onCheckedChange = { blockAdultSites = it })
                            Column {
                                Text("مسدودکردن سایت‌های بزرگسال")
                                Text("فقط در خروجی JSON سازگار با Xray؛ لینک معمولی این فیلتر را اعمال نمی‌کند.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(
                            "اطلاعات ورود به‌صورت خودکار ساخته و داخل کانفیگ ذخیره می‌شود؛ لازم نیست UUID یا رمز را دستی وارد کنی. برای اتصال واقعی، این اطلاعات باید از طریق پنل روی نود سرور هم ثبت شوند.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (manualProtocol == ConfigProtocol.SHADOWSOCKS) {
                            OutlinedTextField(value = manualMethod, onValueChange = { manualMethod = it }, modifier = Modifier.fillMaxWidth(), label = { Text("روش رمزنگاری Shadowsocks") }, singleLine = true)
                        }
                        if (manualProtocol == ConfigProtocol.HYSTERIA2 || manualProtocol == ConfigProtocol.TUIC) {
                            Text("این پروتکل با UDP و TLS ساخته می‌شود.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(
                                "انتقال و امنیت به‌صورت پیشنهادی تنظیم می‌شوند. فقط اگر اطلاعات نود را می‌دانی، از تنظیمات تکمیلی تغییرشان بده.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = showAdvancedBuilder, onCheckedChange = { showAdvancedBuilder = it })
                            Text("تنظیمات تکمیلی ساخت کانفیگ")
                        }
                        if (showAdvancedBuilder) {
                            Text("معمولاً لازم نیست این موارد را تغییر بدهی.", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = manualNetwork,
                                onValueChange = { manualNetwork = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("انتقال (tcp / ws / grpc / xhttp)") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = manualSecurity,
                                onValueChange = { manualSecurity = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("امنیت (none / tls / reality)") },
                                singleLine = true
                            )
                            OutlinedTextField(value = manualHost, onValueChange = { manualHost = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Host / HTTP Host") }, singleLine = true)
                            OutlinedTextField(value = manualWsPath, onValueChange = { manualWsPath = it }, modifier = Modifier.fillMaxWidth(), label = { Text("مسیر مستقل WebSocket (باید {uuid} داشته باشد)") }, supportingText = { Text("نمونه: /ws/{uuid} یا /de/ws/{uuid}") }, singleLine = true)
                            OutlinedTextField(value = manualPath, onValueChange = { manualPath = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Path (XHTTP)") }, supportingText = { Text("مسیر XHTTP مستقل از مسیر WebSocket است.") }, singleLine = true)
                            OutlinedTextField(value = manualServiceName, onValueChange = { manualServiceName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("gRPC serviceName") }, singleLine = true)
                            OutlinedTextField(value = manualSni, onValueChange = { manualSni = it }, modifier = Modifier.fillMaxWidth(), label = { Text("TLS SNI / serverName") }, singleLine = true)
                            OutlinedTextField(value = manualAlpn, onValueChange = { manualAlpn = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ALPN (با کاما جدا کن)") }, singleLine = true)
                            OutlinedTextField(value = manualHeaderType, onValueChange = { manualHeaderType = it }, modifier = Modifier.fillMaxWidth(), label = { Text("HTTP header type") }, singleLine = true)
                            OutlinedTextField(value = manualEncryption, onValueChange = { manualEncryption = it }, modifier = Modifier.fillMaxWidth(), label = { Text("VLESS encryption") }, singleLine = true)
                            OutlinedTextField(value = manualMode, onValueChange = { manualMode = it }, modifier = Modifier.fillMaxWidth(), label = { Text("XHTTP mode (در صورت نیاز)") }, singleLine = true)
                            if (manualProtocol == ConfigProtocol.HYSTERIA2) {
                                OutlinedTextField(value = manualObfs, onValueChange = { manualObfs = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Hysteria2 obfs (اختیاری)") }, singleLine = true)
                                OutlinedTextField(value = manualObfsPassword, onValueChange = { manualObfsPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Hysteria2 obfs-password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                            }
                            if (manualProtocol == ConfigProtocol.TUIC) {
                                OutlinedTextField(value = manualCongestionControl, onValueChange = { manualCongestionControl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("TUIC congestion control") }, singleLine = true)
                                OutlinedTextField(value = manualUdpRelayMode, onValueChange = { manualUdpRelayMode = it }, modifier = Modifier.fillMaxWidth(), label = { Text("TUIC UDP relay mode") }, singleLine = true)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = manualAllowInsecure, onCheckedChange = { manualAllowInsecure = it })
                                Text("اجازه گواهی TLS نامعتبر (ناامن)")
                            }
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("لینک Telegram Web Proxy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("فقط لینک اتصال به پراکسیِ از قبل راه‌اندازی‌شده ساخته می‌شود؛ این بخش سرور یا secret معتبر تولید نمی‌کند.", style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(value = proxyHostname, onValueChange = { proxyHostname = it; proxyMessage = null }, modifier = Modifier.fillMaxWidth(), label = { Text("دامنه پراکسی") }, singleLine = true)
                                OutlinedTextField(value = proxySecret, onValueChange = { proxySecret = it; proxyMessage = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Secret پراکسیِ نصب‌شده") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                                Button(onClick = {
                                    TelegramWebProxyLinkBuilder.build(proxyHostname, proxySecret).fold(
                                        onSuccess = { link ->
                                            generatedOutput = link
                                            multiGenerationOutput = link
                                            shareLink = link
                                            qrContent = link
                                            copyContent = link
                                            proxyMessage = "لینک ساخته شد. اعتبار secret و دسترسی واقعی سرور هنوز باید روی پراکسی بررسی شود."
                                        },
                                        onFailure = { proxyMessage = it.message ?: "ورودی پراکسی معتبر نیست." }
                                    )
                                }, modifier = Modifier.fillMaxWidth()) { Text("ساخت لینک و QR") }
                                proxyMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        Text("تولید چندکانفیگی از اینباند", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("برای یک کاربر شناسه ثابت نگه می‌دارد و از مسیرهای صریح نود چند لینک می‌سازد. این کار به‌تنهایی کاربر را روی سرور ثبت نمی‌کند.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { multiGenerationMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("نوع ساخت چندکانفیگی: $multiGenerationKind")
                        }
                        DropdownMenu(expanded = multiGenerationMenuExpanded, onDismissRequest = { multiGenerationMenuExpanded = false }) {
                            listOf("TLS Relay", "REALITY", "Cloudflare Worker").forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = {
                                    multiGenerationKind = option
                                    multiGenerationMenuExpanded = false
                                    multiGenerationMessage = null
                                    multiGenerationOutput = null
                                })
                            }
                        }
                        if (multiGenerationKind == "Cloudflare Worker") {
                            OutlinedTextField(
                                value = workerPathDraft,
                                onValueChange = { workerPathDraft = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("مسیر Worker با {uuid}") },
                                supportingText = { Text("باید دقیقاً روی Worker مستقر شده باشد؛ نمونه: /ws/{uuid}") },
                                singleLine = true
                            )
                        }
                        Button(
                            onClick = {
                                val inbound = runCatching {
                                    val endpoint = manualServer.trim()
                                    val port = manualPort.toIntOrNull() ?: -1
                                    when (multiGenerationKind) {
                                        "REALITY" -> ConfigInboundSpec(
                                            id = "manual-reality",
                                            name = manualName.trim().ifBlank { "REALITY" },
                                            kind = ConfigInboundKind.REALITY,
                                            endpoint = endpoint,
                                            port = port,
                                            sni = realityServerName.trim().ifBlank { manualSni.trim() },
                                            publicKey = realityPublicKey.trim(),
                                            shortId = realityShortId.trim(),
                                            fingerprint = realityFingerprint.trim().ifBlank { "chrome" },
                                            spiderX = realitySpiderX.trim().ifBlank { "/" },
                                            realityTransports = if (manualNetwork.equals("tcp", true)) listOf("tcp") else if (manualNetwork.equals("xhttp", true)) listOf("xhttp") else listOf("tcp", "xhttp"),
                                            xhttpMode = manualMode.trim().ifBlank { "stream-up" },
                                            path = manualPath.trim().ifBlank { "/" }
                                        )
                                        "TLS Relay" -> ConfigInboundSpec(
                                            id = "manual-tls-relay",
                                            name = manualName.trim().ifBlank { "TLS Relay" },
                                            kind = ConfigInboundKind.TLS_RELAY,
                                            endpoint = endpoint,
                                            port = port,
                                            sni = manualSni.trim().ifBlank { manualHost.trim() },
                                            host = manualHost.trim().ifBlank { manualServer.trim() },
                                            fingerprint = realityFingerprint.trim().ifBlank { "chrome" },
                                            path = manualPath.trim().ifBlank { "/" },
                                            wsPath = manualWsPath.trim().ifBlank { "/ws/{uuid}" },
                                            xhttpMode = manualMode.trim().ifBlank { "stream-up" },
                                            alpn = manualAlpn.trim().ifBlank { "h2,http/1.1" }
                                        )
                                        else -> ConfigInboundSpec(
                                            id = "manual-worker",
                                            name = manualName.trim().ifBlank { "Cloudflare Worker" },
                                            kind = ConfigInboundKind.CLOUDFLARE_WORKER,
                                            endpoint = endpoint,
                                            port = port,
                                            sni = manualSni.trim().ifBlank { manualHost.trim().ifBlank { endpoint } },
                                            host = manualHost.trim().ifBlank { endpoint },
                                            fingerprint = realityFingerprint.trim().ifBlank { "chrome" },
                                            workerRoutes = listOf(WorkerRoute("custom", workerPathDraft.trim()))
                                        )
                                    }
                                }
                                val result = inbound.fold(
                                    onSuccess = { spec ->
                                        ConfigGenerationService.generateForUser(
                                            userId = stableGenerationUserId,
                                            displayName = manualName.trim().ifBlank { "Spd con user" },
                                            inbounds = listOf(spec),
                                            blockAdultSites = blockAdultSites
                                        )
                                    },
                                    onFailure = { Result.failure(it) }
                                )
                                result.fold(
                                    onSuccess = { report ->
                                        val profiles = report.configs.map { it.profile }
                                        val saveResults = profiles.map { libraryStore.save(it) }
                                        savedProfiles = libraryStore.loadAll()
                                        val links = report.configs.map { it.shareLink }
                                        val combined = links.joinToString("\n")
                                        shareLink = links.firstOrNull().orEmpty()
                                        parsedProfile = profiles.firstOrNull()
                                        qrContent = links.firstOrNull()
                                        copyContent = combined
                                        generatedOutput = links.mapIndexed { index, link ->
                                            "${index + 1}. ${report.configs[index].label}\n$link"
                                        }.joinToString("\n\n")
                                        multiGenerationOutput = generatedOutput
                                        multiGenerationMessage = buildString {
                                            append("ساخت ${profiles.size} لینک انجام شد؛ شناسه کاربر ثابت است: $stableGenerationUserId. ")
                                            append("ذخیره موفق: ${saveResults.count { it.isSuccess }} از ${saveResults.size}. ")
                                            append("برای اتصال واقعی باید همین UUID در سرور ثبت شده و مسیر/کلید/پورت با سرویس فعال مطابقت داشته باشد.")
                                            if (report.warnings.isNotEmpty()) append("\n" + report.warnings.joinToString("\n"))
                                        }
                                        parseResult = multiGenerationMessage
                                        parsedSummary = profiles.joinToString("\n\n") { profile ->
                                            "${profile.name}\n${profile.server}:${profile.port} · ${profile.network} · ${profile.security}\n${com.spdcon.app.config.ConfigDiagnostics.format(profile)}"
                                        }
                                    },
                                    onFailure = {
                                        multiGenerationMessage = "ساخت چندکانفیگی ناموفق بود: ${it.message}"
                                        multiGenerationOutput = null
                                        generatedOutput = null
                                        qrContent = null
                                        copyContent = null
                                        parseResult = multiGenerationMessage
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = manualServer.isNotBlank() && (manualPort.toIntOrNull() ?: 0) in 1..65535
                        ) { Text("ساخت چندکانفیگی و ذخیره خودکار") }
                        multiGenerationMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        multiGenerationOutput?.let { output ->
                            OutlinedTextField(
                                value = output,
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("لینک‌های تولیدشده") },
                                readOnly = true,
                                minLines = 3,
                                maxLines = 12
                            )
                            OutlinedButton(
                                onClick = { clipboardManager.setText(AnnotatedString(copyContent ?: output)) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("کپی همه لینک‌ها") }
                        }
                        Button(
                            onClick = {
                                val generatedPassword = java.util.UUID.randomUUID().toString().replace("-", "")
                                val parameters = buildMap {
                                    if (manualProtocol == ConfigProtocol.SHADOWSOCKS) put("method", manualMethod.trim())
                                    if (selectedCountry != "auto") put("country", selectedCountry)
                                    put("blockAdult", blockAdultSites.toString())
                                    if (manualHost.isNotBlank()) put("host", manualHost.trim())
                                    if (manualPath.isNotBlank()) put("path", manualPath.trim())
                                    if (manualProtocol == ConfigProtocol.HYSTERIA2 && manualObfs.isNotBlank()) put("obfs", manualObfs.trim())
                                    if (manualProtocol == ConfigProtocol.HYSTERIA2 && manualObfsPassword.isNotBlank()) put("obfs-password", manualObfsPassword.trim())
                                    if (manualProtocol == ConfigProtocol.TUIC) {
                                        put("password", generatedPassword)
                                        put("congestion_control", manualCongestionControl.trim())
                                        put("udp_relay_mode", manualUdpRelayMode.trim())
                                    }
                                    if (manualServiceName.isNotBlank()) put("serviceName", manualServiceName.trim())
                                    if (manualSni.isNotBlank() && !manualSecurity.equals("reality", true)) put("sni", manualSni.trim())
                                    if (manualSecurity.equals("tls", true)) put("fp", realityFingerprint.trim())
                                    if (manualAlpn.isNotBlank()) {
                                        val alpnValue = if ((manualProtocol == ConfigProtocol.HYSTERIA2 || manualProtocol == ConfigProtocol.TUIC) && manualAlpn.trim() == "h2,http/1.1") "h3" else manualAlpn
                                        put("alpn", alpnValue.split(",").map { it.trim() }.filter { it.isNotBlank() }.joinToString(","))
                                    }
                                    if (manualHeaderType.isNotBlank()) put("headerType", manualHeaderType.trim())
                                    if (manualProtocol == ConfigProtocol.VLESS && manualEncryption.isNotBlank()) put("encryption", manualEncryption.trim())
                                    if (manualMode.isNotBlank()) put("mode", manualMode.trim())
                                    if (manualAllowInsecure) put(when (manualProtocol) { ConfigProtocol.HYSTERIA2 -> "insecure"; ConfigProtocol.TUIC -> "allow_insecure"; else -> "allowInsecure" }, "1")
                                    if (manualSecurity.equals("reality", true)) {
                                        put("sni", realityServerName.trim())
                                        put("pbk", realityPublicKey.trim())
                                        put("sid", realityShortId.trim())
                                        put("fp", realityFingerprint.trim())
                                        put("spx", realitySpiderX.trim())
                                        put("flow", realityFlow.trim())
                                    }
                                }.filterValues { it.isNotBlank() }.toMutableMap().apply {
                                    if (manualCdnEnabled) {
                                        put("host", manualServer.trim())
                                        put("sni", manualServer.trim())
                                        put("path", manualPath.trim().ifBlank { "/" })
                                    }
                                }
                                val profile = ConfigProfile(
                                    name = manualName.trim(),
                                    protocol = manualProtocol,
                                    server = manualServer.trim(),
                                    port = manualPort.toIntOrNull() ?: -1,
                                    credential = if (manualProtocol == ConfigProtocol.VLESS || manualProtocol == ConfigProtocol.VMESS) java.util.UUID.randomUUID().toString() else generatedPassword,
                                    network = if (manualProtocol == ConfigProtocol.HYSTERIA2 || manualProtocol == ConfigProtocol.TUIC) "udp" else if (manualCdnEnabled) "ws" else if (manualNetwork.isBlank() || manualNetwork.equals("auto", true)) "tcp" else manualNetwork.trim(),
                                    security = if (manualProtocol == ConfigProtocol.HYSTERIA2 || manualProtocol == ConfigProtocol.TUIC || manualCdnEnabled) "tls" else manualSecurity.trim().lowercase(),
                                    remark = manualName.trim(),
                                    parameters = parameters
                                )
                                val cdnErrors = if (manualCdnEnabled && !com.spdcon.app.config.SmartConfigAdvisor.looksLikeHostname(manualServer)) {
                                    listOf("برای CDN، دامنه معتبر وارد کن؛ IP خام به‌تنهایی Host/SNI و تنظیم DNS پروکسی‌شده را تأمین نمی‌کند.")
                                } else emptyList()
                                val errors = profile.validate() + cdnErrors + if (manualSecurity.equals("reality", true) && (realityPublicKey.isBlank() || realityShortId.isBlank())) listOf("برای REALITY معتبر، کلید عمومی و shortId مخصوص همان سرور لازم است؛ SNI به‌تنهایی این مقادیر را نمی‌سازد.") else emptyList()
                                if (errors.isNotEmpty()) {
                                    parsedProfile = null
                                    parsedSummary = null
                                    generatedOutput = null
                                    qrContent = null
                                    copyContent = null
                                    parseResult = errors.joinToString("; ")
                                } else {
                                    val serialized = ConfigLinkSerializer.serialize(profile)
                                    if (serialized.isFailure) {
                                        parsedProfile = null
                                        parsedSummary = null
                                        generatedOutput = null
                                        qrContent = null
                                        copyContent = null
                                        parseResult = "ساخت لینک ناموفق بود: ${serialized.exceptionOrNull()?.message ?: "خطای نامشخص"}"
                                    } else {
                                        val link = serialized.getOrThrow()
                                        val autoSaveResult = libraryStore.save(profile)
                                        savedProfiles = libraryStore.loadAll()
                                        libraryMessage = autoSaveResult.fold(
                                            onSuccess = { "کانفیگ ساخته و خودکار در کتابخانه ذخیره شد." },
                                            onFailure = { "کانفیگ ساخته شد، اما ذخیره خودکار ناموفق بود: ${it.message}" }
                                        )
                                        parsedProfile = profile
                                        parsedSummary = listOf(
                                            "نام: ${profile.name}",
                                            "پروتکل: ${profile.protocol}",
                                            "سرور: ${profile.server}:${profile.port}",
                                            "انتقال: ${profile.network}",
                                            "امنیت: ${profile.security}",
                                            "بررسی اولیه:",
                                            com.spdcon.app.config.ConfigDiagnostics.format(profile, cdnEnabled = manualCdnEnabled)
                                        ).joinToString("\n")
                                        parseResult = if (autoSaveResult.isSuccess) {
                                            "کانفیگ ساخته شد، در کتابخانه ذخیره شد و آماده کپی/اشتراک است. برای اتصال واقعی، اطلاعات همین کاربر باید روی نود سرور هم ثبت شده باشند."
                                        } else {
                                            "کانفیگ ساخته شد، اما ذخیره خودکار انجام نشد: ${autoSaveResult.exceptionOrNull()?.message}. می‌توانی بعداً از بخش ذخیره‌ها دوباره ذخیره‌اش کنی."
                                        }
                                        shareLink = link
                                        generatedOutput = link
                                        qrContent = link
                                        copyContent = link
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ساخت پروفایل دستی") }
                    }
                }
            }

            }
            if (selectedSection == "library") {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("کتابخانه محلی کانفیگ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("اطلاعات ورود با AES-GCM و کلید محلی Android Keystore رمزگذاری می‌شوند. حذف داده‌های برنامه یا کلید دستگاه می‌تواند بازیابی را ناممکن کند.")
                        Button(
                            onClick = {
                                val profile = parsedProfile
                                if (profile == null) {
                                    libraryMessage = "ابتدا یک لینک معتبر وارد و تحلیل کن."
                                } else {
                                    val result = libraryStore.save(profile)
                                    libraryMessage = result.fold(
                                        onSuccess = { "کانفیگ ذخیره شد." },
                                        onFailure = { "ذخیره ناموفق بود: ${it.message}" }
                                    )
                                    savedProfiles = libraryStore.loadAll()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = parsedProfile != null
                        ) { Text("ذخیره کانفیگ فعلی") }
                        libraryMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        OutlinedTextField(
                            value = librarySearch,
                            onValueChange = { librarySearch = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("جست‌وجو در نام، سرور، پروتکل یا برچسب") },
                            singleLine = true
                        )
                        if (savedProfiles.isEmpty()) {
                            Text("هنوز کانفیگی ذخیره نشده است.", style = MaterialTheme.typography.bodySmall)
                        } else if (visibleProfiles.isEmpty()) {
                            Text("موردی با این جست‌وجو پیدا نشد.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            visibleProfiles.forEach { saved ->
                                val tagKey = "${saved.protocol}|${saved.server}|${saved.port}|${saved.name}"
                                val tagInput = tagDrafts[tagKey] ?: saved.tags.joinToString(", ")
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text((if (saved.favorite) "⭐ " else "") + saved.name, fontWeight = FontWeight.SemiBold)
                                        Text("${saved.protocol} • ${saved.server}:${saved.port}", style = MaterialTheme.typography.bodySmall)
                                        if (saved.tags.isNotEmpty()) {
                                            Text("برچسب‌ها: ${saved.tags.joinToString("، ")}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        OutlinedTextField(
                                            value = tagInput,
                                            onValueChange = { value -> tagDrafts = tagDrafts + (tagKey to value) },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("برچسب‌ها (با ویرگول جدا کن)") },
                                            singleLine = true
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = {
                                                libraryStore.setFavorite(saved, !saved.favorite)
                                                savedProfiles = libraryStore.loadAll()
                                                libraryMessage = if (saved.favorite) "از علاقه‌مندی‌ها حذف شد." else "به علاقه‌مندی‌ها اضافه شد."
                                            }) { Text(if (saved.favorite) "حذف ⭐" else "علاقه‌مندی ⭐") }
                                            TextButton(onClick = {
                                                libraryStore.setTags(saved, tagInput.split(","))
                                                savedProfiles = libraryStore.loadAll()
                                                tagDrafts = tagDrafts - tagKey
                                                libraryMessage = "برچسب‌ها ذخیره شدند."
                                            }) { Text("ذخیره برچسب‌ها") }
                                            TextButton(onClick = {
                                                parsedProfile = saved
                                                parsedSummary = listOf(
                                                    "نام: ${saved.name}",
                                                    "پروتکل: ${saved.protocol}",
                                                    "سرور: ${saved.server}:${saved.port}",
                                                    "انتقال: ${saved.network}",
                                                    "امنیت: ${saved.security}"
                                                ).joinToString("\\n")
                                                parseResult = "کانفیگ از کتابخانه محلی بارگذاری شد."
                                                shareLink = runCatching { ConfigLinkSerializer.serialize(saved).getOrThrow() }.getOrDefault("")
                                                if (saved.security.equals("reality", true)) {
                                                    val reality = saved.toRealitySettings()
                                                    realityServerName = reality.serverName
                                                    realityPublicKey = reality.publicKey
                                                    realityShortId = reality.shortId
                                                    realityFingerprint = reality.fingerprint
                                                    realitySpiderX = reality.spiderX
                                                    realityFlow = reality.flow
                                                }
                                            }) { Text("بارگذاری") }
                                            TextButton(onClick = {
                                                libraryStore.delete(saved)
                                                savedProfiles = libraryStore.loadAll()
                                                libraryMessage = "کانفیگ حذف شد."
                                            }) { Text("حذف") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            }
            if (selectedSection == "create") {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("بهینه‌سازی شبکه (اختیاری)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("تنظیمات پیشرفته؛ اتصال واقعی نیازمند تست است.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = showAdvancedNetwork, onCheckedChange = { showAdvancedNetwork = it })
                            Text("نمایش تنظیمات پیشرفته شبکه")
                        }
                        if (showAdvancedNetwork) {
                        OutlinedButton(onClick = { presetMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("پروفایل: ${selectedPreset.titleFa}")
                        }
                        DropdownMenu(expanded = presetMenuExpanded, onDismissRequest = { presetMenuExpanded = false }) {
                            NetworkPreset.entries.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.titleFa) },
                                    onClick = {
                                        selectedPreset = preset
                                        fragmentEnabled = preset.fragment.enabled
                                        fragmentPackets = preset.fragment.packets
                                        fragmentLength = preset.fragment.length
                                        fragmentInterval = preset.fragment.interval
                                        presetMenuExpanded = false
                                    }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = fragmentEnabled, onCheckedChange = { fragmentEnabled = it })
                            Column {
                                Text("فعال‌سازی تنظیمات Fragment")
                                Text("TCP fragmentation در خروجی Freedom؛ به‌تنهایی به معنی Fragment کردن اتصال VLESS نیست.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        OutlinedTextField(
                            value = fragmentPackets,
                            onValueChange = { fragmentPackets = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("packets (tlshello یا 1-3)") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = fragmentLength,
                            onValueChange = { fragmentLength = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("length (bytes، نمونه 100-200)") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = fragmentInterval,
                            onValueChange = { fragmentInterval = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("interval (ms، نمونه 10-20)") },
                            singleLine = true
                        )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تقویت REALITY", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("این فیلدها برای ساخت خروجی VLESS + REALITY هستند. مقادیر باید با تنظیمات سرور هماهنگ باشند.")
                        OutlinedTextField(
                            value = realityServerName,
                            onValueChange = {
                                realityServerName = it
                                realityFingerprint = "chrome"
                                realitySpiderX = "/"
                                realityFlow = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("فقط SNI (مثلاً www.digikala.com)") },
                            supportingText = { Text("پیش‌فرض‌ها خودکارند؛ کلید و shortId از سرور لازم‌اند.") },
                            singleLine = true
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = showAdvancedReality, onCheckedChange = { showAdvancedReality = it })
                            Text("تنظیمات سرور REALITY")
                        }
                        if (showAdvancedReality) {
                            OutlinedTextField(value = realityPublicKey, onValueChange = { realityPublicKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("کلید عمومی سرور (pbk)") }, singleLine = true)
                            OutlinedTextField(value = realityShortId, onValueChange = { realityShortId = it }, modifier = Modifier.fillMaxWidth(), label = { Text("shortId سرور") }, singleLine = true)
                            OutlinedTextField(value = realityFingerprint, onValueChange = { realityFingerprint = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Fingerprint (پیش‌فرض chrome)") }, singleLine = true)
                            OutlinedTextField(value = realitySpiderX, onValueChange = { realitySpiderX = it }, modifier = Modifier.fillMaxWidth(), label = { Text("spiderX (پیش‌فرض /)") }, singleLine = true)
                            OutlinedTextField(value = realityFlow, onValueChange = { realityFlow = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Flow (اختیاری)") }, singleLine = true)
                        }
                        Button(
                            onClick = {
                                val profile = parsedProfile
                                if (profile == null) {
                                    generatedOutput = "ابتدا یک لینک کانفیگ را بخوان."
                                } else {
                                    val result = ConfigLinkSerializer.serialize(profile)
                                    generatedOutput = result.fold(
                                        onSuccess = {
                                            qrContent = it
                                            copyContent = it
                                            "لینک خروجی:\n$it"
                                        },
                                        onFailure = {
                                            qrContent = null
                                            copyContent = null
                                            "خطا: ${it.message}"
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = parsedProfile != null
                        ) { Text("تولید لینک خروجی") }
                        Button(
                            onClick = {
                                val profile = parsedProfile
                                if (profile == null) {
                                    generatedOutput = "ابتدا لینک یا پروفایل معتبری را بخوان."
                                } else {
                                    qrContent = null
                                    val exportProfile = if (profile.security.equals("reality", true)) {
                                        val overrides = mapOf(
                                            "sni" to realityServerName.trim(),
                                            "pbk" to realityPublicKey.trim(),
                                            "sid" to realityShortId.trim(),
                                            "fp" to realityFingerprint.trim(),
                                            "spx" to realitySpiderX.trim(),
                                            "flow" to realityFlow.trim()
                                        ).filterValues { it.isNotBlank() }
                                        profile.copy(parameters = profile.parameters + overrides)
                                    } else profile
                                    val result = com.spdcon.app.config.XrayClientConfigGenerator.generate(exportProfile)
                                    generatedOutput = result.fold(
                                        onSuccess = {
                                            copyContent = it
                                            "خروجی JSON کامل Xray برای بازبینی/ذخیره:\\n$it\\n\\nاین خروجی فایل پیکربندی است؛ سرور را خودکار تنظیم نمی‌کند و اتصال VPN را اجرا نمی‌کند."
                                        },
                                        onFailure = {
                                            copyContent = null
                                            "ساخت JSON ممکن نشد: ${it.message}"
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = parsedProfile?.let { com.spdcon.app.config.XrayClientConfigGenerator.supports(it) } == true
                        ) { Text("تولید JSON کامل Xray (VLESS / VMess / Trojan / Shadowsocks)") }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val profile = parsedProfile
                                if (profile == null) {
                                    generatedOutput = "ابتدا یک لینک VLESS + REALITY را بخوان."
                                } else {
                                    qrContent = null
                                    copyContent = null
                                    val reality = RealitySettings(
                                        serverName = realityServerName.trim(),
                                        publicKey = realityPublicKey.trim(),
                                        shortId = realityShortId.trim(),
                                        fingerprint = realityFingerprint.trim(),
                                        spiderX = realitySpiderX.trim(),
                                        flow = realityFlow.trim()
                                    )
                                    val fragment = FragmentSettings(
                                        enabled = fragmentEnabled,
                                        packets = fragmentPackets.trim(),
                                        length = fragmentLength.trim(),
                                        interval = fragmentInterval.trim()
                                    )
                                    val result = XrayConfigGenerator.generateVlessRealityOutbound(profile, reality, fragment)
                                    generatedOutput = result.fold(
                                        onSuccess = {
                                            copyContent = it
                                            "خروجی JSON برای بازبینی:\n$it\n\nتوجه: خروجی Fragment یک Freedom outbound جداگانه دارد و routing/chaining کامل هنوز تولید نمی‌شود."
                                        },
                                        onFailure = {
                                            copyContent = null
                                            "خطا: ${it.message}"
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = parsedProfile?.protocol == ConfigProtocol.VLESS && parsedProfile?.security.equals("reality", true)
                        ) { Text("تولید JSON برای Xray") }
                        generatedOutput?.let {
                            OutlinedTextField(
                                value = it,
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("خروجی") },
                                readOnly = true,
                                minLines = 4,
                                maxLines = 14
                            )
                            OutlinedButton(
                                onClick = { clipboardManager.setText(AnnotatedString(copyContent ?: it)) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("کپی خروجی") }
                            OutlinedButton(
                                onClick = {
                                    val raw = copyContent ?: qrContent
                                    if (!raw.isNullOrBlank()) {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, raw)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری کانفیگ"))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !copyContent.isNullOrBlank() || !qrContent.isNullOrBlank()
                            ) { Text("اشتراک‌گذاری لینک یا JSON") }
                        }
                        if (qrImage != null) {
                            Text("QR لینک کانفیگ (در حالت چندخروجی، لینک اول)")
                            Image(
                                bitmap = qrImage,
                                contentDescription = "QR code for configuration link",
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }

            }
            if (selectedSection == "tools") {
            item { BackhaulTunnelCard() }
            item { TelegramCloudflareProxyCard() }

            item { Text("بخش‌های برنامه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            item { FeatureCard(icon = { Icon(Icons.Default.QrCode, contentDescription = null) }, title = "کتابخانه کانفیگ", description = "ذخیره محلی، جست‌وجو، برچسب‌گذاری، QR و خروجی فایل در مراحل بعد.") }
            item { FeatureCard(icon = { Icon(Icons.Default.Dns, contentDescription = null) }, title = "نودها", description = "وضعیت و همگام‌سازی نودها؛ نیازمند بک‌اند و دسترسی مجاز.") }
            item { FeatureCard(icon = { Icon(Icons.Default.Group, contentDescription = null) }, title = "کاربران و اشتراک‌ها", description = "مدیریت کاربر، محدودیت، انقضا و لینک اشتراک در مراحل بعد.") }
            item { FeatureCard(icon = { Icon(Icons.Default.Security, contentDescription = null) }, title = "Scanner و Checker", description = "بررسی ساختاری و تست‌های کنترل‌شده برای نودهای مجاز؛ در حال توسعه.") }
            item { Text("نسخه 0.2.0 • موتور کانفیگ در حال تکمیل", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

private fun createQrBitmap(content: String): Bitmap? = runCatching {
    require(content.length <= 8_000) { "QR content is too long" }
    val size = 640
    val matrix = MultiFormatWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
    )
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until size) {
            for (y in 0 until size) {
                setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
}.getOrNull()

@Composable
private fun FeatureCard(icon: @Composable () -> Unit, title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            icon()
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
