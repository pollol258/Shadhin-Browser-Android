package com.shadhin.browser.network

enum class DohProvider(
    val displayName: String,
    val endpointUrl: String,
    val primaryIp: String,
    val secondaryIp: String
) {
    CLOUDFLARE(
        displayName = "Cloudflare (1.1.1.1)",
        endpointUrl = "https://cloudflare-dns.com/dns-query",
        primaryIp = "1.1.1.1",
        secondaryIp = "1.0.0.1"
    ),
    QUAD9(
        displayName = "Quad9 (9.9.9.9)",
        endpointUrl = "https://dns.quad9.net/dns-query",
        primaryIp = "9.9.9.9",
        secondaryIp = "149.112.112.112"
    ),
    OFF(
        displayName = "System Direct (Off)",
        endpointUrl = "",
        primaryIp = "",
        secondaryIp = ""
    )
}
