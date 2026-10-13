import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.spdcon.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.spdcon.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Use the official XTLS/libXray Android release (bundled Xray-core), pinned and SHA-256 verified.
val xrayCoreVersion = "v26.10.10"
val xrayCoreZipSha256 = "aad01c82eb82e98702838f7b4ac3f33dba1ae5ff4102538e5ca7f05bbab57437"
val xrayCoreCacheDir = layout.projectDirectory.dir(".gradle/xray-core/$xrayCoreVersion")
val xrayCoreAar = xrayCoreCacheDir.file("libXray.aar")
val prepareXrayCoreAar by tasks.registering {
    inputs.property("xrayCoreVersion", xrayCoreVersion)
    inputs.property("xrayCoreZipSha256", xrayCoreZipSha256)
    outputs.file(xrayCoreAar)
    doLast {
        val aarFile = xrayCoreAar.asFile
        if (aarFile.isFile) return@doLast
        val cacheDir = xrayCoreCacheDir.asFile
        check(cacheDir.mkdirs() || cacheDir.isDirectory) { "Could not create Xray core cache directory" }
        val zipFile = File(cacheDir, "libxray-android.zip")
        if (!zipFile.isFile) {
            val url = URL("https://github.com/XTLS/libXray/releases/download/$xrayCoreVersion/libxray-android.zip")
            url.openConnection().apply {
                connectTimeout = 20_000
                readTimeout = 180_000
            }.getInputStream().use { input ->
                zipFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val actualSha = MessageDigest.getInstance("SHA-256")
            .digest(zipFile.readBytes()).joinToString("") { "%02x".format(it) }
        check(actualSha == xrayCoreZipSha256) {
            "Official Xray core archive checksum mismatch; delete $zipFile and retry"
        }
        ZipFile(zipFile).use { archive ->
            val entry = archive.getEntry("libXray.aar")
                ?: archive.entries().asSequence().firstOrNull { it.name.endsWith("/libXray.aar") }
                ?: error("Official Xray core archive did not contain libXray.aar")
            archive.getInputStream(entry).use { input ->
                aarFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        check(aarFile.length() > 10_000_000L) { "Downloaded Xray core AAR looks incomplete" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("com.google.zxing:core:3.5.4")
    implementation(files(xrayCoreAar.asFile))

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// Ensure the pinned native core is present before Android resolves local AAR dependencies.
tasks.named("preBuild").configure { dependsOn(prepareXrayCoreAar) }
