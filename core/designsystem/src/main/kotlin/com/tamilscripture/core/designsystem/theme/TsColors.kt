package com.tamilscripture.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colour tokens. Dark values are the "night slate" palette from the Claude Design
 * handoff (Tamil Bible Android.dc.html); light values are the website's "warm paper"
 * tokens (apps/web/src/app.css). Screens read these, never raw hex.
 */
@Immutable
data class TsColors(
    val isDark: Boolean,
    /** Page ground. */
    val bg: Color,
    /** Raised cards, inputs, sheets. */
    val surface: Color,
    /** Bars (top, bottom, player), flat cards, the selected-row tint. */
    val surface2: Color,
    /** The commentary / lexicon column on wide windows. */
    val pane: Color,
    /** Hairline dividers. */
    val line: Color,
    /** Control borders. */
    val line2: Color,
    /** Sheet handles, unchecked radio rings. */
    val lineStrong: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    /** Disabled numbers, future calendar days. */
    val faint: Color,
    /** The one accent: gold at night, deep blue by day. */
    val accent: Color,
    val onAccent: Color,
    /** Second reference colour: section headings, streaks, the current stop. */
    val amber: Color,
    /** Profile avatar. */
    val brand: Color,
    val onBrand: Color,
    val hlYellow: Color,
    val hlGreen: Color,
    val hlBlue: Color,
    val hlPink: Color,
    val mapSea: Color,
    val mapLand: Color,
    val mapLake: Color,
    val mapCoast: Color,
    val mapRiver: Color,
    val scrim: Color,
) {
    /** Selected verse background: gold at 14% at night (design), the website's warm --hl by day. */
    val verseSelected: Color get() = if (isDark) accent.copy(alpha = 0.14f) else Color(0xFFFBEFD3)
    /** Chip / badge tint (accent at 18%). */
    val accentSoft: Color get() = accent.copy(alpha = if (isDark) 0.18f else 0.12f)
    /** Navigation pill indicator (accent at 20%). */
    val navIndicator: Color get() = accent.copy(alpha = if (isDark) 0.20f else 0.14f)
    /** Very light accent wash for a focused verse block (8%). */
    val accentWash: Color get() = accent.copy(alpha = 0.08f)
    val amberWash: Color get() = amber.copy(alpha = if (isDark) 0.08f else 0.10f)
}

val DarkTsColors = TsColors(
    isDark = true,
    bg = Color(0xFF1B1D22),
    surface = Color(0xFF262930),
    surface2 = Color(0xFF20232A),
    pane = Color(0xFF1F2228),
    line = Color(0xFF2E323B),
    line2 = Color(0xFF3A3F4A),
    lineStrong = Color(0xFF4A505C),
    ink = Color(0xFFF1ECE1),
    ink2 = Color(0xFFC4BEB1),
    muted = Color(0xFFB0AA9D),
    faint = Color(0xFF6B6259),
    accent = Color(0xFFD9B25C),
    onAccent = Color(0xFF1B1D22),
    amber = Color(0xFFE39A5F),
    brand = Color(0xFF2B5B8C),
    onBrand = Color(0xFFF1ECE1),
    hlYellow = Color(0xFF6E6224),
    hlGreen = Color(0xFF2F4F2C),
    hlBlue = Color(0xFF29405A),
    hlPink = Color(0xFF5A2E3C),
    mapSea = Color(0xFF1E2A33),
    mapLand = Color(0xFF2A3B3A),
    mapLake = Color(0xFF1A2430),
    mapCoast = Color(0xFF3D4554),
    mapRiver = Color(0xFF3E5D78),
    scrim = Color(0x80000000),
)

val LightTsColors = TsColors(
    isDark = false,
    bg = Color(0xFFFBF7F0),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF3EDE2),
    pane = Color(0xFFF7F2E8),
    line = Color(0xFFE5DCCF),
    line2 = Color(0xFFDED3C4),
    lineStrong = Color(0xFFC9BFB1),
    ink = Color(0xFF191512),
    ink2 = Color(0xFF57504A),
    muted = Color(0xFF6B6259),
    faint = Color(0xFFA39A8F),
    accent = Color(0xFF2B5B8C),
    onAccent = Color(0xFFFFFFFF),
    amber = Color(0xFFA2600F),
    brand = Color(0xFF2B5B8C),
    onBrand = Color(0xFFFFFFFF),
    hlYellow = Color(0xFFF9E7A6),
    hlGreen = Color(0xFFCFE6C2),
    hlBlue = Color(0xFFC9DCF2),
    hlPink = Color(0xFFF4CBD8),
    mapSea = Color(0xFFDCE6EC),
    mapLand = Color(0xFFF1EBDF),
    mapLake = Color(0xFFC9D9E4),
    mapCoast = Color(0xFFC9BFB1),
    mapRiver = Color(0xFF9FBFDA),
    scrim = Color(0x47000000),
)

val LocalTsColors = staticCompositionLocalOf { DarkTsColors }
