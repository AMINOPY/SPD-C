package com.spdcon.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun TelegramCloudflareProxyCard() {
    val context = LocalContext.current
    var workerUrl by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("پراکسی وب تلگرام با Cloudflare", style = MaterialTheme.typography.titleLarge)
            Text("این مسیر از TWP-CF الهام می‌گیرد. ابزار اصلی، Worker را در حساب Cloudflare مستقر/مدیریت می‌کند؛ این بخش فعلاً توکن API را دریافت یا ذخیره نمی‌کند.")
            OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ArasTey/TWP-CF")))
            }, modifier = Modifier.fillMaxWidth()) { Text("بررسی سورس TWP-CF") }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://deployer.imarastey.workers.dev/")))
            }, modifier = Modifier.fillMaxWidth()) { Text("بازکردن ابزار استقرار TWP-CF") }
            OutlinedTextField(
                value = workerUrl,
                onValueChange = { workerUrl = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("آدرس Worker مستقرشده") },
                placeholder = { Text("https://your-worker.workers.dev") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val uri = runCatching { Uri.parse(workerUrl) }.getOrNull()
                    message = when {
                        uri == null || uri.scheme != "https" || uri.host.isNullOrBlank() -> "آدرس معتبر HTTPS وارد کن."
                        else -> "قالب آدرس درست است؛ این بررسی، دسترسی یا کارکرد واقعی پراکسی را تأیید نمی‌کند."
                    }
                }, modifier = Modifier.weight(1f)) { Text("بررسی قالب آدرس") }
                OutlinedButton(
                    enabled = workerUrl.startsWith("https://") && Uri.parse(workerUrl).host != null,
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(workerUrl))) },
                    modifier = Modifier.weight(1f)
                ) { Text("بازکردن Worker") }
            }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
            Text("امنیت: مخزن TWP-CF در متادیتای GitHub مجوز SPDX مشخصی نشان نمی‌دهد؛ فعلاً فقط لینک و راهنمای استفاده ارائه می‌شود و کد آن کپی نشده است. برای امنیت بیشتر، سورس را بررسی کن و ترجیحاً Worker را در حساب خودت مستقر کن.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
