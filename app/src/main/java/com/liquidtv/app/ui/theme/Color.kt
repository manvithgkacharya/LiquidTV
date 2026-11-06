package com.liquidtv.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Core palette ──────────────────────────────────────────────────────────────
object LiquidColors {

    // Background depths
    val DeepBlack     = Color(0xFF000000)
    val MidnightNavy  = Color(0xFF060A12)   // richer near-black with blue tint
    val DarkSurface   = Color(0xFF0D1117)
    val DeepSurface   = Color(0xFF111620)

    // Glass surfaces (with alpha)
    val GlassPrimary    = Color(0x2EFFFFFF)   // 18% white — more visible
    val GlassSecondary  = Color(0x1FFFFFFF)   // 12% white
    val GlassTertiary   = Color(0x12FFFFFF)   // 7% white
    val GlassDark       = Color(0x40000000)   // 25% black
    val GlassDarker     = Color(0x59000000)   // 35% black

    // Glass borders
    val GlassBorder         = Color(0x40FFFFFF)  // 25% white — more visible
    val GlassBorderFocused  = Color(0x99FFFFFF)  // 60% white
    val GlassBorderSubtle   = Color(0x1AFFFFFF)  // 10% white

    // Specular highlights
    val HighlightBright  = Color(0x99FFFFFF)  // 60% white — more vivid
    val HighlightSoft    = Color(0x40FFFFFF)  // 25% white
    val HighlightFaint   = Color(0x1AFFFFFF)  // 10% white

    // Accent colors
    val AccentBlue        = Color(0xFF3D9BE9)   // Cool electric blue
    val AccentBlueLight   = Color(0xFF5BB8FF)
    val AccentBlueDark    = Color(0xFF2272C3)
    val AccentViolet      = Color(0xFF8B5CF6)
    val AccentVioletLight = Color(0xFFA78BFA)
    val AccentCyan        = Color(0xFF06B6D4)
    val AccentTeal        = Color(0xFF14B8A6)

    // Additional rich accents
    val AccentGold        = Color(0xFFFFBB33)   // warm gold
    val AccentRose        = Color(0xFFFF2D55)   // vivid rose/red
    val AccentOrange      = Color(0xFFFF6B35)   // vibrant orange
    val AccentGreen       = Color(0xFF30D158)   // vivid green
    val AccentPink        = Color(0xFFFF375F)   // hot pink
    val AccentIndigo      = Color(0xFF5856D6)   // deep indigo

    // Category accent colors (gradient start → end)
    val CategorySports    = Color(0xFFFF6B35)
    val CategorySportsEnd = Color(0xFFFF3B30)
    val CategoryNews      = Color(0xFF06B6D4)
    val CategoryNewsEnd   = Color(0xFF2272C3)
    val CategoryMovies    = Color(0xFF8B5CF6)
    val CategoryMoviesEnd = Color(0xFF5856D6)
    val CategoryMusic     = Color(0xFFFF375F)
    val CategoryMusicEnd  = Color(0xFFBF5AF2)
    val CategoryKids      = Color(0xFFFFBB33)
    val CategoryKidsEnd   = Color(0xFFFF9F0A)
    val CategoryDoc       = Color(0xFF30D158)
    val CategoryDocEnd    = Color(0xFF34C759)
    val CategoryEnt       = Color(0xFF5BB8FF)
    val CategoryEntEnd    = Color(0xFF8B5CF6)

    // Glow colors (for focus effects)
    val GlowBlueStrong    = Color(0x663D9BE9)
    val GlowVioletStrong  = Color(0x668B5CF6)
    val GlowCyan          = Color(0x4406B6D4)

    // Live / status
    val LiveRed         = Color(0xFFFF3B30)
    val LiveRedSoft     = Color(0x99FF3B30)
    val SuccessGreen    = Color(0xFF34C759)
    val WarningAmber    = Color(0xFFFF9F0A)
    val ErrorRed        = Color(0xFFFF453A)

    // Text
    val TextPrimary     = Color(0xFFFFFFFF)
    val TextSecondary   = Color(0xCCFFFFFF)  // 80% — more readable
    val TextTertiary    = Color(0x99FFFFFF)  // 60% — more readable
    val TextDisabled    = Color(0x4DFFFFFF)  // 30%
    val TextAccent      = AccentBlue

    // Icon
    val IconActive      = Color(0xFFFFFFFF)
    val IconInactive    = Color(0x99FFFFFF)  // slightly more visible

    // Shadow / glow
    val ShadowDeep      = Color(0xCC000000)
    val GlowBlue        = Color(0x4D3D9BE9)
    val GlowViolet      = Color(0x4D8B5CF6)

    // Ambient themes
    val AmbientMidnightStart  = Color(0xFF050810)
    val AmbientAuroraStart    = Color(0xFF0D1B2A)
    val AmbientAuroraGreen    = Color(0xFF00A878)
    val AmbientOceanStart     = Color(0xFF0A1628)
    val AmbientOceanBlue      = Color(0xFF0077B6)
    val AmbientCosmicStart    = Color(0xFF0A0015)
    val AmbientCosmicPurple   = Color(0xFF6D28D9)
    val AmbientMinimalStart   = Color(0xFF0F0F0F)
}

