package com.spdcon.app

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spdcon.app.config.ConfigLinkParser
import com.spdcon.app.config.ConfigLinkSerializer
import com.spdcon.app.config.ConfigProfile
import com.spdcon.app.config.ConfigProtocol
import com.spdcon.app.config.FragmentSettings
import com.spdcon.app.config.NetworkPreset
import com.spdcon.app.config.RealitySettings
import com.spdcon.app.config.XrayConfigGenerator
import com.spdcon.app.config.toRealitySettings

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
    var shareLink by remember { mutableStateOf("") }
    var parseResult by remember { mutableStateOf<String?>(null) }
    var parsedSummary by remember { mutableStateOf<String?>(null) }
    var parsedProfile by remember { mutableStateOf<ConfigProfile?>(null) }
    var generatedOutput by remember { mutableStateOf<String?>(null) }
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
                                        onSuccess = { "لینک خروجی:\n$it" },
                                        onFailure = { "خطا: ${it.message}" }
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
