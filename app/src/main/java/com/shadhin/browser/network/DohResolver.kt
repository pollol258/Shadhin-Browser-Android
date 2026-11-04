package com.shadhin.browser.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * RFC 8484 DNS-over-HTTPS (DoH) wire-format resolver for A and AAAA records.
 */
class DohResolver(
    private var provider: DohProvider = DohProvider.CLOUDFLARE,
    private val cache: LruDnsCache = LruDnsCache(),
    customClient: OkHttpClient? = null
) : Dns {

    private val httpClient: OkHttpClient = customClient ?: OkHttpClient.Builder()
        .dns(BootstrapDns())
        .connectionPool(okhttp3.ConnectionPool(32, 5, TimeUnit.MINUTES))
        .connectTimeout(3500, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .build()

    fun setProvider(newProvider: DohProvider) {
        if (provider != newProvider) {
            provider = newProvider
            cache.clear()
        }
    }

    fun getProvider(): DohProvider = provider

    override fun lookup(hostname: String): List<InetAddress> {
        val lower = hostname.lowercase().trim()

        // Check if input is already an IP address literal
        try {
            val literal = InetAddress.getByName(lower)
            if (literal.hostAddress == lower) {
                return listOf(literal)
            }
        } catch (_: Exception) {}

        if (provider == DohProvider.OFF) {
            return Dns.SYSTEM.lookup(hostname)
        }

        // Check Cache (minimum 60s TTL enforced)
        cache.get(lower)?.let { return it }

        // 1. Try Primary DoH Provider
        val primaryResolved = queryDohSyncForEndpoint(lower, provider.endpointUrl)
        if (primaryResolved.isNotEmpty()) {
            return primaryResolved
        }

        // 2. Fallback to Secondary DoH Provider (Cloudflare <-> Quad9)
        val secondaryEndpoint = if (provider == DohProvider.CLOUDFLARE) {
            DohProvider.QUAD9.endpointUrl
        } else {
            DohProvider.CLOUDFLARE.endpointUrl
        }
        val secondaryResolved = queryDohSyncForEndpoint(lower, secondaryEndpoint)
        if (secondaryResolved.isNotEmpty()) {
            return secondaryResolved
        }

        // 3. Fallback to System DNS to guarantee no 503 connection failure
        return try {
            val systemResolved = Dns.SYSTEM.lookup(hostname)
            if (systemResolved.isNotEmpty()) {
                cache.put(lower, systemResolved, 60L)
            }
            systemResolved
        } catch (e: Exception) {
            throw java.net.UnknownHostException("Resolution failed for $hostname: ${e.message}")
        }
    }

    suspend fun resolveAsync(hostname: String): List<InetAddress> {
        return withContext(Dispatchers.IO) {
            try {
                lookup(hostname)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun queryDohSyncForEndpoint(hostname: String, endpointUrl: String): List<InetAddress> {
        if (endpointUrl.isBlank()) return emptyList()
        val addresses = mutableListOf<InetAddress>()
        var minTtl = 300L

        // Query A record (type 1)
        val queryA = buildDnsWireQuery(hostname, type = 1)
        val responseA = executeDohQueryForEndpoint(queryA, endpointUrl)
        if (responseA != null) {
            val (parsedA, ttlA) = parseDnsWireResponse(responseA)
            addresses.addAll(parsedA)
            if (ttlA in 1 until minTtl) minTtl = ttlA
        }

        // Query AAAA record (type 28)
        val queryAAAA = buildDnsWireQuery(hostname, type = 28)
        val responseAAAA = executeDohQueryForEndpoint(queryAAAA, endpointUrl)
        if (responseAAAA != null) {
            val (parsedAAAA, ttlAAAA) = parseDnsWireResponse(responseAAAA)
            addresses.addAll(parsedAAAA)
            if (ttlAAAA in 1 until minTtl) minTtl = ttlAAAA
        }

        if (addresses.isNotEmpty()) {
            cache.put(hostname, addresses, minTtl)
        }
        return addresses
    }

    private fun executeDohQueryForEndpoint(wireBytes: ByteArray, endpoint: String): ByteArray? {
        if (endpoint.isBlank()) return null

        val request = Request.Builder()
            .url(endpoint)
            .post(wireBytes.toRequestBody("application/dns-message".toMediaType()))
            .addHeader("Accept", "application/dns-message")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /**
         * Builds an RFC 1035 / RFC 8484 DNS query packet.
         * Type 1 = A, Type 28 = AAAA.
         */
        fun buildDnsWireQuery(hostname: String, type: Int, id: Short = 0x1234.toShort()): ByteArray {
            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)

            // Header (12 bytes)
            dos.writeShort(id.toInt()) // ID
            dos.writeShort(0x0100) // Flags: Standard query, recursion desired (RD = 1)
            dos.writeShort(1) // QDCOUNT = 1
            dos.writeShort(0) // ANCOUNT = 0
            dos.writeShort(0) // NSCOUNT = 0
            dos.writeShort(0) // ARCOUNT = 0

            // Question Section: QNAME
            val labels = hostname.trim('.').split('.')
            for (label in labels) {
                val bytes = label.toByteArray(Charsets.US_ASCII)
                dos.writeByte(bytes.size)
                dos.write(bytes)
            }
            dos.writeByte(0) // Root label null terminator

            dos.writeShort(type) // QTYPE: 1 (A) or 28 (AAAA)
            dos.writeShort(1) // QCLASS: 1 (IN - Internet)
            dos.flush()

            return baos.toByteArray()
        }

        /**
         * Parses DNS response packet and extracts IP addresses and minimum TTL.
         */
        fun parseDnsWireResponse(data: ByteArray): Pair<List<InetAddress>, Long> {
            val addresses = mutableListOf<InetAddress>()
            var minTtl = 3600L
            if (data.size < 12) return Pair(emptyList(), minTtl)

            val buffer = ByteBuffer.wrap(data)
            buffer.short // ID
            val flags = buffer.short.toInt() and 0xFFFF
            val rcode = flags and 0x000F
            if (rcode != 0) { // Non-zero indicates error (e.g. NXDOMAIN = 3)
                return Pair(emptyList(), minTtl)
            }

            val qdCount = buffer.short.toInt() and 0xFFFF
            val anCount = buffer.short.toInt() and 0xFFFF
            buffer.short // nsCount
            buffer.short // arCount

            // Skip Question Section
            for (i in 0 until qdCount) {
                skipDomainName(buffer)
                buffer.short // QTYPE
                buffer.short // QCLASS
            }

            // Parse Answer Section
            for (i in 0 until anCount) {
                skipDomainName(buffer)
                val type = buffer.short.toInt() and 0xFFFF
                val clazz = buffer.short.toInt() and 0xFFFF
                val ttl = buffer.int.toLong() and 0xFFFFFFFFL
                val rdLength = buffer.short.toInt() and 0xFFFF

                if (ttl in 1 until minTtl) {
                    minTtl = ttl
                }

                if (clazz == 1 && rdLength == 4 && type == 1) { // Type A (IPv4)
                    val ipBytes = ByteArray(4)
                    buffer.get(ipBytes)
                    try {
                        addresses.add(InetAddress.getByAddress(ipBytes))
                    } catch (_: Exception) {}
                } else if (clazz == 1 && rdLength == 16 && type == 28) { // Type AAAA (IPv6)
                    val ipBytes = ByteArray(16)
                    buffer.get(ipBytes)
                    try {
                        addresses.add(InetAddress.getByAddress(ipBytes))
                    } catch (_: Exception) {}
                } else {
                    // Skip unknown or CNAME RDATA
                    buffer.position(buffer.position() + rdLength)
                }
            }

            return Pair(addresses, minTtl)
        }

        private fun skipDomainName(buffer: ByteBuffer) {
            while (buffer.hasRemaining()) {
                val len = buffer.get().toInt() and 0xFF
                if (len == 0) break
                if ((len and 0xC0) == 0xC0) {
                    // Pointer (2 bytes), second byte follows
                    buffer.get()
                    break
                } else {
                    buffer.position(buffer.position() + len)
                }
            }
        }
    }
}
