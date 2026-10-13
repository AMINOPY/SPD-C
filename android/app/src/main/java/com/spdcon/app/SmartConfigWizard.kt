package com.spdcon.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class SmartNetworkContext(val title: String) {
    AUTO("نوع شبکه نامشخص"),
    CELLULAR("داده همراه (اپراتور نامشخص)"),
    MCI("همراه اول"),
    IRANCELL("ایرانسل"),
    NATIONAL("اینترنت ملی / محدود"),
    WIFI("وای‌فای"),
    DATACENTER("سرور به سرور / دیتاسنتر"),
    BACKHAUL("تونل Backhaul ایران ↔ خارج")
}

enum class SmartGoal(val title: String) {
    BALANCED("متعادل و سازگار"),
    RESTRICTED("شبکه محدود / اتصال ناپایدار"),
    CLOUDFLARE("دامنه پشت Cloudflare"),
    REALITY("سرور از قبل با REALITY تنظیم شده")
}

data class SmartConfigChoice(
    val context: SmartNetworkContext,
    val goal: SmartGoal,
    val protocol: String,
    val transport: String,
    val security: String,
    val port: String,
    val alpn: String,
    val explanation: String,
    val warning: String
)

object SmartConfigAdvisor {
    fun recommend(context: SmartNetworkContext, goal: SmartGoal): SmartConfigChoice {
        val transport: String
        val security: String
        val port: String
        val alpn: String
        val explanation: String
        val warning: String

        when (goal) {
            SmartGoal.CLOUDFLARE -> {
                transport = "ws"
                security = "tls"
                port = "443"
                alpn = "h2,http/1.1"
                explanation = "پروفایل پیشنهادی WS + TLS برای دامنه‌ای که واقعاً روی Cloudflare پروکسی شده است."
                warning = "این انتخاب فقط قالب را تنظیم می‌کند؛ باید پورت مجاز، DNS پروکسی‌شده و سرویس مبدأ با هم سازگار باشند."
            }
            SmartGoal.REALITY -> {
                transport = "tcp"
                security = "reality"
                port = "443"
                alpn = "h2,http/1.1"
                explanation = "قالب VLESS + REALITY برای سروری که از قبل REALITY دارد."
                warning = "SNI به‌تنهایی کافی نیست؛ pbk و shortId باید از همان سرور گرفته شوند. این برنامه آن‌ها را حدس نمی‌زند."
            }
            SmartGoal.RESTRICTED -> {
                transport = if (context == SmartNetworkContext.DATACENTER) "tcp" else "ws"
                security = "tls"
                port = "443"
                alpn = "h2,http/1.1"
                explanation = "پروفایل محافظه‌کارانه TLS برای آزمایش سازگاری در شبکه محدود."
                warning = "هیچ پروتکلی عبور از DPI یا اتصال موفق را تضمین نمی‌کند؛ نتیجه به سرور، شبکه و سیاست فیلترینگ وابسته است."
            }
            SmartGoal.BALANCED -> {
                transport = if (context == SmartNetworkContext.DATACENTER) "tcp" else "ws"
                security = "tls"
                port = "443"
                alpn = "h2,http/1.1"
                explanation = "شروع ساده با TLS و پورت رایج HTTPS؛ در صورت داشتن سرور با تنظیمات متفاوت باید با همان هماهنگ شود."
                warning = "این یک پیش‌فرض پیشنهادی است، نه تشخیص زنده DPI یا تست اتصال."
            }
        }

        val contextNote = when (context) {
            SmartNetworkContext.AUTO -> "نوع شبکه مشخص نشد؛ تنظیمات پیشنهادی عمومی هستند."
            SmartNetworkContext.CELLULAR -> "داده همراه شناسایی شد؛ اپراتور و کیفیت مسیر از نوع اتصال به‌تنهایی قابل تشخیص قطعی نیست."
            SmartNetworkContext.MCI -> "پروفایل همراه اول یک راهنمای قابل‌ویرایش است، نه نتیجه تست زنده اپراتور."
            SmartNetworkContext.IRANCELL -> "پروفایل ایرانسل یک راهنمای قابل‌ویرایش است، نه نتیجه تست زنده اپراتور."
            SmartNetworkContext.NATIONAL -> "برای اینترنت ملی/محدود، فقط گزینه‌های محلی و سرورهای در دسترس همان شبکه را می‌توان ارزیابی کرد."
            SmartNetworkContext.WIFI -> "وای‌فای به‌تنهایی مشخص نمی‌کند ISP یا محدودیت‌های مسیر چیست."
            SmartNetworkContext.DATACENTER -> "انتخاب دیتاسنتر به نود واقعی و تست DNS/TLS/پورت نیاز دارد؛ نام کشور به‌تنهایی کافی نیست."
            SmartNetworkContext.BACKHAUL -> "برای ترافیک ایران ↔ خارج، اول تونل Backhaul را جداگانه پیکربندی و از دو سمت آزمایش کن."
        }
        return SmartConfigChoice(
            context = context,
            goal = goal,
            protocol = "VLESS",
            transport = transport,
            security = security,
            port = port,
            alpn = alpn,
            explanation = explanation,
            warning = "$contextNote $warning"
        )
    }
}

@Composable
fun detectSmartNetworkContext(context: Context): SmartNetworkContext {
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return SmartNetworkContext.AUTO
    val network = connectivity.activeNetwork ?: return SmartNetworkContext.AUTO
    val capabilities = connectivity.getNetworkCapabilities(network) ?: return SmartNetworkContext.AUTO
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> SmartNetworkContext.WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> SmartNetworkContext.CELLULAR
        else -> SmartNetworkContext.AUTO
    }
}

@Composable
fun SmartConfigWizard(onApply: (SmartConfigChoice) -> Unit) {
    val appContext = LocalContext.current
    var context by remember(appContext) { mutableStateOf(detectSmartNetworkContext(appContext)) }
    var goal by remember { mutableStateOf(SmartGoal.BALANCED) }
    var contextMenu by remember { mutableStateOf(false) }
    var goalMenu by remember { mutableStateOf(false) }
    val choice = SmartConfigAdvisor.recommend(context, goal)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ساخت هوشمند کانفیگ", style = MaterialTheme.typography.titleLarge)
            Text("نوع اتصال Wi‑Fi یا داده همراه به‌صورت محلی تشخیص داده می‌شود؛ اپراتور، DPI و سلامت نود بدون تست واقعی حدس زده نمی‌شوند. دو انتخاب کوتاه برای پیشنهاد اولیه کافی است.")
            OutlinedButton(onClick = { contextMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text("شبکه: ${context.title}")
            }
            DropdownMenu(expanded = contextMenu, onDismissRequest = { contextMenu = false }) {
                SmartNetworkContext.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.title) }, onClick = { context = option; contextMenu = false })
                }
            }
            OutlinedButton(onClick = { goalMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text("هدف: ${goal.title}")
            }
            DropdownMenu(expanded = goalMenu, onDismissRequest = { goalMenu = false }) {
                SmartGoal.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.title) }, onClick = { goal = option; goalMenu = false })
                }
            }
            Text(choice.explanation, style = MaterialTheme.typography.bodyMedium)
            Text("پیشنهاد: VLESS • ${choice.transport.uppercase()} • ${choice.security.uppercase()} • پورت ${choice.port}", style = MaterialTheme.typography.bodyMedium)
            Text(choice.warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onApply(choice) }, modifier = Modifier.fillMaxWidth()) {
                Text("اعمال پیشنهاد در فرم ساخت")
            }
        }
    }
}
