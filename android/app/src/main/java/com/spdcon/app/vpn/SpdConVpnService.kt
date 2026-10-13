package com.spdcon.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.spdcon.app.MainActivity
import libXray.DialerController
import libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Device-wide Android VPN service. The proxy outbound is produced by the pinned
 * official libXray parser and Android's TUN fd is handed to Xray-core.
 * "running" means the core accepted startup; it is not a remote reachability test.
 */
class SpdConVpnService : VpnService() {
    private val worker = Executors.newSingleThreadExecutor()
    private val stopping = AtomicBoolean(false)
    @Volatile private var tun: ParcelFileDescriptor? = null
    @Volatile private var running = false
    @Volatile private var activeName = "Spd con"

    private val socketController = DialerController { fd -> protect(fd.toInt()) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                activeName = intent.getStringExtra(EXTRA_NAME)?.take(100) ?: "Spd con"
                startForeground(NOTIFICATION_ID, notification("Preparing secure tunnel…"))
                val link = intent.getStringExtra(EXTRA_LINK)
                if (link.isNullOrBlank()) {
                    publishStatus("error", "No configuration link was provided")
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                worker.execute {
                    try {
                        startTunnel(link)
                    } catch (failure: Throwable) {
                        publishStatus("error", failure.message ?: failure.javaClass.simpleName)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                }
                return START_STICKY
            }
            else -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
    }

    private fun startTunnel(link: String) {
        stopping.set(false)
        if (running) {
            publishStatus("disconnecting", "Switching to the selected configuration")
            runCatching { invoke("stopXray", JSONObject()) }
            runCatching { LibXray.resetDNS() }
            runCatching { LibXray.registerDialerController(DialerController { false }) }
            runCatching { LibXray.registerListenerController(DialerController { false }) }
            runCatching { tun?.close() }
            tun = null
            running = false
        }
        publishStatus("connecting", "Preparing Android VPN interface")

        val conversion = invoke("convertShareLinksToXrayJson", JSONObject().put("text", link))
        val outbounds = if (conversion.optBoolean("success", false)) {
            conversion.optJSONObject("data")?.optJSONArray("outbounds")
                ?: throw IllegalArgumentException("Xray returned no outbound")
        } else if (link.startsWith("vmess://", ignoreCase = true)) {
            // The app's VMess QR serializer intentionally emits the legacy Base64(JSON)
            // form; current libXray imports VMessAEAD links, not this legacy format.
            JSONArray().put(buildLegacyVmessOutbound(link))
        } else {
            throw IllegalArgumentException(
                conversion.optString("error").ifBlank { "Xray could not parse this configuration link" }
            )
        }
        check(outbounds.length() > 0) { "Xray did not create a usable outbound" }

        val proxyOutbound = outbounds.optJSONObject(0)
            ?: throw IllegalArgumentException("Xray returned an invalid outbound")
        // Use a reserved, stable tag so imported remarks cannot collide with DNS/direct routes.
        val proxyTag = "spdcon-proxy"
        proxyOutbound.put("tag", proxyTag)

        val builder = Builder()
            .setSession("Spd con • $activeName")
            .setMtu(1400)
            .addAddress("10.77.0.2", 32)
            .addAddress("fdfe:dcba:9876::2", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("2606:4700:4700::1111")

        val established = builder.establish()
            ?: throw IllegalStateException("Android did not grant the VPN interface. Approve the system VPN dialog and retry.")
        tun = established

        try {
            // Register protection before starting Xray: otherwise the upstream
            // proxy connection can be captured by the VPN it is meant to carry.
            val dialerError = LibXray.registerDialerController(socketController)
            check(dialerError.isNullOrBlank()) { "Could not register Xray socket protection: $dialerError" }
            val listenerError = LibXray.registerListenerController(socketController)
            check(listenerError.isNullOrBlank()) { "Could not register Xray listener protection: $listenerError" }

            // Keep Go's resolver away from Android's VPN loopback DNS.
            val dnsError = LibXray.setDNS(socketController, "1.1.1.1:53")
            check(dnsError.isNullOrBlank()) { "Could not configure Xray DNS resolver: $dnsError" }

            val allOutbounds = JSONArray()
            allOutbounds.put(proxyOutbound)
            allOutbounds.put(JSONObject()
                .put("tag", "dns")
                .put("protocol", "dns")
                .put("settings", JSONObject()
                    .put("rules", JSONArray().put(JSONObject().put("action", "hijack")))))
            allOutbounds.put(JSONObject()
                .put("tag", "direct")
                .put("protocol", "freedom")
                .put("settings", JSONObject()))
            allOutbounds.put(JSONObject()
                .put("tag", "block")
                .put("protocol", "blackhole")
                .put("settings", JSONObject()))

            val inbound = JSONObject()
                .put("tag", "tun")
                .put("port", 0)
                .put("protocol", "tun")
                .put("settings", JSONObject()
                    .put("name", "spdcon0")
                    .put("mtu", 1400)
                    .put("gateway", JSONArray().put("10.77.0.1/30").put("fdfe:dcba:9876::1/126")))

            val xrayConfig = JSONObject()
                .put("log", JSONObject().put("loglevel", "warning"))
                .put("env", JSONObject().put("xray.tun.fd", established.fd.toString()))
                .put("dns", JSONObject()
                    .put("servers", JSONArray()
                        .put("https://1.1.1.1/dns-query")
                        .put("https://1.0.0.1/dns-query"))
                    .put("queryStrategy", "UseIP")
                    .put("tag", "spdcon-dns"))
                .put("inbounds", JSONArray().put(inbound))
                .put("outbounds", allOutbounds)
                .put("routing", JSONObject()
                    .put("domainStrategy", "AsIs")
                    .put("rules", JSONArray()
                        .put(JSONObject()
                            .put("type", "field")
                            .put("inboundTag", JSONArray().put("tun"))
                            .put("port", "53")
                            .put("outboundTag", "dns"))
                        .put(JSONObject()
                            .put("type", "field")
                            .put("inboundTag", JSONArray().put("spdcon-dns"))
                            .put("outboundTag", proxyTag))
                        .put(JSONObject()
                            .put("type", "field")
                            .put("inboundTag", JSONArray().put("tun"))
                            .put("outboundTag", proxyTag)))))

            publishStatus("connecting", "Starting Xray-core")
            val response = invoke("runXray", JSONObject().put("xrayJson", xrayConfig.toString()))
            check(response.optBoolean("success", false)) {
                response.optString("error").ifBlank { "Xray-core failed to start the tunnel" }
            }
            running = true
            val probeStarted = System.nanoTime()
            val reachable = probeThroughVpn()
            val probeMs = (System.nanoTime() - probeStarted) / 1_000_000L
            if (reachable) {
                publishStatus("connected", "Xray tunnel is running; HTTPS probe succeeded in ${probeMs} ms")
            } else {
                publishStatus("running-unverified", "Xray started, but the HTTPS probe failed. Check node parameters, DNS and UDP support.")
            }
        } catch (failure: Throwable) {
            runCatching { invoke("stopXray", JSONObject()) }
            runCatching { LibXray.resetDNS() }
            runCatching { LibXray.registerDialerController(DialerController { false }) }
            runCatching { LibXray.registerListenerController(DialerController { false }) }
            runCatching { established.close() }
            tun = null
            throw failure
        }
    }

    private fun stopTunnel() {
        if (!stopping.compareAndSet(false, true)) return
        worker.execute {
            publishStatus("disconnecting", "Stopping Xray-core")
            runCatching { invoke("stopXray", JSONObject()) }
            runCatching { LibXray.resetDNS() }
            runCatching { LibXray.registerDialerController(DialerController { false }) }
            runCatching { LibXray.registerListenerController(DialerController { false }) }
            runCatching { tun?.close() }
            tun = null
            running = false
            publishStatus("disconnected", "VPN disconnected")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        if (running) {
            runCatching { invoke("stopXray", JSONObject()) }
            runCatching { LibXray.resetDNS() }
        }
        runCatching { tun?.close() }
        tun = null
        worker.shutdownNow()
        super.onDestroy()
    }

    /** Tests the app's own HTTPS traffic after the system TUN route is active. */
    private fun probeThroughVpn(): Boolean = runCatching {
        val connection = URL("https://cp.cloudflare.com/").openConnection() as HttpURLConnection
        connection.connectTimeout = 7_000
        connection.readTimeout = 7_000
        connection.requestMethod = "HEAD"
        connection.instanceFollowRedirects = false
        try {
            connection.connect()
            connection.responseCode in 200..499
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    private fun buildLegacyVmessOutbound(link: String): JSONObject {
        val encoded = link.removePrefix("vmess://").trim()
        require(encoded.isNotBlank()) { "Legacy VMess link is empty" }
        val decoded = runCatching {
            String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)
        }.getOrElse { throw IllegalArgumentException("Invalid legacy VMess Base64 payload") }
        val vmess = JSONObject(decoded)
        val address = vmess.optString("add").trim()
        val port = vmess.optString("port").toIntOrNull()
            ?: throw IllegalArgumentException("VMess link has no valid port")
        val id = vmess.optString("id").trim()
        require(address.isNotBlank() && id.isNotBlank() && port in 1..65535) {
            "VMess link is missing server, port or UUID"
        }
        val network = when (vmess.optString("net", "tcp").lowercase()) {
            "tcp", "raw" -> "raw"
            "ws", "websocket" -> "ws"
            "grpc" -> "grpc"
            "xhttp", "splithttp" -> "xhttp"
            else -> throw IllegalArgumentException("Unsupported VMess transport: ${vmess.optString("net")}")
        }
        val user = JSONObject()
            .put("id", id)
            .put("alterId", vmess.optString("aid", "0").toIntOrNull() ?: 0)
            .put("security", vmess.optString("scy", "auto"))
        val server = JSONObject()
            .put("address", address)
            .put("port", port)
            .put("users", JSONArray().put(user))
        val stream = JSONObject()
            .put("network", network)
            .put("security", if (vmess.optString("tls").equals("tls", true) || vmess.optString("tls").equals("true", true)) "tls" else "none")
        when (network) {
            "ws" -> {
                val headers = JSONObject()
                vmess.optString("host").takeIf { it.isNotBlank() }?.let { headers.put("Host", it) }
                stream.put("wsSettings", JSONObject()
                    .put("path", vmess.optString("path", "/").ifBlank { "/" })
                    .put("headers", headers))
            }
            "grpc" -> stream.put("grpcSettings", JSONObject().put("serviceName", vmess.optString("path")))
            "xhttp" -> stream.put("xhttpSettings", JSONObject().put("path", vmess.optString("path", "/").ifBlank { "/" }))
            "raw" -> vmess.optString("type").takeIf { it.isNotBlank() && it != "none" }?.let {
                stream.put("tcpSettings", JSONObject().put("header", JSONObject().put("type", it)))
            }
        }
        if (stream.optString("security") == "tls") {
            val tls = JSONObject().put("serverName", vmess.optString("sni").ifBlank { address })
            vmess.optString("alpn").takeIf { it.isNotBlank() }?.let {
                tls.put("alpn", JSONArray(it.split(',').map(String::trim).filter(String::isNotBlank)))
            }
            vmess.optString("fp").takeIf { it.isNotBlank() }?.let { tls.put("fingerprint", it) }
            stream.put("tlsSettings", tls)
        }
        return JSONObject()
            .put("tag", "spdcon-proxy")
            .put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(server)))
            .put("streamSettings", stream)
    }

    private fun invoke(method: String, payload: JSONObject): JSONObject {
        val request = JSONObject()
            .put("apiVersion", API_VERSION)
            .put("method", method)
            .put("payload", payload)
            .toString()
        return JSONObject(LibXray.invoke(request))
    }

    private fun publishStatus(status: String, detail: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("status", status)
            .putString("detail", detail.take(500))
            .putString("profile", activeName)
            .apply()
        updateNotification(when (status) {
            "connected" -> "Connected • $activeName"
            "running-unverified" -> "Engine running; internet check failed • $activeName"
            "running" -> "Tunnel engine is running • $activeName"
            "error" -> "Connection failed • ${detail.take(80)}"
            else -> detail.take(100)
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Spd con VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "VPN connection status and disconnect control" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disconnect = PendingIntent.getService(
            this, 1, Intent(this, SpdConVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Spd con VPN")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Disconnect", disconnect)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(text))
    }

    companion object {
        const val ACTION_START = "com.spdcon.app.vpn.START"
        const val ACTION_STOP = "com.spdcon.app.vpn.STOP"
        const val EXTRA_LINK = "profile_link"
        const val EXTRA_NAME = "profile_name"
        const val PREFS = "spdcon-vpn-status"
        private const val CHANNEL_ID = "spdcon-vpn"
        private const val NOTIFICATION_ID = 4207
        private const val API_VERSION = 3
    }
}
