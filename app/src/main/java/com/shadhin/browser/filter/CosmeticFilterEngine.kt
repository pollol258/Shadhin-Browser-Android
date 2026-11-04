package com.shadhin.browser.filter

/**
 * High-performance in-memory cosmetic filter parser and generator supporting
 * Adblock Plus and uBlock Origin element-hiding syntax.
 *
 * Supported patterns:
 *  - Global: `##.ad-banner`, `###ad-container`, `##div[id^="google_ads_"]`
 *  - Domain-specific: `example.com##.sponsor-post`, `news.com,blog.org##.native-ad`
 *  - Domain-negation: `~exception.com##.global-ad`
 */
class CosmeticFilterEngine {

    private val universalRules = mutableListOf<String>()
    private val domainRules = mutableMapOf<String, MutableList<String>>()
    private val negatedDomainRules = mutableMapOf<String, MutableList<String>>()

    init {
        // Seed with high-impact universal ad & tracker selectors
        val defaultRules = listOf(
            "##.ad-banner",
            "##.ad-container",
            "###ad-container",
            "###google_top_ad",
            "##div[id^=\"google_ads_\"]",
            "##div[id^=\"aswift_\"]",
            "##ins.adsbygoogle",
            "##.sponsor-post",
            "##.sponsored-content",
            "##.taboola-ad",
            "##.outbrain-ad",
            "##[data-ad-slot]",
            "##[data-ad-unit]",
            "##.native-ad",
            "##.sponsored-badge",
            "##iframe[src*=\"doubleclick.net\"]",
            "##iframe[src*=\"googlesyndication.com\"]"
        )
        parseRules(defaultRules)
    }

    /**
     * Parses a list of raw rule lines.
     */
    fun parseRules(lines: List<String>) {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("[")) {
                continue // Skip empty lines and comments
            }
            parseRule(trimmed)
        }
    }

    /**
     * Parses a single rule line.
     */
    fun parseRule(rawRule: String) {
        val markerIndex = rawRule.indexOf("##")
        if (markerIndex == -1) return

        val selector = rawRule.substring(markerIndex + 2).trim()
        if (selector.isEmpty() || !isValidSelector(selector)) return

        if (markerIndex == 0) {
            // Global selector
            if (!universalRules.contains(selector)) {
                universalRules.add(selector)
            }
        } else {
            val domainPart = rawRule.substring(0, markerIndex).trim()
            val domains = domainPart.split(",")
            for (rawDomain in domains) {
                val d = rawDomain.trim().lowercase()
                if (d.isEmpty()) continue
                if (d.startsWith("~")) {
                    val actualDomain = d.removePrefix("~")
                    negatedDomainRules.getOrPut(actualDomain) { mutableListOf() }.add(selector)
                } else {
                    domainRules.getOrPut(d) { mutableListOf() }.add(selector)
                }
            }
        }
    }

    /**
     * Retrieves all matching CSS selectors for the given domain.
     * Evaluates universal rules (excluding those negated for this domain)
     * plus domain-specific selectors (including parent domains).
     */
    fun getSelectorsForDomain(domain: String?): List<String> {
        val result = mutableSetOf<String>()
        val normalizedDomain = domain?.trim()?.lowercase()?.removePrefix("www.")

        // Allowlist YouTube and video streaming domains to ensure video elements are never broken
        if (normalizedDomain != null && (
            normalizedDomain == "youtube.com" || normalizedDomain.endsWith(".youtube.com") ||
            normalizedDomain == "googlevideo.com" || normalizedDomain.endsWith(".googlevideo.com") ||
            normalizedDomain == "ytimg.com" || normalizedDomain.endsWith(".ytimg.com") ||
            normalizedDomain == "youtu.be"
        )) {
            return emptyList()
        }

        // Collect universal rules
        for (rule in universalRules) {
            if (normalizedDomain != null && isRuleNegatedForDomain(rule, normalizedDomain)) {
                continue
            }
            result.add(rule)
        }

        // Collect domain-specific rules matching domain or parent domains
        if (!normalizedDomain.isNullOrBlank()) {
            var current: String? = normalizedDomain
            while (!current.isNullOrEmpty()) {
                domainRules[current]?.let { result.addAll(it) }
                val nextDot = current.indexOf('.')
                current = if (nextDot != -1 && nextDot < current.length - 1) {
                    current.substring(nextDot + 1)
                } else {
                    null
                }
            }
        }

        return result.toList()
    }

    private fun isRuleNegatedForDomain(rule: String, domain: String): Boolean {
        var current: String? = domain
        while (!current.isNullOrEmpty()) {
            if (negatedDomainRules[current]?.contains(rule) == true) {
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

    /**
     * Generates minified CSS declaration enforcing display: none !important.
     */
    fun generateMinifiedCss(selectors: List<String>): String {
        if (selectors.isEmpty()) return ""
        val joined = selectors.joinToString(",")
        return "$joined{display:none!important;visibility:hidden!important;height:0!important;min-height:0!important;opacity:0!important;pointer-events:none!important;}"
    }

    /**
     * Builds the complete self-invoking JavaScript snippet that injects a persistent
     * style tag into document.head / document.documentElement at document start
     * and sets up a MutationObserver for dynamic single-page applications.
     */
    fun buildInjectionScript(domain: String?): String {
        val selectors = getSelectorsForDomain(domain)
        if (selectors.isEmpty()) return ""

        val css = generateMinifiedCss(selectors)
        val escapedCss = escapeJsString(css)

        return """
            (function() {
                try {
                    var styleId = '__shadhin_cosmetic_rules__';
                    var existing = document.getElementById(styleId);
                    if (!existing) {
                        var style = document.createElement('style');
                        style.id = styleId;
                        style.textContent = '$escapedCss';
                        var target = document.head || document.documentElement;
                        if (target) {
                            target.appendChild(style);
                        } else {
                            document.addEventListener('DOMContentLoaded', function() {
                                (document.head || document.documentElement).appendChild(style);
                            });
                        }
                    }
                    if (window.MutationObserver && !window.__shadhin_observer_active__) {
                        window.__shadhin_observer_active__ = true;
                        var observer = new MutationObserver(function() {
                            var el = document.getElementById(styleId);
                            var parent = document.head || document.documentElement;
                            if (parent && !el) {
                                var s = document.createElement('style');
                                s.id = styleId;
                                s.textContent = '$escapedCss';
                                parent.appendChild(s);
                            }
                        });
                        observer.observe(document.documentElement || document, { childList: true, subtree: true });
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }

    private fun isValidSelector(selector: String): Boolean {
        // Prevent JavaScript string breakout or dangerous CSS injection
        val lower = selector.lowercase()
        return !lower.contains("<script") &&
               !lower.contains("javascript:") &&
               !lower.contains("\n") &&
               !lower.contains("\r")
    }

    private fun escapeJsString(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    fun getUniversalRuleCount(): Int = universalRules.size
    fun getDomainRuleCount(): Int = domainRules.values.sumOf { it.size }
}
