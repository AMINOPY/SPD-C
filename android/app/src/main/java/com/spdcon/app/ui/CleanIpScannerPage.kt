package com.spdcon.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spdcon.app.ui.theme.NeonCyan
import com.spdcon.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CleanIpScannerPage(onBack: () -> Unit, onUseIp: (ip: String, sni: String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("spdcon-clean-ip-scanner", 0) }
    var source by remember { mutableStateOf("default") }
    var input by remember { mutableStateOf("") }
    var sni by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var workers by remember { mutableFloatStateOf(8f) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf(emptyList<CleanIpResult>()) }
    var recentIps by remember { mutableStateOf((prefs.getString("last_good_ips", "") ?: "").lines().filter(String::isNotBlank)) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(tr("Clean IP Scanner"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(tr("Test IPv4 candidates against your real TLS domain"), color = TextSecondary, fontSize = 12.sp)
            }
            TextButton(onClick = onBack) { Text(tr("Back")) }
        }
        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("IP source"), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterChip(source == "default", { source = "default" }, label = { Text(tr("Default")) })
                    FilterChip(source == "custom", { source = "custom" }, label = { Text(tr("Custom / CIDR")) })
                    FilterChip(source == "recent", { source = "recent" }, label = { Text(tr("Recent")) })
                }
                when (source) {
                    "custom" -> {
                        OutlinedTextField(
                            value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(),
                            minLines = 3, label = { Text(tr("Paste IPs, CIDR or ranges")) },
                            placeholder = { Text("104.16.0.1\n104.16.0.0/24\n104.16.0.1-104.16.0.20") }
                        )
                        Text(tr("Paste a list from a text file. Maximum 256 candidates per run."), color = TextSecondary, fontSize = 11.sp)
                    }
                    "recent" -> Text(
                        if (recentIps.isEmpty()) tr("No successful results saved yet")
                        else tr("{count} previous successful IPs").replace("{count}", recentIps.size.toString()),
                        color = TextSecondary, fontSize = 12.sp
                    )
                    else -> Text(tr("Small built-in Cloudflare edge sample; use your own domain below."),
                        color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
        OutlinedTextField(
            value = sni, onValueChange = { sni = it.trim() }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, label = { Text(tr("Your TLS / Cloudflare domain (SNI)")) },
            placeholder = { Text("subdomain.example.com") }
        )
        OutlinedTextField(
            value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(tr("HTTPS port")) }
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("Parallel workers: {count}").replace("{count}", workers.toInt().toString()), fontSize = 13.sp)
            Slider(value = workers, onValueChange = { workers = it }, valueRange = 1f..16f, steps = 14)
        }
        Button(
            onClick = {
                scanning = true; error = null; status = tr("Scanning candidates…"); results = emptyList()
                scope.launch {
                    try {
                        val candidates = withContext(Dispatchers.Default) {
                            when (source) {
                                "custom" -> CleanIpScanner.parseCandidates(input)
                                "recent" -> recentIps.take(CleanIpScanner.MAX_CANDIDATES)
                                else -> CleanIpScanner.DEFAULT_CLOUDFLARE_CANDIDATES
                            }
                        }
                        require(candidates.isNotEmpty()) { tr("No IP candidates to scan") }
                        val selectedPort = port.toIntOrNull() ?: 0
                        val scanned = withContext(Dispatchers.IO) {
                            CleanIpScanner.scan(candidates, selectedPort, sni.trim(), workers = workers.toInt())
                        }
                        results = scanned
                        val good = scanned.filter { it.tlsOk }.map { it.ip }.distinct()
                        recentIps = good
                        prefs.edit().putString("last_good_ips", good.joinToString("\n")).apply()
                        status = tr("Finished: {good} verified TLS IPs from {total} candidates")
                            .replace("{good}", good.size.toString()).replace("{total}", scanned.size.toString())
                    } catch (e: Exception) {
                        error = e.message ?: tr("Scan failed"); status = null
                    } finally { scanning = false }
                }
            },
            enabled = !scanning && CleanIpScanner.isValidSni(sni.trim()),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (scanning) tr("Scanning…") else tr("Find clean IPs")) }
        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(it, color = NeonCyan, fontSize = 12.sp) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        if (results.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tr("Results"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { clipboard.setText(AnnotatedString(results.filter { it.tlsOk }.joinToString("\n") { it.ip })) }) {
                    Text(tr("Copy good IPs"))
                }
            }
            Text(tr("A verified TLS handshake is a reachability signal, not a guarantee that a proxy or route will work."),
                color = TextSecondary, fontSize = 11.sp)
            results.forEach { result ->
                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(result.ip, fontWeight = FontWeight.SemiBold)
                            Text(if (result.tlsOk) "${result.latencyMs} ms" else tr("Failed"),
                                color = if (result.tlsOk) NeonCyan else MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                        Text(tr(result.detail), color = TextSecondary, fontSize = 11.sp)
                        if (result.tlsOk) TextButton(onClick = { onUseIp(result.ip, sni.trim()) }) {
                            Text(tr("Use IP in config"))
                        }
                    }
                }
            }
        }
    }
}
