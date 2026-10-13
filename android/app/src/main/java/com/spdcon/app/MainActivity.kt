package com.spdcon.app

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
import com.spdcon.app.config.ConfigLibraryStore
import com.spdcon.app.config.ConfigLinkParser
import com.spdcon.app.config.ConfigLinkSerializer
import com.spdcon.app.config.ConfigProfile
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
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpdConDashboard()
                }
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
    var libraryMessage by remember { mutableStateOf<String?>(null) }
    var manualProtocol by remember { mutableStateOf(ConfigProtocol.VLESS) }
    var manualProtocolMenuExpanded by remember { mutableStateOf(false) }
    var manualName by remember { mutableStateOf("کانفیگ جدید") }
    var manualServer by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("443") }
    var manualCredential by remember { mutableStateOf("") }
    var manualNetwork by remember { mutableStateOf("tcp") }
    var manualSecurity by remember { mutableStateOf("none") }
    var manualMethod by remember { mutableStateOf("chacha20-ietf-poly1305") }
    var shareLink by remember { mutableStateOf("") }
    var parseResult by remember { mutableStateOf<String?>(null) }
    var parsedSummary by remember { mutableStateOf<String?>(null) }
    var parsedProfile by remember { mutableStateOf<ConfigProfile?>(null) }
    var generatedOutput by remember { mutableStateOf<String?>(null) }
    var qrContent by remember { mutableStateOf<String?>(null) }
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("موتور ساخت کانفیگ", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(6.dp))
                        Text("لینک VLESS، VMess، Trojan یا Shadowsocks را وارد کن تا اطلاعاتش خوانده و بررسی شود.")
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
                                        "امنیت: ${profile.security}"
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

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ساخت دستی کانفیگ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("بدون لینک آماده هم می‌توانی یک پروفایل بسازی. برای REALITY، فیلدهای تکمیلی را در بخش پایین وارد کن.")
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
                        OutlinedTextField(value = manualName, onValueChange = { manualName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("نام کانفیگ") }, singleLine = true)
                        OutlinedTextField(value = manualServer, onValueChange = { manualServer = it }, modifier = Modifier.fillMaxWidth(), label = { Text("دامنه یا IP سرور") }, singleLine = true)
                        OutlinedTextField(value = manualPort, onValueChange = { manualPort = it }, modifier = Modifier.fillMaxWidth(), label = { Text("پورت") }, singleLine = true)
                        OutlinedTextField(
                            value = manualCredential,
                            onValueChange = { manualCredential = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (manualProtocol == ConfigProtocol.SHADOWSOCKS) "رمز عبور" else if (manualProtocol == ConfigProtocol.TROJAN) "رمز Trojan" else "UUID / شناسه") },
                            singleLine = true
                        )
                        if (manualProtocol == ConfigProtocol.SHADOWSOCKS) {
                            OutlinedTextField(value = manualMethod, onValueChange = { manualMethod = it }, modifier = Modifier.fillMaxWidth(), label = { Text("روش رمزنگاری Shadowsocks") }, singleLine = true)
                        }
                        OutlinedTextField(value = manualNetwork, onValueChange = { manualNetwork = it }, modifier = Modifier.fillMaxWidth(), label = { Text("نوع انتقال (tcp/ws/grpc/xhttp)") }, singleLine = true)
                        OutlinedTextField(value = manualSecurity, onValueChange = { manualSecurity = it }, modifier = Modifier.fillMaxWidth(), label = { Text("امنیت (none/tls/reality)") }, singleLine = true)
                        Button(
                            onClick = {
                                val parameters = buildMap {
                                    if (manualProtocol == ConfigProtocol.SHADOWSOCKS) put("method", manualMethod.trim())
                                    if (manualSecurity.equals("reality", true)) {
                                        put("sni", realityServerName.trim())
                                        put("pbk", realityPublicKey.trim())
                                        put("sid", realityShortId.trim())
                                        put("fp", realityFingerprint.trim())
                                        put("spx", realitySpiderX.trim())
                                        put("flow", realityFlow.trim())
                                    }
                                }.filterValues { it.isNotBlank() }
                                val profile = ConfigProfile(
                                    name = manualName.trim(),
                                    protocol = manualProtocol,
                                    server = manualServer.trim(),
                                    port = manualPort.toIntOrNull() ?: -1,
                                    credential = manualCredential.trim(),
                                    network = manualNetwork.trim(),
                                    security = manualSecurity.trim().lowercase(),
                                    remark = manualName.trim(),
                                    parameters = parameters
                                )
                                val errors = profile.validate()
                                if (errors.isNotEmpty()) {
                                    parseResult = errors.joinToString("; ")
                                } else {
                                    parsedProfile = profile
                                    parsedSummary = listOf(
                                        "نام: ${profile.name}",
                                        "پروتکل: ${profile.protocol}",
                                        "سرور: ${profile.server}:${profile.port}",
                                        "انتقال: ${profile.network}",
                                        "امنیت: ${profile.security}"
                                    ).joinToString("\\n")
                                    parseResult = "پروفایل دستی ساخته شد؛ اتصال واقعی هنوز آزمایش نشده است."
                                    shareLink = ConfigLinkSerializer.serialize(profile).getOrDefault("")
                                    generatedOutput = null
                                    qrContent = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ساخت پروفایل دستی") }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("کتابخانه محلی کانفیگ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("کانفیگ‌ها روی همین دستگاه ذخیره می‌شوند. این نسخه هنوز رمزگذاری مخصوص خزانهٔ امن ندارد؛ از دستگاه امن استفاده کن.")
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
                        if (savedProfiles.isEmpty()) {
                            Text("هنوز کانفیگی ذخیره نشده است.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            savedProfiles.forEach { saved ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(saved.name, fontWeight = FontWeight.SemiBold)
                                        Text("${saved.protocol} • ${saved.server}:${saved.port}", style = MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Fragment و پروفایل شبکه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("پروفایل‌ها فقط نقطه شروع آزمایشی‌اند؛ نتیجه به اپراتور، منطقه، APN و نسخه Xray بستگی دارد و دور زدن محدودیت تضمین نمی‌شود.")
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

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تقویت REALITY", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("این فیلدها برای ساخت خروجی VLESS + REALITY هستند. مقادیر باید با تنظیمات سرور هماهنگ باشند.")
                        OutlinedTextField(value = realityServerName, onValueChange = { realityServerName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("SNI / serverName") }, singleLine = true)
                        OutlinedTextField(value = realityPublicKey, onValueChange = { realityPublicKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("کلید عمومی REALITY (pbk)") }, singleLine = true)
                        OutlinedTextField(value = realityShortId, onValueChange = { realityShortId = it }, modifier = Modifier.fillMaxWidth(), label = { Text("shortId (hex، حداکثر 16 رقم)") }, singleLine = true)
                        OutlinedTextField(value = realityFingerprint, onValueChange = { realityFingerprint = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Fingerprint") }, singleLine = true)
                        OutlinedTextField(value = realitySpiderX, onValueChange = { realitySpiderX = it }, modifier = Modifier.fillMaxWidth(), label = { Text("spiderX") }, singleLine = true)
                        OutlinedTextField(value = realityFlow, onValueChange = { realityFlow = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Flow (اختیاری: xtls-rprx-vision)") }, singleLine = true)
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
                                            "لینک خروجی:\n$it"
                                        },
                                        onFailure = {
                                            qrContent = null
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
                                    generatedOutput = "ابتدا یک لینک VLESS + REALITY را بخوان."
                                } else {
                                    qrContent = null
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
                                        onSuccess = { "خروجی JSON برای بازبینی:\n$it\n\nتوجه: خروجی Fragment یک Freedom outbound جداگانه دارد و routing/chaining کامل هنوز تولید نمی‌شود." },
                                        onFailure = { "خطا: ${it.message}" }
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
                                onClick = { clipboardManager.setText(AnnotatedString(it)) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("کپی خروجی") }
                        }
                        if (qrImage != null) {
                            Text("QR لینک کانفیگ")
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

            item { Text("بخش‌های برنامه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            item { FeatureCard(icon = { Icon(Icons.Default.QrCode, contentDescription = null) }, title = "کتابخانه کانفیگ", description = "ذخیره محلی، جست‌وجو، برچسب‌گذاری، QR و خروجی فایل در مراحل بعد.") }
            item { FeatureCard(icon = { Icon(Icons.Default.Dns, contentDescription = null) }, title = "نودها", description = "وضعیت و همگام‌سازی نودها؛ نیازمند بک‌اند و دسترسی مجاز.") }
            item { FeatureCard(icon = { Icon(Icons.Default.Group, contentDescription = null) }, title = "کاربران و اشتراک‌ها", description = "مدیریت کاربر، محدودیت، انقضا و لینک اشتراک در مراحل بعد.") }
            item { FeatureCard(icon = { Icon(Icons.Default.Security, contentDescription = null) }, title = "Scanner و Checker", description = "بررسی ساختاری و تست‌های کنترل‌شده برای نودهای مجاز؛ در حال توسعه.") }
            item { Text("نسخه 0.2.0 • موتور کانفیگ در حال تکمیل", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp)) }
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
