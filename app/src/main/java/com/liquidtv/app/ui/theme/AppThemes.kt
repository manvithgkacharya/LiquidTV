package com.liquidtv.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Atmosphere slugs ported verbatim from SONIX (`public.app_themes.atmosphere`).
 */
enum class ThemeAtmosphere(val slug: String) {
    ROYAL_NAVY("royal-navy"),
    CELESTIAL_CYAN("celestial-cyan"),
    RUBY_VELVET("ruby-velvet"),
    PEARL_MARBLE("pearl-marble"),
    EMERALD_PALACE("emerald-palace"),
    MIDNIGHT_SAPPHIRE("midnight-sapphire"),
    CARRARA_MARBLE("carrara-marble"),
    IMPERIAL_AMETHYST("imperial-amethyst"),
    IMPERIAL_BRONZE("imperial-bronze"),
    BLACK_ONYX("black-onyx"),
}

/**
 * Complete color palette for a LiquidTV global theme, with derived high-contrast helper tokens.
 */
@Immutable
data class ThemePalette(
    val background: Color,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val card: Color,
    val text: Color,
    val textSecondary: Color,
    val muted: Color,
    val panel: Color,
    val surface: Color,
    val accent: Color,
    val border: Color,
    val focus: Color,
    val selected: Color,
    val danger: Color,
    val success: Color,
    val waveOne: Color,
    val waveTwo: Color,
) {
    val isLight: Boolean
        get() = background.luminance() > 0.45f

    /** High-contrast foreground color rendered on top of filled [accent] surfaces (buttons, pills). */
    val onAccent: Color
        get() = if (accent.luminance() > 0.22f) {
            if (isLight) lerp(text, Color.Black, 0.45f) else background
        } else {
            Color.White
        }

    /** High-contrast foreground color rendered on top of filled [danger] surfaces. */
    val onDanger: Color
        get() = if (danger.luminance() > 0.28f) {
            if (isLight) lerp(text, Color.Black, 0.45f) else background
        } else {
            Color.White
        }

    /** High-contrast foreground color rendered on top of filled [success] surfaces. */
    val onSuccess: Color
        get() = if (success.luminance() > 0.28f) {
            if (isLight) lerp(text, Color.Black, 0.45f) else background
        } else {
            Color.White
        }

    /** Foreground tint for accent-colored text on [card]/[surface]/[panel] so small text stays crisp on light themes. */
    val accentText: Color
        get() = if (isLight) lerp(text, accent, 0.48f) else accent

    /** Secondary accent used for subtle dual-stop theme gradients. */
    val accentSecondary: Color
        get() = waveTwo

    /** Soft focus glow tint derived from [focus] and [accent]. */
    val focusGlow: Color
        get() = focus

    /** Harmonized warning amber shifted for contrast on light vs dark surfaces. */
    val warning: Color
        get() = if (isLight) Color(0xFFB5690A) else Color(0xFFFFB547)

    /** Overlay scrim color for modal dialogs and bottom sheets. */
    val dialogScrim: Color
        get() = Color.Black.copy(alpha = if (isLight) 0.48f else 0.68f)
}

@Immutable
data class ThemeDefinition(
    val id: String,
    val arabicName: String,
    val englishName: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val isDefault: Boolean,
    val atmosphere: ThemeAtmosphere,
    val palette: ThemePalette,
) {
    val isLight: Boolean
        get() = palette.isLight
}

/**
 * Curated registry of ten premium dark Liquid Glass themes. Stable IDs are retained so saved user selections continue to resolve.
 */
object AppThemeCatalog {

    const val DEFAULT_THEME_ID = "royal-navy"

    val RoyalNavy = ThemeDefinition(
        id = "royal-navy",
        arabicName = "الياقوت الملكي",
        englishName = "Royal Sapphire",
        sortOrder = 10,
        isActive = true,
        isDefault = true,
        atmosphere = ThemeAtmosphere.ROYAL_NAVY,
        palette = ThemePalette(
            background = Color(0xFF050B18),
            backgroundTop = Color(0xFF10264A),
            backgroundBottom = Color(0xFF183D65),
            card = Color(0xFF10223B),
            text = Color(0xFFF4F8FF),
            textSecondary = Color(0xFFC7D5E8),
            muted = Color(0xFF8EA5C1),
            panel = Color(0xFF09182B),
            surface = Color(0xFF102B45),
            accent = Color(0xFF5B9DFF),
            border = Color(0xFF385A82),
            focus = Color(0xFFA8D1FF),
            selected = Color(0xFF173A60),
            danger = Color(0xFFF07887),
            success = Color(0xFF59D6A0),
            waveOne = Color(0xFF2867A6),
            waveTwo = Color(0xFF4B83C8),
        ),
    )

    val CelestialCyan = ThemeDefinition(
        id = "celestial-cyan",
        arabicName = "الأطلسي العميق",
        englishName = "Atlantic Noir",
        sortOrder = 14,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.CELESTIAL_CYAN,
        palette = ThemePalette(
            background = Color(0xFF041116),
            backgroundTop = Color(0xFF0B3039),
            backgroundBottom = Color(0xFF12515A),
            card = Color(0xFF0D2931),
            text = Color(0xFFF2FEFF),
            textSecondary = Color(0xFFC0E5E8),
            muted = Color(0xFF82B7BE),
            panel = Color(0xFF071D24),
            surface = Color(0xFF0D333C),
            accent = Color(0xFF36C6C9),
            border = Color(0xFF286A73),
            focus = Color(0xFFA1F0EE),
            selected = Color(0xFF14505B),
            danger = Color(0xFFFF7E91),
            success = Color(0xFF65D8AE),
            waveOne = Color(0xFF0D8796),
            waveTwo = Color(0xFF2FB4B9),
        ),
    )

    val RubyVelvet = ThemeDefinition(
        id = "ruby-velvet",
        arabicName = "الياقوت الأحمر",
        englishName = "Ruby Royale",
        sortOrder = 13,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.RUBY_VELVET,
        palette = ThemePalette(
            background = Color(0xFF10070B),
            backgroundTop = Color(0xFF351321),
            backgroundBottom = Color(0xFF5A2035),
            card = Color(0xFF2A111E),
            text = Color(0xFFFFF5F6),
            textSecondary = Color(0xFFE8CDD4),
            muted = Color(0xFFB895A2),
            panel = Color(0xFF1B0C14),
            surface = Color(0xFF321524),
            accent = Color(0xFFF06A82),
            border = Color(0xFF71354B),
            focus = Color(0xFFFFC0CE),
            selected = Color(0xFF4B1C30),
            danger = Color(0xFFFF7187),
            success = Color(0xFF72CEA2),
            waveOne = Color(0xFFB83C61),
            waveTwo = Color(0xFFE05B79),
        ),
    )

    val PearlMarble = ThemeDefinition(
        id = "pearl-marble",
        arabicName = "الذهبي الوردي الليلي",
        englishName = "Rose Gold Noir",
        sortOrder = 19,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.PEARL_MARBLE,
        palette = ThemePalette(
            background = Color(0xFF10090D),
            backgroundTop = Color(0xFF35202A),
            backgroundBottom = Color(0xFF53333D),
            card = Color(0xFF2A1922),
            text = Color(0xFFFFF5F5),
            textSecondary = Color(0xFFE8CDD4),
            muted = Color(0xFFB895A2),
            panel = Color(0xFF1A1017),
            surface = Color(0xFF35212B),
            accent = Color(0xFFE6A4A0),
            border = Color(0xFF754D5A),
            focus = Color(0xFFF8D0C9),
            selected = Color(0xFF4C2E3A),
            danger = Color(0xFFFF7187),
            success = Color(0xFF72CEA2),
            waveOne = Color(0xFFB86E78),
            waveTwo = Color(0xFFD58D91),
        ),
    )

    val EmeraldPalace = ThemeDefinition(
        id = "emerald-palace",
        arabicName = "الزمرد الفاخر",
        englishName = "Emerald Prestige",
        sortOrder = 11,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.EMERALD_PALACE,
        palette = ThemePalette(
            background = Color(0xFF04120F),
            backgroundTop = Color(0xFF10372C),
            backgroundBottom = Color(0xFF1A5945),
            card = Color(0xFF0E2A20),
            text = Color(0xFFF4FBF7),
            textSecondary = Color(0xFFC7E7D9),
            muted = Color(0xFF8EAA98),
            panel = Color(0xFF081D17),
            surface = Color(0xFF103326),
            accent = Color(0xFF36D6A0),
            border = Color(0xFF356C59),
            focus = Color(0xFFA0F0D0),
            selected = Color(0xFF174735),
            danger = Color(0xFFE8797D),
            success = Color(0xFF66D49A),
            waveOne = Color(0xFF1C8A64),
            waveTwo = Color(0xFF4BBE91),
        ),
    )

    val MidnightSapphire = ThemeDefinition(
        id = "midnight-sapphire",
        arabicName = "النيلي الكهربائي",
        englishName = "Electric Indigo",
        sortOrder = 18,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.MIDNIGHT_SAPPHIRE,
        palette = ThemePalette(
            background = Color(0xFF07091B),
            backgroundTop = Color(0xFF171D4B),
            backgroundBottom = Color(0xFF293A75),
            card = Color(0xFF171C3A),
            text = Color(0xFFF5F6FF),
            textSecondary = Color(0xFFCDD2F4),
            muted = Color(0xFF969FC9),
            panel = Color(0xFF10142D),
            surface = Color(0xFF1D2550),
            accent = Color(0xFF7185FF),
            border = Color(0xFF4653A0),
            focus = Color(0xFFC0C8FF),
            selected = Color(0xFF2D3973),
            danger = Color(0xFFEF7889),
            success = Color(0xFF68D2A2),
            waveOne = Color(0xFF4659CC),
            waveTwo = Color(0xFF7788F0),
        ),
    )

    val CarraraMarble = ThemeDefinition(
        id = "carrara-marble",
        arabicName = "التيتانيوم الفاخر",
        englishName = "Titanium Luxe",
        sortOrder = 16,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.CARRARA_MARBLE,
        palette = ThemePalette(
            background = Color(0xFF090C11),
            backgroundTop = Color(0xFF202A36),
            backgroundBottom = Color(0xFF3B4655),
            card = Color(0xFF1B232D),
            text = Color(0xFFF7F8FA),
            textSecondary = Color(0xFFCED3DA),
            muted = Color(0xFF929DA9),
            panel = Color(0xFF111821),
            surface = Color(0xFF222D39),
            accent = Color(0xFFB9C8D9),
            border = Color(0xFF566579),
            focus = Color(0xFFE5EDF5),
            selected = Color(0xFF344456),
            danger = Color(0xFFE77A83),
            success = Color(0xFF72C79D),
            waveOne = Color(0xFF6E849C),
            waveTwo = Color(0xFFA3B4C7),
        ),
    )

    val ImperialAmethyst = ThemeDefinition(
        id = "imperial-amethyst",
        arabicName = "الجمشت الفاخر",
        englishName = "Amethyst Elite",
        sortOrder = 15,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.IMPERIAL_AMETHYST,
        palette = ThemePalette(
            background = Color(0xFF0B0714),
            backgroundTop = Color(0xFF28163E),
            backgroundBottom = Color(0xFF48265F),
            card = Color(0xFF241532),
            text = Color(0xFFFBF6FF),
            textSecondary = Color(0xFFDCCCE8),
            muted = Color(0xFFA998B9),
            panel = Color(0xFF170D24),
            surface = Color(0xFF2C1A3D),
            accent = Color(0xFFB58AFF),
            border = Color(0xFF60447A),
            focus = Color(0xFFE0C9FF),
            selected = Color(0xFF41265A),
            danger = Color(0xFFF07E98),
            success = Color(0xFF6DCDA5),
            waveOne = Color(0xFF7847B0),
            waveTwo = Color(0xFFA36BD2),
        ),
    )

    val ImperialBronze = ThemeDefinition(
        id = "imperial-bronze",
        arabicName = "النحاس الإمبراطوري",
        englishName = "Copper Imperial",
        sortOrder = 17,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.IMPERIAL_BRONZE,
        palette = ThemePalette(
            background = Color(0xFF100A08),
            backgroundTop = Color(0xFF382016),
            backgroundBottom = Color(0xFF60351F),
            card = Color(0xFF2B1A12),
            text = Color(0xFFFFF8F2),
            textSecondary = Color(0xFFE5D2BB),
            muted = Color(0xFFB29A7F),
            panel = Color(0xFF1B100B),
            surface = Color(0xFF362015),
            accent = Color(0xFFE69A62),
            border = Color(0xFF795039),
            focus = Color(0xFFFFD0A8),
            selected = Color(0xFF4C2B19),
            danger = Color(0xFFE97C6F),
            success = Color(0xFF7AC99B),
            waveOne = Color(0xFFA85B31),
            waveTwo = Color(0xFFD07C49),
        ),
    )

    val BlackOnyx = ThemeDefinition(
        id = "black-onyx",
        arabicName = "الذهب الأسود",
        englishName = "Obsidian Gold",
        sortOrder = 12,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.BLACK_ONYX,
        palette = ThemePalette(
            background = Color(0xFF080907),
            backgroundTop = Color(0xFF1C1A14),
            backgroundBottom = Color(0xFF3B3020),
            card = Color(0xFF211D15),
            text = Color(0xFFF8F5EC),
            textSecondary = Color(0xFFD9D1C0),
            muted = Color(0xFFA79A80),
            panel = Color(0xFF12110D),
            surface = Color(0xFF211C14),
            accent = Color(0xFFD9B36C),
            border = Color(0xFF665438),
            focus = Color(0xFFF0D8A4),
            selected = Color(0xFF3A2E1C),
            danger = Color(0xFFE97C6F),
            success = Color(0xFF7AC99B),
            waveOne = Color(0xFF9D783D),
            waveTwo = Color(0xFFD9B36C),
        ),
    )

    val allThemes: List<ThemeDefinition> = listOf(
        RoyalNavy,
        EmeraldPalace,
        BlackOnyx,
        RubyVelvet,
        CelestialCyan,
        ImperialAmethyst,
        CarraraMarble,
        ImperialBronze,
        MidnightSapphire,
        PearlMarble,
    ).filter { it.isActive }.sortedBy { it.sortOrder }

    val defaultTheme: ThemeDefinition = RoyalNavy

    private val byId: Map<String, ThemeDefinition> = allThemes.associateBy { it.id }

    /**
     * Resolves a stored theme ID (or atmosphere slug) into a valid [ThemeDefinition],
     * falling back safely to [RoyalNavy] if null, blank, unknown, or corrupted.
     */
    fun resolve(themeId: String?): ThemeDefinition {
        if (themeId.isNullOrBlank()) return defaultTheme
        val normalized = themeId.trim().lowercase()
        return byId[normalized] ?: defaultTheme
    }
}
