package com.spdcon.app.ui

import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

enum class AppLanguage(val code: String, val nativeLabel: String) {
    ENGLISH("en", "English"),
    PERSIAN("fa", "فارسی"),
    ARABIC("ar", "العربية");

    val layoutDirection: LayoutDirection
        get() = if (this == ENGLISH) LayoutDirection.Ltr else LayoutDirection.Rtl

    companion object {
        fun fromCode(code: String): AppLanguage = entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.ENGLISH }
val LocalSetAppLanguage = compositionLocalOf<(AppLanguage) -> Unit> { {} }

@Composable
fun tr(english: String): String {
    return when (LocalAppLanguage.current) {
        AppLanguage.ENGLISH -> english
        AppLanguage.PERSIAN -> persian[english] ?: english
        AppLanguage.ARABIC -> arabic[english] ?: english
    }
}

private val persian = mapOf(
    "Home" to "خانه", "Library" to "کتابخانه", "Create" to "ساخت", "Tools" to "ابزارها", "More" to "بیشتر",
    "Secure • Private • Connected" to "امن • خصوصی • متصل", "VPN off" to "وی‌پی‌ان خاموش",
    "Configuration ready" to "کانفیگ آماده است", "Saved locally. Scan the QR code or copy the link into your VPN client:" to "به‌صورت محلی ذخیره شد. QR را اسکن یا لینک را در برنامهٔ VPN کپی کنید:",
    "Copy link" to "کپی لینک", "Close" to "بستن", "Configuration details" to "جزئیات کانفیگ",
    "VPN not connected" to "وی‌پی‌ان متصل نیست", "Your configurations stay on this device until you export or sync them." to "کانفیگ‌ها تا زمان خروجی گرفتن یا همگام‌سازی روی همین دستگاه می‌مانند.",
    "Quick Actions" to "دسترسی سریع", "New Config" to "کانفیگ جدید", "Library" to "کتابخانه", "QR / Import" to "QR / واردکردن", "Diagnostics" to "عیب‌یابی",
    "Overview" to "نمای کلی", "Configs" to "کانفیگ‌ها", "Favorites" to "علاقه‌مندی‌ها", "Sync" to "همگام‌سازی",
    "Create Config" to "ساخت کانفیگ", "Build a valid share link and save it to your local library." to "لینک اشتراک معتبر بسازید و آن را در کتابخانهٔ محلی ذخیره کنید.",
    "Choose protocol" to "انتخاب پروتکل", "Advanced utilities and server-side helpers" to "ابزارهای پیشرفته و امکانات سمت سرور",
    "Backhaul Generator" to "سازندهٔ Backhaul", "Build tunnel server / client configuration" to "ساخت تنظیمات سرور و کلاینت تونل",
    "REALITY & node settings" to "تنظیمات REALITY و نود", "Open the full configuration panel" to "بازکردن پنل کامل تنظیمات",
    "Cloudflare / Telegram proxy" to "پروکسی Cloudflare / تلگرام", "Open existing proxy tools" to "بازکردن ابزارهای پروکسی",
    "QR import & diagnostics" to "ورود QR و عیب‌یابی", "Use the full import and diagnostic panel" to "استفاده از پنل کامل ورود و عیب‌یابی",
    "Settings and advanced features" to "تنظیمات و قابلیت‌های پیشرفته", "Local-first storage" to "ذخیره‌سازی محلی",
    "Credentials are stored by the existing encrypted local library. Cloud sync remains optional." to "اطلاعات ورود در کتابخانهٔ محلی رمزگذاری‌شده ذخیره می‌شود. همگام‌سازی ابری اختیاری است.",
    "Language" to "زبان", "Choose the app language. Persian and Arabic use right-to-left layout." to "زبان برنامه را انتخاب کنید. فارسی و عربی با چینش راست‌به‌چپ نمایش داده می‌شوند.",
    "Appearance" to "ظاهر", "SPD-C Neon Glass • dark navy • cyan glow" to "SPD-C نئون گلس • سرمه‌ای تیره • درخشش فیروزه‌ای",
    "Advanced / legacy panel" to "پنل پیشرفته / قدیمی", "Nodes, Cloud sync, Backhaul, Telegram proxy and extended settings" to "نودها، همگام‌سازی ابری، Backhaul، پروکسی تلگرام و تنظیمات بیشتر"
)

private val arabic = mapOf(
    "Home" to "الرئيسية", "Library" to "المكتبة", "Create" to "إنشاء", "Tools" to "الأدوات", "More" to "المزيد",
    "Secure • Private • Connected" to "آمن • خاص • متصل", "VPN off" to "VPN متوقف",
    "Configuration ready" to "الإعداد جاهز", "Saved locally. Scan the QR code or copy the link into your VPN client:" to "تم الحفظ محليًا. امسح رمز QR أو انسخ الرابط إلى تطبيق VPN:",
    "Copy link" to "نسخ الرابط", "Close" to "إغلاق", "Configuration details" to "تفاصيل الإعداد",
    "VPN not connected" to "VPN غير متصل", "Your configurations stay on this device until you export or sync them." to "تبقى إعداداتك على هذا الجهاز حتى تصديرها أو مزامنتها.",
    "Quick Actions" to "إجراءات سريعة", "New Config" to "إعداد جديد", "Library" to "المكتبة", "QR / Import" to "QR / استيراد", "Diagnostics" to "التشخيص",
    "Overview" to "نظرة عامة", "Configs" to "الإعدادات", "Favorites" to "المفضلة", "Sync" to "المزامنة",
    "Create Config" to "إنشاء إعداد", "Build a valid share link and save it to your local library." to "أنشئ رابط مشاركة صالحًا واحفظه في المكتبة المحلية.",
    "Choose protocol" to "اختر البروتوكول", "Advanced utilities and server-side helpers" to "أدوات متقدمة ومساعدات الخادم",
    "Backhaul Generator" to "منشئ Backhaul", "Build tunnel server / client configuration" to "إنشاء إعدادات خادم وعميل النفق",
    "REALITY & node settings" to "إعدادات REALITY والعقدة", "Open the full configuration panel" to "فتح لوحة الإعدادات الكاملة",
    "Cloudflare / Telegram proxy" to "وكيل Cloudflare / Telegram", "Open existing proxy tools" to "فتح أدوات الوكيل",
    "QR import & diagnostics" to "استيراد QR والتشخيص", "Use the full import and diagnostic panel" to "استخدم لوحة الاستيراد والتشخيص الكاملة",
    "Settings and advanced features" to "الإعدادات والميزات المتقدمة", "Local-first storage" to "التخزين المحلي أولاً",
    "Credentials are stored by the existing encrypted local library. Cloud sync remains optional." to "تُخزّن بيانات الاعتماد في المكتبة المحلية المشفرة. تظل المزامنة السحابية اختيارية.",
    "Language" to "اللغة", "Choose the app language. Persian and Arabic use right-to-left layout." to "اختر لغة التطبيق. تستخدم الفارسية والعربية تخطيطًا من اليمين إلى اليسار.",
    "Appearance" to "المظهر", "SPD-C Neon Glass • dark navy • cyan glow" to "SPD-C نيون زجاجي • كحلي داكن • توهج سماوي",
    "Advanced / legacy panel" to "اللوحة المتقدمة / القديمة", "Nodes, Cloud sync, Backhaul, Telegram proxy and extended settings" to "العقد ومزامنة السحابة وBackhaul ووكيل Telegram والإعدادات الموسعة"
)
