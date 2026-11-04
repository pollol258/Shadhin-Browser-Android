package com.shadhin.browser.engine

import java.net.URLEncoder

object UrlNormalizer {

    private const val SEARCH_ENGINE_PREFIX = "https://www.google.com/search?q="
    private val IP_REGEX = Regex("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$")
    private val DOMAIN_REGEX = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z]{2,})+$")

    fun normalizeInput(
        rawInput: String,
        allowLocalHttp: Boolean = false,
        searchPrefix: String = SEARCH_ENGINE_PREFIX
    ): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return ""

        // Preserve already well-formed special or web URIs
        if (trimmed.startsWith("about:") || trimmed.startsWith("javascript:") || trimmed.startsWith("data:")) {
            return trimmed
        }

        if (trimmed.startsWith("https://")) {
            return trimmed
        }

        if (trimmed.startsWith("http://")) {
            val withoutScheme = trimmed.removePrefix("http://")
            val host = extractHostFromCandidate(withoutScheme)
            // If it's a private / LAN address and local HTTP is allowed, allow http://
            if (allowLocalHttp && isLocalOrLanAddress(host)) {
                return trimmed
            }
            // Otherwise, upgrade to https://
            return "https://$withoutScheme"
        }

        // Host without scheme: e.g. "google.com", "example.com/test", "192.168.1.1:8080", "localhost:3000"
        if (isLikelyWebAddress(trimmed)) {
            val host = extractHostFromCandidate(trimmed)
            if (allowLocalHttp && isLocalOrLanAddress(host)) {
                return "http://$trimmed"
            }
            return "https://$trimmed"
        }

        // Fallback: search query
        return buildSearchUrl(trimmed, searchPrefix)
    }

    fun isLocalOrLanAddress(host: String): Boolean {
        val cleanHost = host.lowercase().substringBefore(":")
        if (cleanHost == "localhost" || cleanHost == "127.0.0.1" || cleanHost == "10.0.2.2") {
            return true
        }

        // Check IPv4 private ranges
        val parts = cleanHost.split(".")
        if (parts.size == 4) {
            val p0 = parts[0].toIntOrNull()
            val p1 = parts[1].toIntOrNull()
            val p2 = parts[2].toIntOrNull()
            val p3 = parts[3].toIntOrNull()
            if (p0 != null && p1 != null && p2 != null && p3 != null) {
                // 10.0.0.0/8
                if (p0 == 10) return true
                // 192.168.0.0/16
                if (p0 == 192 && p1 == 168) return true
                // 172.16.0.0/12 (172.16.x.x - 172.31.x.x)
                if (p0 == 172 && p1 in 16..31) return true
            }
        }

        return cleanHost.endsWith(".local") || cleanHost.endsWith(".lan")
    }

    fun isLikelyWebAddress(text: String): Boolean {
        if (text.contains(" ") || text.contains("\n") || text.contains("\r")) {
            return false
        }

        val hostPart = text.substringBefore("/").substringBefore("?")
        val cleanHost = hostPart.substringBefore(":")

        // Localhost and standard LAN names
        if (cleanHost.equals("localhost", ignoreCase = true)) {
            return true
        }

        // IP addresses
        if (IP_REGEX.matches(cleanHost)) {
            return true
        }

        // Standard domain matching (must contain at least one dot with valid TLD structure)
        if (DOMAIN_REGEX.matches(cleanHost)) {
            return true
        }

        if (cleanHost.contains(".")) {
            val tld = cleanHost.substringAfterLast(".", "")
            if (tld.isNotEmpty() && tld.all { it.isLetter() } && tld.length >= 2) {
                return true
            }
        }

        return false
    }

    private fun extractHostFromCandidate(candidate: String): String {
        val segment = candidate.substringBefore("/").substringBefore("?")
        return segment.substringBefore(":")
    }

    fun buildSearchUrl(query: String, searchPrefix: String = SEARCH_ENGINE_PREFIX): String {
        val encoded = try {
            URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        } catch (_: Exception) {
            query.replace(" ", "%20")
        }
        return "$searchPrefix$encoded"
    }

    fun normalizeWebAddress(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("about:")) {
            return trimmed
        }
        return "https://$trimmed"
    }

    fun isValidUrlOrHost(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.contains(" ")) return false
        val candidate = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed.substringAfter("://")
        } else {
            trimmed
        }
        return isLikelyWebAddress(candidate)
    }
}
