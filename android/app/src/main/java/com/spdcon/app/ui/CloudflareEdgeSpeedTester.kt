package com.spdcon.app.ui

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class CloudflareSpeedResult(
    val ip: String,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val downloadBytes: Long,
    val uploadBytes: Long,
    val downloadMs: Long,
    val uploadMs: Long
)

/**
 * Small, user-triggered bandwidth probe against Cloudflare's speed endpoint, pinned to one IP.
 * This only measures Cloudflare edge IPs; it is not a speed test for an arbitrary custom origin.
 */
object CloudflareEdgeSpeedTester {
    private const val HOST = "speed.cloudflare.com"
    private const val PORT = 443
    private const val DEFAULT_BYTES = 512 * 1024
    private const val MAX_BYTES = 2 * 1024 * 1024

    fun test(ip: String, bytesPerDirection: Int = DEFAULT_BYTES, timeoutMs: Int = 15_000): CloudflareSpeedResult {
        require(CleanIpScanner.parseCandidates(ip).singleOrNull() == ip) { "Enter a valid IPv4 address" }
        require(bytesPerDirection in 64 * 1024..MAX_BYTES) { "Speed test size must be between 64 KiB and 2 MiB" }
        require(timeoutMs in 2_000..30_000) { "Timeout must be between 2 and 30 seconds" }

        val download = download(ip, bytesPerDirection, timeoutMs)
        require(download.bytes > 0) { "Cloudflare speed endpoint returned no download data" }

        val upload = upload(ip, bytesPerDirection, timeoutMs)
        require(upload.status in 200..299) { "Cloudflare upload endpoint returned HTTP ${upload.status}" }

        return CloudflareSpeedResult(
            ip = ip,
            downloadMbps = mbps(download.bytes, download.elapsedMs),
            uploadMbps = mbps(bytesPerDirection.toLong(), upload.elapsedMs),
            downloadBytes = download.bytes,
            uploadBytes = bytesPerDirection.toLong(),
            downloadMs = download.elapsedMs,
            uploadMs = upload.elapsedMs
        )
    }

    private data class TransferResult(val bytes: Long, val elapsedMs: Long)
    private data class UploadResult(val status: Int, val elapsedMs: Long)

    private fun download(ip: String, requestedBytes: Int, timeoutMs: Int): TransferResult {
        return openTls(ip, timeoutMs).use { socket ->
            val started = System.nanoTime()
            val output = socket.outputStream
            output.write(
                ("GET /__down?bytes=$requestedBytes HTTP/1.1\r\n" +
                    "Host: $HOST\r\n" +
                    "Accept-Encoding: identity\r\n" +
                    "Connection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)
            )
            output.flush()
            val input = BufferedInputStream(socket.inputStream)
            val response = readHeaders(input)
            require(response.status in 200..299) { "Cloudflare download endpoint returned HTTP ${response.status}" }
            val transferEncoding = response.headers["transfer-encoding"].orEmpty()
            val downloaded = when {
                transferEncoding.contains("chunked", ignoreCase = true) -> readChunkedBody(input)
                response.headers["content-length"]?.toLongOrNull() != null ->
                    readExactBody(input, response.headers.getValue("content-length").toLong())
                else -> readUntilEof(input)
            }
            TransferResult(downloaded, elapsedMs(started))
        }
    }

    private fun upload(ip: String, requestedBytes: Int, timeoutMs: Int): UploadResult {
        return openTls(ip, timeoutMs).use { socket ->
            val started = System.nanoTime()
            val output = socket.outputStream
            output.write(
                ("POST /__up HTTP/1.1\r\n" +
                    "Host: $HOST\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "Content-Length: $requestedBytes\r\n" +
                    "Connection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)
            )
            val block = ByteArray(16 * 1024) { 0x53.toByte() }
            var remaining = requestedBytes
            while (remaining > 0) {
                val size = minOf(block.size, remaining)
                output.write(block, 0, size)
                remaining -= size
            }
            output.flush()
            val response = readHeaders(BufferedInputStream(socket.inputStream))
            UploadResult(response.status, elapsedMs(started))
        }
    }

    private fun openTls(ip: String, timeoutMs: Int): SSLSocket {
        val raw = Socket()
        try {
            raw.connect(InetSocketAddress(ip, PORT), timeoutMs)
            raw.soTimeout = timeoutMs
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val ssl = factory.createSocket(raw, HOST, PORT, true) as SSLSocket
            ssl.soTimeout = timeoutMs
            ssl.sslParameters = ssl.sslParameters.apply {
                serverNames = listOf(SNIHostName(HOST))
                endpointIdentificationAlgorithm = "HTTPS"
            }
            ssl.startHandshake()
            return ssl
        } catch (e: Exception) {
            runCatching { raw.close() }
            throw e
        }
    }

    private data class HttpHeaders(val status: Int, val headers: Map<String, String>)

    private fun readHeaders(input: BufferedInputStream): HttpHeaders {
        val bytes = ByteArrayOutputStream()
        var matched = 0
        while (bytes.size() < 32 * 1024) {
            val next = input.read()
            if (next < 0) throw IllegalStateException("Connection closed before HTTP headers arrived")
            bytes.write(next)
            matched = when {
                matched == 0 && next == 13 -> 1
                matched == 1 && next == 10 -> 2
                matched == 2 && next == 13 -> 3
                matched == 3 && next == 10 -> 4
                next == 13 -> 1
                else -> 0
            }
            if (matched == 4) break
        }
        require(matched == 4) { "HTTP response headers exceeded the size limit" }
        val lines = bytes.toString("US-ASCII").split("\r\n")
        val status = lines.firstOrNull()?.split(' ')?.getOrNull(1)?.toIntOrNull()
            ?: throw IllegalStateException("Malformed HTTP status line")
        val headers = linkedMapOf<String, String>()
        lines.drop(1).forEach { line ->
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        return HttpHeaders(status, headers)
    }

    private fun readChunkedBody(input: BufferedInputStream): Long {
        var total = 0L
        while (true) {
            val sizeLine = readAsciiLine(input).substringBefore(';').trim()
            val chunkSize = sizeLine.toLongOrNull(16)
                ?: throw IllegalStateException("Malformed chunked HTTP response")
            if (chunkSize == 0L) {
                while (readAsciiLine(input).isNotEmpty()) Unit
                return total
            }
            total += readExactBody(input, chunkSize)
            val cr = input.read()
            val lf = input.read()
            require(cr == 13 && lf == 10) { "Malformed chunked HTTP response terminator" }
        }
    }

    private fun readAsciiLine(input: BufferedInputStream): String {
        val bytes = ByteArrayOutputStream()
        var previous = -1
        while (bytes.size() < 8192) {
            val current = input.read()
            if (current < 0) throw IllegalStateException("Unexpected end of HTTP response")
            if (previous == 13 && current == 10) {
                val data = bytes.toByteArray()
                return String(data, 0, (data.size - 1).coerceAtLeast(0), StandardCharsets.US_ASCII)
            }
            bytes.write(current)
            previous = current
        }
        throw IllegalStateException("HTTP response line exceeded the size limit")
    }

    private fun readExactBody(input: BufferedInputStream, length: Long): Long {
        var remaining = length
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw IllegalStateException("Download ended before Content-Length bytes arrived")
            remaining -= count
        }
        return length
    }

    private fun readUntilEof(input: BufferedInputStream): Long {
        var total = 0L
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
        }
        return total
    }

    private fun elapsedMs(start: Long) = ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
    private fun mbps(bytes: Long, millis: Long) = bytes * 8.0 / millis / 1000.0
}
