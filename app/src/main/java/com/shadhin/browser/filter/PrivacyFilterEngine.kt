package com.shadhin.browser.filter

import android.net.Uri

/**
 * High-speed in-memory network tracker and telemetry blocklist.
 * Matches hostnames and all subdomains in shouldInterceptRequest.
 */
class PrivacyFilterEngine {

    private val blockedDomains = HashSet<String>()

    init {
        // High-frequency tracker & telemetry domains
        val defaultBlocklist = listOf(
            // Google Ads / Analytics
            "google-analytics.com",
            "googletagmanager.com",
            "googletagservices.com",
            "doubleclick.net",
            "pagead2.googlesyndication.com",
            "adservice.google.com",
            // Facebook / Meta
            "pixel.facebook.com",
            "an.facebook.com",
            "graph.facebook.com",
            // Ad Networks & Retargeting
            "criteo.com",
            "criteo.net",
            "taboola.com",
            "outbrain.com",
            "rubiconproject.com",
            "adnxs.com",
            "scorecardresearch.com",
            "quantserve.com",
            "chartbeat.com",
            "hotjar.com",
            "fullstory.com",
            "mixpanel.com",
            "segment.io",
            "branch.io",
            "appsflyer.com",
            "adjust.com",
            "advertising.com",
            "adroll.com",
            "pubmatic.com",
            "openx.net",
            "casalemedia.com",
            "revcontent.com",
            "yieldmo.com",
            "smartadserver.com",
            "moatads.com",
            "amazon-adsystem.com"
        )
        for (item in defaultBlocklist) {
            blockedDomains.add(item.lowercase())
        }
    }

    private val allowlistDomains = setOf(
        "googlevideo.com",
        "ytimg.com",
        "youtube.com",
        "youtube-nocookie.com",
        "youtu.be"
    )

    private val shieldDisabledHosts = HashSet<String>()

    /**
     * Checks if the given URL host matches any known tracker or telemetry domain.
     * Enforces media allowlist, per-site shield exemptions, and first-party allowlist.
     */
    fun shouldBlockUrl(url: String?, documentHost: String? = null): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            return false
        }
        val host = uri.host?.lowercase() ?: return false

        // Check if shield is disabled for this document host
        if (!documentHost.isNullOrBlank()) {
            val cleanDocHost = documentHost.lowercase().trim()
            if (isShieldDisabled(cleanDocHost)) {
                return false
            }

            // First-party allowlist: never block requests sharing registrable domain with top-level page
            if (isSameRegistrableDomain(host, cleanDocHost)) {
                return false
            }
        }

        // Media allowlist: never block YouTube / Google Video assets
        if (isAllowlistedMediaDomain(host)) {
            return false
        }

        return isHostBlocked(host)
    }

    /**
     * Checks if host matches allowlisted media streaming domains.
     */
    fun isAllowlistedMediaDomain(host: String): Boolean {
        val lower = host.lowercase().trim()
        for (domain in allowlistDomains) {
            if (lower == domain || lower.endsWith(".$domain")) {
                return true
            }
        }
        return false
    }

    /**
     * Computes or checks if two hosts share the same registrable domain (eTLD+1).
     */
    fun isSameRegistrableDomain(host1: String, host2: String): Boolean {
        val reg1 = getRegistrableDomain(host1)
        val reg2 = getRegistrableDomain(host2)
        return reg1.isNotBlank() && reg1 == reg2
    }

    fun getRegistrableDomain(host: String): String {
        val clean = host.lowercase().trim().removePrefix("www.")
        val parts = clean.split(".")
        if (parts.size <= 2) return clean

        // Common two-level ccTLD check (.com.bd, .co.uk, .org.uk, .com.au, etc.)
        val lastTwo = "${parts[parts.size - 2]}.${parts.last()}"
        val twoLevelTlds = setOf("com.bd", "org.bd", "gov.bd", "edu.bd", "co.uk", "org.uk", "gov.uk", "com.au", "net.au", "co.nz", "co.jp")
        return if (twoLevelTlds.contains(lastTwo) && parts.size >= 3) {
            "${parts[parts.size - 3]}.$lastTwo"
        } else {
            "${parts[parts.size - 2]}.${parts.last()}"
        }
    }

    fun isShieldDisabled(host: String): Boolean {
        val reg = getRegistrableDomain(host)
        return shieldDisabledHosts.contains(host) || shieldDisabledHosts.contains(reg)
    }

    fun setShieldDisabled(host: String, disabled: Boolean) {
        val clean = host.lowercase().trim()
        val reg = getRegistrableDomain(clean)
        if (disabled) {
            shieldDisabledHosts.add(clean)
            if (reg.isNotBlank()) shieldDisabledHosts.add(reg)
        } else {
            shieldDisabledHosts.remove(clean)
            shieldDisabledHosts.remove(reg)
        }
    }

    /**
     * Checks if host or any parent domain is on the blocklist.
     */
    fun isHostBlocked(host: String): Boolean {
        var current: String? = host
        while (!current.isNullOrEmpty()) {
            if (blockedDomains.contains(current)) {
                return true
            }
            val nextDot = current.indexOf('.')
            current = if (nextDot != -1 && nextDot < current.length - 1) {
                current.substring(nextDot + 1)
            } else {
                null
            }
        }
        return false
    }

    fun addBlockedDomain(domain: String) {
        blockedDomains.add(domain.lowercase().trim())
    }

    fun getBlocklistSize(): Int = blockedDomains.size
}
