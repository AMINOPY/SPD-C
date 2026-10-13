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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
                        Text("ساخت، سازمان‌دهی، بررسی", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(6.dp))
                        Text("نسخه آغازین: وارد کردن لینک اشتراک کانفیگ و اعتبارسنجی اولیه.")
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = shareLink,
                            onValueChange = {
                                shareLink = it
                                parseResult = null
                                parsedSummary = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("لینک VLESS / VMess / Trojan") },
                            placeholder = { Text("vless://…") },
                            minLines = 2,
                            maxLines = 4
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val result = ConfigLinkParser.parse(shareLink)
                                if (result.isSuccess) {
                                    val profile = result.getOrThrow()
                                    parseResult = "کانفیگ با موفقیت خوانده شد. این فقط اعتبارسنجی ساختاری است؛ اتصال را تأیید نمی‌کند."
                                    parsedSummary = listOf(
                                        "نام: ${profile.name}",
                                        "پروتکل: ${profile.protocol}",
                                        "سرور: ${profile.server}:${profile.port}",
                                        "انتقال: ${profile.network}",
                                        "امنیت: ${profile.security}"
                                    ).joinToString("\n")
                                } else {
                                    parseResult = result.exceptionOrNull()?.message ?: "لینک قابل خواندن نیست."
                                    parsedSummary = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("  خواندن و اعتبارسنجی لینک")
                        }
                        parseResult?.let { message ->
                            Spacer(Modifier.height(10.dp))
                            Text(message, style = MaterialTheme.typography.bodyMedium)
                        }
                        parsedSummary?.let { summary ->
                            Spacer(Modifier.height(8.dp))
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(summary, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            item {
                Text("بخش‌های برنامه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            item {
                FeatureCard(
                    icon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                    title = "کتابخانه کانفیگ",
                    description = "ذخیره محلی، جست‌وجو، برچسب‌گذاری، خروجی و QR در مراحل بعد."
                )
            }
            item {
                FeatureCard(
                    icon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    title = "نودها",
                    description = "وضعیت، سلامت و همگام‌سازی نودها؛ نیازمند بک‌اند و دسترسی مجاز."
                )
            }
            item {
                FeatureCard(
                    icon = { Icon(Icons.Default.Group, contentDescription = null) },
                    title = "کاربران و اشتراک‌ها",
                    description = "مدیریت کاربر، محدودیت، انقضا و لینک اشتراک در مراحل بعد."
                )
            }
            item {
                FeatureCard(
                    icon = { Icon(Icons.Default.Security, contentDescription = null) },
                    title = "Scanner و Checker",
                    description = "بررسی ساختاری و تست‌های کنترل‌شده برای نودهای مجاز؛ در حال توسعه."
                )
            }
            item {
                Text("نسخه 0.1.0 • توسعه مرحله‌ای", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            }
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
