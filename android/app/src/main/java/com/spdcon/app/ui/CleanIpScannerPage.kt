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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun CleanIpScannerPage(onBack: () -> Unit, onUseIp: (ip: String, sni: String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("spdcon-clean-ip-scanner", 0) }
    var mode by remember { mutableStateOf("tls") }
    var source by remember { mutableStateOf("default") }
    var countryCode by remember { mutableStateOf("ir") }
    var input by remember { mutableStateOf("") }
    var sni by remember { mutableStateOf("") }
    var dnsDomain by remember { mutableStateOf("example.com") }
    var dnsTransport by remember { mutableStateOf(DnsTransport.UDP) }
    var port by remember { mutableStateOf("443") }
    var workers by remember { mutableFloatStateOf(8f) }
    var scanning by remember { mutableStateOf(false) }
    var scanPaused by remember { mutableStateOf(false) }
    val scanControl = remember { ScanControl() }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf(emptyList<CleanIpResult>()) }
    var dnsResults by remember { mutableStateOf(emptyList<DnsResolverResult>()) }
    var recentIps by remember { mutableStateOf((prefs.getString("last_good_ips", "") ?: "").lines().filter(String::isNotBlank)) }
    var recentDnsIps by remember { mutableStateOf((prefs.getString("last_dns_ips", "") ?: "").lines().filter(String::isNotBlank)) }
    var history by remember { mutableStateOf(ScanHistoryStore.load(prefs)) }
    val scanningStatus = tr("Scanning candidates…")
    val noCandidatesError = tr("No IP candidates to scan")
    val scanFailedMessage = tr("Scan failed")
    val finishedTemplate = tr("Finished: {good} verified TLS IPs from {total} candidates")
    val finishedDnsTemplate = tr("Finished: {good} DNS-compatible resolvers from {total} candidates")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(tr("Clean IP Scanner"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (mode == "dns") tr("Test DNS resolver compatibility") else tr("Test IPv4 candidates against your real TLS domain"), color = TextSecondary, fontSize = 12.sp)
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        FilterChip(source == "default", { source = "default" }, label = { Text(tr("Default")) })
                        FilterChip(source == "custom", { source = "custom" }, label = { Text(tr("Custom / CIDR")) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        FilterChip(source == "recent", { source = "recent" }, label = { Text(tr("Recent")) })
                        FilterChip(source == "country", { source = "country" }, label = { Text(tr("Country ranges")) })
                    }
                }
                if (source == "country") {
                    Text(tr("Select the country whose registered IPv4 ranges will be sampled:"), color = TextSecondary, fontSize = 12.sp)
                    listOf(
                        "IR" to "Iran", "US" to "United States",
                        "DE" to "Germany", "NL" to "Netherlands",
                        "GB" to "United Kingdom", "FR" to "France",
                        "AZ" to "Azerbaijan", "TR" to "Turkey"
                    ).chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            pair.forEach { (code, label) ->
                                FilterChip(
                                    selected = countryCode == code.lowercase(),
                                    onClick = { countryCode = code.lowercase() },
                                    label = { Text("$label ($code)") }
                                )
                            }
                        }
                    }
                    Text(tr("Samples up to 256 public IPv4s from daily-updated ipverse registration ranges. Registration country does not guarantee physical location or a working tunnel."),
                        color = TextSecondary, fontSize = 11.sp)
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
                        if ((if (mode == "dns") recentDnsIps else recentIps).isEmpty()) tr("No successful results saved yet")
                        else tr("{count} previous successful IPs").replace("{count}", (if (mode == "dns") recentDnsIps else recentIps).size.toString()),
                        color = TextSecondary, fontSize = 12.sp
                    )
                    "country" -> Text(tr("Country sample is fetched when scanning starts. The source contains registered IPv4 prefixes."),
                        color = TextSecondary, fontSize = 12.sp)
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DnsTransport.values().forEach { transportOption ->
                    FilterChip(
                        selected = dnsTransport == transportOption,
                        onClick = { dnsTransport = transportOption },
                        label = { Text(when (transportOption) {
                            DnsTransport.UDP -> "UDP"
                            DnsTransport.TCP -> "TCP"
                            DnsTransport.BOTH -> "UDP + TCP fallback"
                        }) }
                    )
                }
            }
        }
        OutlinedTextField(
            value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(if (mode == "dns") tr("DNS port") else tr("HTTPS port")) }
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("Parallel workers: {count}").replace("{count}", workers.toInt().toString()), fontSize = 13.sp)
            Slider(value = workers, onValueChange = { workers = it }, valueRange = 1f..16f, steps = 14)
        }
        Button(
            onClick = {
                scanning = true; scanPaused = false; scanControl.paused = false; scanControl.cancelled = false
                error = null; status = if (source == "country") tr("Downloading country ranges and scanning…") else scanningStatus
                results = emptyList(); dnsResults = emptyList()
                scope.launch {
                    val outcome = executeCleanIpScan(
                        mode, source, input, countryCode, recentIps, recentDnsIps, sni.trim(), dnsDomain.trim(),
                        port.toIntOrNull() ?: 0, workers.toInt(), dnsTransport, noCandidatesError, scanFailedMessage,
                        finishedTemplate, finishedDnsTemplate, scanControl
                    ) { partialTls, partialDns, completed, total ->
                        results = partialTls
                        dnsResults = partialDns
                        status = if (scanControl.paused) "Paused • " + completed + "/" + total else "Scanning • " + completed + "/" + total
                    }
                    error = outcome.errorMessage
                    status = outcome.statusMessage
                    results = outcome.tlsResults
                    dnsResults = outcome.dnsResults
                    if (outcome.errorMessage == null) {
                        if (mode == "dns") {
                            recentDnsIps = outcome.goodIps
                            prefs.edit().putString("last_dns_ips", outcome.goodIps.joinToString("\n")).apply()
                        } else {
                            recentIps = outcome.goodIps
                            prefs.edit().putString("last_good_ips", outcome.goodIps.joinToString("\n")).apply()
                        }
                        ScanHistoryStore.record(prefs, mode, source, countryCode, outcome.tlsResults, outcome.dnsResults)
                        history = ScanHistoryStore.load(prefs)
                    }
                    scanPaused = false
                    scanning = false
                }
            },
            enabled = !scanning && if (mode == "dns") DnsResolverScanner.isValidDomain(dnsDomain.trim()) else CleanIpScanner.isValidSni(sni.trim()),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (scanning) tr("Scanning…") else tr("Find clean IPs")) }
        if (scanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scanPaused = !scanPaused
                    scanControl.paused = scanPaused
                }) { Text(if (scanPaused) tr("Resume") else tr("Pause")) }
                TextButton(onClick = {
                    scanControl.cancelled = true
                    scanControl.paused = false
                    scanPaused = false
                }) { Text(tr("Stop scan")) }
            }
        }
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

        if (history.isNotEmpty()) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(tr("Scan history (last 30 runs)"), fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(ScanHistoryStore.exportJson(prefs))) }) {
                            Text(tr("Export full JSON"))
                        }
                        TextButton(onClick = {
                            ScanHistoryStore.clear(prefs)
                            history = emptyList()
                        }) { Text(tr("Clear history")) }
                    }
                    history.take(10).forEach { entry ->
                        val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(entry.timestampMs))
                        val sourceLabel = if (entry.source == "country") "${entry.countryCode} country" else entry.source
                        Text(
                            "$time • ${entry.mode.uppercase()} • $sourceLabel • ${entry.successful}/${entry.total} good",
                            color = TextSecondary, fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}


private data class CleanIpScanOutcome(
    val tlsResults: List<CleanIpResult> = emptyList(),
    val dnsResults: List<DnsResolverResult> = emptyList(),
    val goodIps: List<String> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

private class ScanControl {
    @Volatile var paused: Boolean = false
    @Volatile var cancelled: Boolean = false

    suspend fun awaitIfResumed() {
        while (paused && !cancelled) delay(150)
    }
}

private suspend fun executeCleanIpScan(
    mode: String,
    source: String,
    customInput: String,
    countryCode: String,
    recentTls: List<String>,
    recentDns: List<String>,
    sni: String,
    dnsDomain: String,
    port: Int,
    workers: Int,
    dnsTransport: DnsTransport,
    noCandidatesError: String,
    scanFailedMessage: String,
    finishedTlsTemplate: String,
    finishedDnsTemplate: String,
    control: ScanControl,
    onProgress: suspend (List<CleanIpResult>, List<DnsResolverResult>, Int, Int) -> Unit
): CleanIpScanOutcome = try {
    val countrySample = if (source == "country") {
        withContext(Dispatchers.IO) { CountryIpRangeSource.fetchSample(countryCode) }
    } else null
    val candidates = withContext(Dispatchers.Default) {
        when (source) {
            "custom" -> CleanIpScanner.parseCandidates(customInput)
            "recent" -> (if (mode == "dns") recentDns else recentTls).take(CleanIpScanner.MAX_CANDIDATES)
            "country" -> countrySample?.ips.orEmpty()
            else -> if (mode == "dns") DnsResolverScanner.DEFAULT_RESOLVERS else CleanIpScanner.DEFAULT_CLOUDFLARE_CANDIDATES
        }
    }
    require(candidates.isNotEmpty()) { noCandidatesError }
    require(port in 1..65535) { "Port must be between 1 and 65535" }

    val allTls = mutableListOf<CleanIpResult>()
    val allDns = mutableListOf<DnsResolverResult>()
    var completed = 0
    val batchSize = (workers * 2).coerceIn(8, 32)
    for (batch in candidates.chunked(batchSize)) {
        control.awaitIfResumed()
        if (control.cancelled) break
        if (mode == "dns") {
            val scanned = withContext(Dispatchers.IO) {
                DnsResolverScanner.scan(batch, dnsDomain, port, workers = workers, transport = dnsTransport)
            }
            allDns += scanned
        } else {
            val scanned = withContext(Dispatchers.IO) {
                CleanIpScanner.scan(batch, port, sni, workers = workers)
            }
            allTls += scanned
        }
        completed += batch.size
        onProgress(allTls.toList(), allDns.toList(), completed, candidates.size)
    }

    val tls = allTls.sortedWith(compareBy<CleanIpResult> { !it.tlsOk }.thenBy { it.latencyMs })
    val dns = allDns.sortedWith(compareByDescending<DnsResolverResult> { it.score }.thenBy { it.latencyMs })
    val good = if (mode == "dns") dns.filter { it.score >= 2 }.map { it.ip }.distinct()
        else tls.filter { it.tlsOk }.map { it.ip }.distinct()
    val baseStatus = if (control.cancelled) {
        "Stopped after " + completed + "/" + candidates.size + " candidates • " + good.size + " passed"
    } else if (mode == "dns") {
        finishedDnsTemplate.replace("{good}", good.size.toString()).replace("{total}", dns.size.toString())
    } else {
        finishedTlsTemplate.replace("{good}", good.size.toString()).replace("{total}", tls.size.toString())
    }
    val countrySuffix = countrySample?.let { " • " + it.countryCode + ": " + it.prefixCount + " registered ranges" } ?: ""
    CleanIpScanOutcome(
        tlsResults = tls,
        dnsResults = dns,
        goodIps = good,
        statusMessage = baseStatus + countrySuffix
    )
} catch (e: Exception) {
    CleanIpScanOutcome(errorMessage = e.message ?: scanFailedMessage)
}
