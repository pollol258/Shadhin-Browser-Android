package com.shadhin.browser.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CosmeticFilterEngineTest {

    private lateinit var engine: CosmeticFilterEngine

    @Before
    fun setUp() {
        engine = CosmeticFilterEngine()
    }

    @Test
    fun `test global selectors parsing and retrieval`() {
        engine.parseRule("##.custom-ad-overlay")
        val selectors = engine.getSelectorsForDomain("anydomain.com")
        assertTrue(selectors.contains(".custom-ad-overlay"))
    }

    @Test
    fun `test domain specific selectors`() {
        engine.parseRule("nytimes.com##.paywall-modal")
        val nytSelectors = engine.getSelectorsForDomain("nytimes.com")
        assertTrue(nytSelectors.contains(".paywall-modal"))

        val otherSelectors = engine.getSelectorsForDomain("bbc.com")
        assertFalse(otherSelectors.contains(".paywall-modal"))
    }

    @Test
    fun `test domain negation syntax`() {
        engine.parseRule("~exception.com##.global-cookie-banner")
        engine.parseRule("##.global-cookie-banner")

        val standardSelectors = engine.getSelectorsForDomain("standard.org")
        assertTrue(standardSelectors.contains(".global-cookie-banner"))

        val exceptionSelectors = engine.getSelectorsForDomain("exception.com")
        assertFalse(exceptionSelectors.contains(".global-cookie-banner"))
    }

    @Test
    fun `test minified CSS output contains display none and important`() {
        val css = engine.generateMinifiedCss(listOf(".banner", "#popup"))
        assertEquals(".banner,#popup{display:none!important;visibility:hidden!important;height:0!important;min-height:0!important;opacity:0!important;pointer-events:none!important;}", css)
    }

    @Test
    fun `test injection script generation includes MutationObserver`() {
        val script = engine.buildInjectionScript("test.com")
        assertTrue(script.contains("__shadhin_cosmetic_rules__"))
        assertTrue(script.contains("MutationObserver"))
    }
}
