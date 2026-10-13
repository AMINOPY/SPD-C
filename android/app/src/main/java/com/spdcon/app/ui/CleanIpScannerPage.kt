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
    var mode by remember { mutableStateOf("tls") }
    var source by remember { mutableStateOf("default") }
    var input by remember { mutableStateOf("") }
    var sni by remember { mutableStateOf("") }
    var dnsDomain by remember { mutableStateOf("example.com") }
    var port by remember { mutableStateOf("443") }
    var workers by remember { mutableFloatStateOf(8f) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf(emptyList<CleanIpResult>()) }
    var dnsResults by remember { mutableStateOf(emptyList<DnsResolverResult>()) }
    var recentIps by remember { mutableStateOf((prefs.getString("last_good_ips", "") ?: "").lines().filter(String::isNotBlank)) }
    var recentDnsIps by remember { mutableStateOf((prefs.getString("last_dns_ips", "") ?: "").lines().filter(String::isNotBlank)) }
    val scanningStatus = tr("Scanning candidates…")
    val noCandidatesError = tr("No IP candidates to scan")
    val scanFailedMessage = tr("Scan failed")
    val finishedTemplate = tr("Finished: {good} verified TLS IPs from {total} candidates")

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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == "tls", onClick = { mode = "tls"; port = "443" },
                label = { Text(tr("Clean TLS IP")) })
            FilterChip(selected = mode == "dns", onClick = { mode = "dns"; port = "53" },
                label = { Text(tr("DNS Resolver (SlipNet-style)")) })
        }
        Text(
            if (mode == "dns") tr("Checks DNS A, TXT and random-subdomain/NXDOMAIN behaviour.")
            else tr("Checks TLS reachability with your real domain; this is not a full tunnel test."),
            color = TextSecondary, fontSize = 12.sp
        )
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
                        else tr("{count} previous successful IPs").replace("{count}", if (mode == "dns") recentDnsIps.size.toString() else recentIps.size.toString()),
                        color = TextSecondary, fontSize = 12.sp
                    )
                    else -> Text(tr("Small built-in Cloudflare edge sample; use your own domain below."),
                        color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
        if (mode == "tls") {
            OutlinedTextField(
                value = sni, onValueChange = { sni = it.trim() }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text(tr("Your TLS / Cloudflare domain (SNI)")) },
                placeholder = { Text("subdomain.example.com") }
            )
        } else {
            OutlinedTextField(
                value = dnsDomain, onValueChange = { dnsDomain = it.trim() }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text(tr("DNS test domain")) },
                placeholder = { Text("example.com") }
            )
        }
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
                scanning = true; error = null; status = scanningStatus; results = emptyList()
                scope.launch {
                    try {
                        val candidates = withContext(Dispatchers.Default) {
                            when (source) {
                                "custom" -> CleanIpScanner.parseCandidates(input)
                                "recent" -> (if (mode == "dns") recentDnsIps else recentIps).take(CleanIpScanner.MAX_CANDIDATES)
                                else -> if (mode == "dns") DnsResolverScanner.DEFAULT_RESOLVERS else CleanIpScanner.DEFAULT_CLOUDFLARE_CANDIDATES
                            }
                        }
                        require(candidates.isNotEmpty()) { noCandidatesError }
                        val selectedPort = port.toIntOrNull() ?: 0
                        if (mode == "dns") {
                            val scanned = withContext(Dispatchers.IO) {
                                DnsResolverScanner.scan(candidates, dnsDomain.trim(), selectedPort, workers = workers.toInt())
                            }
                            dnsResults = scanned
                            results = emptyList()
                            val good = scanned.filter { it.score >= 2 }.map { it.ip }.distinct()
                            recentDnsIps = good
                            prefs.edit().putString("last_dns_ips", good.joinToString("\n")).apply()
                            status = tr("Finished: {good} DNS-compatible resolvers from {total} candidates")
                                .replace("{good}", good.size.toString()).replace("{total}", scanned.size.toString())
                        } else {
                            val scanned = withContext(Dispatchers.IO) {
                                CleanIpScanner.scan(candidates, selectedPort, sni.trim(), workers = workers.toInt())
                            }
                            results = scanned
                            dnsResults = emptyList()
                            val good = scanned.filter { it.tlsOk }.map { it.ip }.distinct()
                            recentIps = good
                            prefs.edit().putString("last_good_ips", good.joinToString("\n")).apply()
                            status = finishedTemplate
                                .replace("{good}", good.size.toString()).replace("{total}", scanned.size.toString())
                        }
                    } catch (e: Exception) {
                        error = e.message ?: scanFailedMessage; status = null
                    } finally { scanning = false }
                }
            },
            enabled = !scanning && if (mode == "dns") DnsResolverScanner.isValidDomain(dnsDomain.trim()) else CleanIpScanner.isValidSni(sni.trim()),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (scanning) tr("Scanning…") else tr("Find clean IPs")) }
        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(it, color = NeonCyan, fontSize = 12.sp) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        if (dnsResults.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tr("DNS resolver results"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { clipboard.setText(AnnotatedString(dnsResults.filter { it.score >= 2 }.joinToString("\n") { it.ip })) }) {
                    Text(tr("Copy compatible IPs"))
                }
            }
            Text(tr("Score counts successful A, TXT and random-subdomain/NXDOMAIN probes. This is a DNS compatibility check, not proof of end-to-end tunnel connectivity."),
                color = TextSecondary, fontSize = 11.sp)
            dnsResults.forEach { result ->
                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(result.ip, fontWeight = FontWeight.SemiBold)
                            Text(result.score.toString() + "/" + result.maxScore + " • " + result.latencyMs + " ms",
                                color = if (result.score >= 2) NeonCyan else MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                        Text(tr(result.status), color = TextSecondary, fontSize = 11.sp)
                        TextButton(onClick = { clipboard.setText(AnnotatedString(result.ip)) }) { Text(tr("Copy IP")) }
                    }
                }
            }
        }
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
