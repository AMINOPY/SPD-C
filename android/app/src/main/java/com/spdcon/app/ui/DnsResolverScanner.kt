package com.spdcon.app.ui

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.Socket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom

enum class DnsTransport { UDP, TCP, BOTH }

data class DnsResolverResult(
    val ip: String,
    val latencyMs: Long,
    val score: Int,
    val maxScore: Int,
    val status: String,
    val transport: String = "UDP"
)

/** Bounded DNS compatibility probe. DNS reachability is not proof of a working VPN tunnel. */
object DnsResolverScanner {
    const val MAX_CANDIDATES = 256
    const val DEFAULT_PORT = 53
    val DEFAULT_RESOLVERS = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "208.67.222.222", "94.140.14.14")

    fun scan(candidates: List<String>, testDomain: String = "example.com", port: Int = DEFAULT_PORT,
             timeoutMs: Int = 1800, workers: Int = 8, transport: DnsTransport = DnsTransport.UDP): List<DnsResolverResult> {
        require(candidates.isNotEmpty()) { "No DNS resolver candidates were provided" }
        require(candidates.size <= MAX_CANDIDATES) { "Maximum " + MAX_CANDIDATES + " resolvers per run" }
        require(candidates.all(::isIpv4)) { "Resolver candidates must be IPv4 addresses" }
        require(isValidDomain(testDomain)) { "Enter a valid DNS test domain" }
        require(port in 1..65535) { "DNS port must be between 1 and 65535" }
        require(timeoutMs in 500..5000) { "Timeout must be between 500 and 5000 ms" }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(workers.coerceIn(1, 16))
        return try {
            pool.invokeAll(candidates.distinct().map { ip ->
                java.util.concurrent.Callable { probe(ip, testDomain, port, timeoutMs, transport) }
            }).map { it.get() }.sortedWith(compareByDescending<DnsResolverResult> { it.score }.thenBy { it.latencyMs })
        } finally { pool.shutdownNow() }
    }

    fun isValidDomain(value: String): Boolean {
        if (value.isBlank() || value.length > 253 || value.startsWith(".") || value.endsWith(".") ||
            value.contains("..") || value.any { it.isWhitespace() || it in "/?#@:" }) return false
        return value.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 && label.first().isLetterOrDigit() &&
                label.last().isLetterOrDigit() && label.all { it.isLetterOrDigit() || it == '-' }
        }
    }

    fun isIpv4(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) && (it.toIntOrNull() ?: 999) in 0..255 }
    }

    private fun probe(ip: String, domain: String, port: Int, timeoutMs: Int, transport: DnsTransport): DnsResolverResult {
        val start = System.nanoTime()
        var score = 0
        val details = mutableListOf<String>()
        try {
            val address = InetAddress.getByName(ip)
            val a = query(address, port, domain, 1, timeoutMs, transport)
            if (a.rcode == 0 && a.answers > 0) { score++; details += "A✓" } else details += "A✗"
            val txt = query(address, port, domain, 16, timeoutMs, transport)
            if (txt.rcode == 0) { score++; details += "TXT✓" } else details += "TXT✗"
            val randomLabel = "spd-" + java.lang.Long.toString(SecureRandom().nextLong().ushr(1), 36) + "." + domain
            val random = query(address, port, randomLabel, 1, timeoutMs, transport)
            if (random.rcode == 3) { score++; details += "NXD✓" }
            else details += "NXD✗"
            return DnsResolverResult(ip, (System.nanoTime() - start) / 1_000_000L, score, 3,
                if (score == 3) "DNS compatible" else details.joinToString(" "), transport = transport.name)
        } catch (e: Exception) {
            val message = if (e is SocketTimeoutException) "TIMEOUT" else e.javaClass.simpleName
            return DnsResolverResult(ip, (System.nanoTime() - start) / 1_000_000L, score, 3, message)
        }
    }

    private data class DnsResponse(val rcode: Int, val answers: Int)

    private fun query(server: InetAddress, port: Int, domain: String, type: Int, timeoutMs: Int, transport: DnsTransport): DnsResponse {
        if (transport == DnsTransport.TCP) return queryTcp(server, port, domain, type, timeoutMs)
        if (transport == DnsTransport.BOTH) {
            return try { queryUdp(server, port, domain, type, timeoutMs) }
            catch (_: Exception) { queryTcp(server, port, domain, type, timeoutMs) }
        }
        return queryUdp(server, port, domain, type, timeoutMs)
    }

    private fun queryUdp(server: InetAddress, port: Int, domain: String, type: Int, timeoutMs: Int): DnsResponse {
        val id = SecureRandom().nextInt(65536)
        val request = buildQuery(id, domain, type)
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(server, port))
            socket.send(DatagramPacket(request, request.size, server, port))
            val buffer = ByteArray(4096)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            if (packet.length < 12) throw IllegalStateException("Malformed DNS response")
            val data = packet.data
            val responseId = ((data[0].toInt() and 0xff) shl 8) or (data[1].toInt() and 0xff)
            if (responseId != id) throw IllegalStateException("Mismatched DNS response")
            val flags = ((data[2].toInt() and 0xff) shl 8) or (data[3].toInt() and 0xff)
            if ((flags and 0x8000) == 0) throw IllegalStateException("Not a DNS response")
            val answers = ((data[6].toInt() and 0xff) shl 8) or (data[7].toInt() and 0xff)
            return DnsResponse(flags and 0x000f, answers)
        }
    }


    private fun queryTcp(server: InetAddress, port: Int, domain: String, type: Int, timeoutMs: Int): DnsResponse {
        val id = SecureRandom().nextInt(65536)
        val request = buildQuery(id, domain, type)
        Socket().use { socket ->
            socket.connect(InetSocketAddress(server, port), timeoutMs)
            socket.soTimeout = timeoutMs
            val output = DataOutputStream(socket.getOutputStream())
            output.writeShort(request.size)
            output.write(request)
            output.flush()
            val input = DataInputStream(socket.getInputStream())
            val length = input.readUnsignedShort()
            if (length < 12 || length > 4096) throw IllegalStateException("Malformed TCP DNS response")
            val data = ByteArray(length)
            input.readFully(data)
            val responseId = ((data[0].toInt() and 0xff) shl 8) or (data[1].toInt() and 0xff)
            if (responseId != id) throw IllegalStateException("Mismatched DNS response")
            val flags = ((data[2].toInt() and 0xff) shl 8) or (data[3].toInt() and 0xff)
            if ((flags and 0x8000) == 0) throw IllegalStateException("Not a DNS response")
            val answers = ((data[6].toInt() and 0xff) shl 8) or (data[7].toInt() and 0xff)
            return DnsResponse(flags and 0x000f, answers)
        }
    }

    private fun buildQuery(id: Int, domain: String, type: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun word(n: Int) { out.write((n ushr 8) and 0xff); out.write(n and 0xff) }
        word(id); word(0x0100); word(1); word(0); word(0); word(1)
        domain.split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            require(bytes.isNotEmpty() && bytes.size <= 63) { "Invalid DNS label" }
            out.write(bytes.size); out.write(bytes)
        }
        out.write(0); word(type); word(1)
        out.write(0); word(41); word(1232); out.write(0); out.write(0); word(0); word(0)
        return out.toByteArray()
    }
}
