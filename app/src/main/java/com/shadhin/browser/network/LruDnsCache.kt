package com.shadhin.browser.network

import java.net.InetAddress
import java.util.LinkedHashMap

/**
 * Thread-safe LRU in-memory DNS cache respecting TTL.
 */
class LruDnsCache(private val maxCapacity: Int = 256) {

    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expiresAtEpochMs: Long
    )

    private val cache = object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            return size > maxCapacity
        }
    }

    @Synchronized
    fun get(hostname: String): List<InetAddress>? {
        val key = hostname.lowercase().trim()
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAtEpochMs) {
            cache.remove(key)
            return null
        }
        return entry.addresses
    }

    @Synchronized
    fun put(hostname: String, addresses: List<InetAddress>, ttlSeconds: Long) {
        if (addresses.isEmpty()) return
        val key = hostname.lowercase().trim()
        val effectiveTtl = ttlSeconds.coerceIn(60L, 86400L) // Min 60 sec, max 24 hours
        val expiresAt = System.currentTimeMillis() + (effectiveTtl * 1000L)
        cache[key] = CacheEntry(addresses, expiresAt)
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    @Synchronized
    fun size(): Int = cache.size
}
