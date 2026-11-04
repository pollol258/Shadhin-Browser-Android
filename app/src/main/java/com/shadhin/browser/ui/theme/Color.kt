package com.shadhin.browser.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for the "Warm Glass" design system.
 * Palette: Dark Warm Cocoa/Terracotta gradient base, Cream #FFF3EA light base,
 * Warm Capsule fill alpha 88%, Accent Orange #F28C4E, Soft White #FFF8F3 active chip fill.
 */

// Core Primary Accent
val AccentOrange = Color(0xFFF28C4E) // #F28C4E only for toggles, progress, shield count, primary actions

// Active Chip / Active Tab Tokens
val ActiveTabBackground = Color(0xFFFFF8F3) // Solid soft white
val ActiveTabText = Color(0xFF2C1608) // Dark warm brown for strong contrast

// Dark Warm Theme (Cocoa / Terracotta)
val DarkBackgroundStart = Color(0xFF18100C) // Deep espresso / cocoa
val DarkBackgroundEnd = Color(0xFF241510) // Warm terracotta / burnt umber tone
val DarkSurface = Color(0xFF261914) // Warm glass card surface
val DarkSurfaceHigh = Color(0xFF35231C) // Higher elevation warm card
val DarkCapsuleFill = Color(0xE01E130E) // 88% alpha (0xE0) dark warm capsule fill
val DarkCapsuleBorder = Color(0xFF452C22) // Hairline border
val DarkTextPrimary = Color(0xFFFFF4EE) // Cream white text
val DarkTextSecondary = Color(0xFFB59E92) // Muted warm taupe text

// Light Warm Theme (Cream #FFF3EA)
val LightBackgroundStart = Color(0xFFFFF3EA) // Cream base
val LightBackgroundEnd = Color(0xFFFCE6D6) // Soft apricot cream
val LightSurface = Color(0xFFFFFFFF) // Crisp translucent warm card
val LightSurfaceHigh = Color(0xFFF7E7DC) // Elevated warm card
val LightCapsuleFill = Color(0xE0FFFFFF) // 88% alpha (0xE0) white warm glass capsule
val LightCapsuleBorder = Color(0xFFE5D0C2) // Hairline border
val LightTextPrimary = Color(0xFF2B1911) // Dark espresso text
val LightTextSecondary = Color(0xFF7D6559) // Warm terracotta brown secondary

// Legacy mappings for backward compatibility
val Background = DarkBackgroundStart
val Surface = DarkSurface
val SurfaceHigh = DarkSurfaceHigh
val CapsuleFill = DarkCapsuleFill
val CapsuleBorder = DarkCapsuleBorder
val TextPrimary = DarkTextPrimary
val TextSecondary = DarkTextSecondary
val Accent = AccentOrange

// Functional Status
val StatusError = Color(0xFFE54D42)
val StatusSuccess = Color(0xFF48BB78)
val StatusWarning = Color(0xFFED8936)
