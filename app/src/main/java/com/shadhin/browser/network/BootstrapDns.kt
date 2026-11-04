package com.shadhin.browser.network

import okhttp3.Dns
import java.net.InetAddress

/**
 * Bootstrap DNS resolver preventing DNS leaks when contacting DoH endpoints.
 * Returns hardcoded IPs for Cloudflare and Quad9 domains.
 */
class BootstrapDns : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val lower = hostname.lowercase().trim()
        return when (lower) {
            "cloudflare-dns.com", "one.one.one.one" -> listOf(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1"),
                InetAddress.getByName("2606:4700:4700::1111"),
                InetAddress.getByName("2606:4700:4700::1001")
            )
            "dns.quad9.net" -> listOf(
                InetAddress.getByName("9.9.9.9"),
                InetAddress.getByName("149.112.112.112"),
                InetAddress.getByName("2620:fe::fe"),
                InetAddress.getByName("2620:fe::9")
            )
            else -> Dns.SYSTEM.lookup(hostname)
        }
    }
}
