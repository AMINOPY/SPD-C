package com.spdcon.app.config

enum class DpiEvidenceStatus {
    TUNNEL_WORKS_ON_CURRENT_NETWORK,
    POSSIBLE_NETWORK_SPECIFIC_INTERFERENCE,
    CONFIGURATION_OR_SERVER_ISSUE_POSSIBLE,
    NODE_OR_DNS_UNREACHABLE,
    UNKNOWN
}

data class DpiEvidenceAssessment(
    val status: DpiEvidenceStatus,
    val titleFa: String,
    val explanationFa: String,
    val nextStepFa: String,
    val confirmedDpi: Boolean = false
)

/**
 * DPI is not directly detectable from a single failed connection. A network-specific
 * interference label is only a hypothesis when the same profile succeeds on another
 * network and fails here; even that is not proof of DPI.
 */
object DpiResistanceAssessor {
    fun assess(
        current: ConnectionDiagnosticReport,
        comparisonOnOtherNetwork: ConnectionDiagnosticReport? = null
    ): DpiEvidenceAssessment {
        if (current.tunnelConnected) {
            return DpiEvidenceAssessment(
                DpiEvidenceStatus.TUNNEL_WORKS_ON_CURRENT_NETWORK,
                "تونل روی شبکهٔ فعلی کار می‌کند",
                "در این تست، درخواست واقعی از مسیر پروفایل عبور کرده است. این نتیجه مقاومت در برابر DPI را در شبکه‌های دیگر یا در آینده اثبات نمی‌کند.",
                "اگر هدفت مقایسهٔ محدودیت شبکه است، همین پروفایل را روی شبکهٔ دیگری هم با شرایط مشابه آزمایش کن."
            )
        }
        val otherNetworkPassed = comparisonOnOtherNetwork != null &&
            comparisonOnOtherNetwork.profileName == current.profileName &&
            comparisonOnOtherNetwork.tunnelConnected
        if (otherNetworkPassed) {
            return DpiEvidenceAssessment(
                DpiEvidenceStatus.POSSIBLE_NETWORK_SPECIFIC_INTERFERENCE,
                "احتمال تفاوت مسیر یا محدودیت شبکه",
                "همین پروفایل در تست دیگری موفق و در تست فعلی ناموفق بوده است. این فقط نشانهٔ تفاوت شبکه/مسیر است؛ DPI تأیید نشده و تفاوت سرور، زمان یا شرایط تست هم ممکن است مؤثر باشد.",
                "تست را چند بار تکرار کن، زمان و شبکه را ثبت کن و DNS، پورت و TLS را با هم مقایسه کن."
            )
        }
        val stages = current.stages.associateBy { it.name }
        val dns = stages["dns"]?.state
        val tcp = stages["tcp"]?.state
        val tunnel = stages["tunnel"]?.state
        val configFailure = current.diagnosis?.category == ConnectionFailureCategory.CONFIGURATION ||
            current.diagnosis?.category == ConnectionFailureCategory.CORE_INITIALIZATION
        return when {
            configFailure -> DpiEvidenceAssessment(
                DpiEvidenceStatus.CONFIGURATION_OR_SERVER_ISSUE_POSSIBLE,
                "تنظیمات یا سمت سرور را بررسی کن",
                "پروفایل یا راه‌اندازی هسته ممکن است مشکل داشته باشد. از این نتیجه نمی‌توان DPI را نتیجه گرفت.",
                current.diagnosis?.nextStepFa ?: "پارامترهای پروفایل و تنظیمات سمت سرور را بررسی کن."
            )
            dns == DiagnosticStageState.FAIL || tcp == DiagnosticStageState.FAIL ->
                DpiEvidenceAssessment(
                    DpiEvidenceStatus.NODE_OR_DNS_UNREACHABLE,
                    "DNS یا دسترسی نود ناموفق است",
                    "نود یا پورت در این تست در دسترس نبود. علت می‌تواند DNS، خاموش بودن سرویس، مسیر شبکه یا فیلترینگ باشد؛ DPI مشخص نشده است.",
                    current.diagnosis?.nextStepFa ?: "دامنه، پورت و دسترسی سرور را جداگانه بررسی کن."
                )
            tunnel == DiagnosticStageState.FAIL ->
                DpiEvidenceAssessment(
                    DpiEvidenceStatus.UNKNOWN,
                    "وضعیت DPI نامشخص است",
                    "تونل برقرار نشد، اما شکست مذاکره یا درخواست می‌تواند از تنظیمات، سرویس مقصد، TLS/REALITY، مسیر شبکه یا فیلترینگ ناشی شود.",
                    current.diagnosis?.nextStepFa ?: "گزارش مرحله‌ها را بررسی و همان پروفایل را روی شبکهٔ دیگری آزمایش کن."
                )
            else -> DpiEvidenceAssessment(
                DpiEvidenceStatus.UNKNOWN,
                "شواهد کافی برای ارزیابی DPI نیست",
                "نتایج فعلی برای تعیین علت شکست یا مقاومت در برابر DPI کافی نیستند.",
                "یک تست کامل تونل اجرا کن و نتیجه را همراه با شبکه و زمان ذخیره کن."
            )
        }
    }
}
