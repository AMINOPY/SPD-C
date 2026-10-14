package com.spdcon.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.spdcon.app.config.BackhaulConfigGenerator
import com.spdcon.app.config.BackhaulRole
import com.spdcon.app.config.BackhaulSettings

@Composable
fun BackhaulTunnelCard() {
    val clipboard = LocalClipboardManager.current
    var role by remember { mutableStateOf(BackhaulRole.SERVER) }
    var roleMenu by remember { mutableStateOf(false) }
    var transport by remember { mutableStateOf("wsmux") }
    var transportMenu by remember { mutableStateOf(false) }
    var bindPort by remember { mutableStateOf("8080") }
    var remoteHost by remember { mutableStateOf("") }
    var remotePort by remember { mutableStateOf("8080") }
    var token by remember { mutableStateOf("") }
    var ports by remember { mutableStateOf("443") }
    var cloudflareProxy by remember { mutableStateOf(true) }
    var keepalive by remember { mutableStateOf("75") }
    var heartbeat by remember { mutableStateOf("40") }
    var muxConnections by remember { mutableStateOf("8") }
    var output by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("تونل Backhaul ایران ↔ خارج", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("برای هر دو سرور فایل TOML جدا بساز. این بخش تنظیمات را تولید می‌کند؛ نصب یا اجرای Backhaul از داخل اندروید انجام نمی‌شود.")
            OutlinedButton(onClick = { roleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (role == BackhaulRole.SERVER) "نقش: سرور ایران (Server)" else "نقش: کلاینت خارج (Client)")
            }
            DropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }) {
                DropdownMenuItem(text = { Text("سرور ایران") }, onClick = { role = BackhaulRole.SERVER; roleMenu = false })
                DropdownMenuItem(text = { Text("کلاینت خارج") }, onClick = { role = BackhaulRole.CLIENT; roleMenu = false })
            }
            OutlinedButton(onClick = { transportMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text("ترنسپورت: $transport")
            }
            DropdownMenu(expanded = transportMenu, onDismissRequest = { transportMenu = false }) {
                listOf("ws", "wsmux", "wss", "wssmux").forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { transport = option; transportMenu = false })
                }
            }
            if (role == BackhaulRole.SERVER) {
                OutlinedTextField(value = bindPort, onValueChange = { bindPort = it.filter(Char::isDigit).take(5) }, modifier = Modifier.fillMaxWidth(), label = { Text("پورت شنود سرور (مثلاً 8080)") }, singleLine = true)
                OutlinedTextField(value = ports, onValueChange = { ports = it }, modifier = Modifier.fillMaxWidth(), label = { Text("پورت‌های مقصد (با کاما جدا کن؛ مثلاً 443,8443)") }, supportingText = { Text("قالب‌های پیشرفته بازه/مقصد را بعداً اضافه می‌کنیم؛ فعلاً پورت‌های عددی.") }, singleLine = true)
            } else {
                OutlinedTextField(value = remoteHost, onValueChange = { remoteHost = it.trim() }, modifier = Modifier.fillMaxWidth(), label = { Text("دامنه سمت ایران (مثلاً tunnel.example.com)") }, singleLine = true)
                OutlinedTextField(value = remotePort, onValueChange = { remotePort = it.filter(Char::isDigit).take(5) }, modifier = Modifier.fillMaxWidth(), label = { Text("پورت تونل") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = cloudflareProxy, onCheckedChange = { cloudflareProxy = it })
                    Text("دامنه پشت پروکسی Cloudflare (ابر نارنجی)")
                }
            }
            OutlinedTextField(value = token, onValueChange = { token = it }, modifier = Modifier.fillMaxWidth(), label = { Text("توکن مشترک تونل (محرمانه)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            OutlinedTextField(value = keepalive, onValueChange = { keepalive = it.filter(Char::isDigit).take(4) }, modifier = Modifier.fillMaxWidth(), label = { Text("Keepalive (ثانیه)") }, singleLine = true)
            if (transport.contains("mux")) {
                OutlinedTextField(value = heartbeat, onValueChange = { heartbeat = it.filter(Char::isDigit).take(4) }, modifier = Modifier.fillMaxWidth(), label = { Text("Heartbeat (ثانیه)") }, singleLine = true)
                OutlinedTextField(value = muxConnections, onValueChange = { muxConnections = it.filter(Char::isDigit).take(3) }, modifier = Modifier.fillMaxWidth(), label = { Text(if (role == BackhaulRole.SERVER) "تعداد mux connections" else "Connection pool") }, singleLine = true)
            }
            Text("امنیت: توکن را در کانفیگ‌های عمومی یا اسکرین‌شات منتشر نکن. ترنسپورت‌های موجود در این فرم مطابق فهرست نسخه رسمی بررسی‌شده‌اند؛ uwsmux در این نسخه به‌عنوان گزینه تأییدنشده نمایش داده نمی‌شود.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                val settings = BackhaulSettings(
                    role = role,
                    transport = transport,
                    bindPort = bindPort.toIntOrNull(),
                    remoteHost = remoteHost,
                    remotePort = remotePort.toIntOrNull(),
                    token = token,
                    ports = ports.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    keepalivePeriod = keepalive.toIntOrNull(),
                    heartbeat = heartbeat.toIntOrNull(),
                    muxConnections = muxConnections.toIntOrNull(),
                    cloudflareProxy = cloudflareProxy
                )
                val result = BackhaulConfigGenerator.generate(settings)
                output = result.fold({ message = "فایل تنظیمات تولید شد؛ نسخه Backhaul سرور را پیش از اجرا بررسی کن."; it }, { message = it.message ?: "تنظیمات معتبر نیست."; "" })
            }, modifier = Modifier.fillMaxWidth()) { Text("ساخت فایل TOML") }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
            if (output.isNotBlank()) {
                OutlinedTextField(value = output, onValueChange = {}, modifier = Modifier.fillMaxWidth(), label = { Text("خروجی TOML") }, readOnly = true, minLines = 8, maxLines = 24)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(output)); message = "خروجی کپی شد؛ توکن داخل آن محرمانه است." }, modifier = Modifier.weight(1f)) { Text("کپی تنظیمات") }
                    OutlinedButton(onClick = { output = ""; message = "خروجی از صفحه پاک شد." }, modifier = Modifier.weight(1f)) { Text("پاک‌کردن") }
                }
            }
        }
    }
}
