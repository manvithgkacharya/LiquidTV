package com.liquidtv.app.domain.model

import androidx.annotation.FontRes
import com.liquidtv.app.R
import kotlinx.serialization.Serializable

@Serializable
enum class AddonSource {
    DEFAULT,
    OFFICIAL,
    COMMUNITY,
    CUSTOM,
}

@Serializable
data class StremioAddon(
    val id: String,
    val name: String,
    val version: String = "",
    val description: String = "",
    val url: String,
    val types: List<String> = listOf("movie", "series"),
    val resources: List<String> = emptyList(),
    val enabled: Boolean = true,
    val source: AddonSource = AddonSource.COMMUNITY,
    val configurationRequired: Boolean = false,
    val configureUrl: String = "",
    val hasSearchCatalog: Boolean = false,
    val searchCatalogIdsByType: Map<String, List<String>> = emptyMap(),
) {
    fun supportsResource(resourceName: String): Boolean =
        resources.any { it.equals(resourceName, ignoreCase = true) }

    fun baseUrl(): String =
        url.replace(Regex("/manifest\\.json(?:\\?.*)?$", RegexOption.IGNORE_CASE), "")
            .trimEnd('/')
}

data class AddonMarketResult(
    val official: List<StremioAddon>,
    val community: List<StremioAddon>,
    val warningMessage: String? = null,
)

@Serializable
enum class SubtitleFontOption(
    val id: String,
    val displayNameAr: String,
    val englishName: String,
    val actualWeight: Int,
    @FontRes val fontResId: Int,
) {
    NOTO_SANS_ARABIC_BOLD(
        id = "noto_sans_arabic_bold",
        displayNameAr = "نوتو سانس العربي",
        englishName = "Noto Sans Arabic Bold",
        actualWeight = 700,
        fontResId = R.font.sub_noto_sans_arabic_bold,
    ),
    NOTO_KUFI_ARABIC_BOLD(
        id = "noto_kufi_arabic_bold",
        displayNameAr = "نوتو كوفي العربي",
        englishName = "Noto Kufi Arabic Bold",
        actualWeight = 700,
        fontResId = R.font.sub_noto_kufi_arabic_bold,
    ),
    TAJAWAL_EXTRABOLD(
        id = "tajawal_extrabold",
        displayNameAr = "تجول عريض جداً",
        englishName = "Tajawal ExtraBold",
        actualWeight = 800,
        fontResId = R.font.sub_tajawal_extrabold,
    ),
    CAIRO_BOLD(
        id = "cairo_bold",
        displayNameAr = "القاهرة عريض",
        englishName = "Cairo Bold",
        actualWeight = 700,
        fontResId = R.font.sub_cairo_bold,
    ),
    IBM_PLEX_SANS_ARABIC_BOLD(
        id = "ibm_plex_sans_arabic_bold",
        displayNameAr = "آي بي إم بليكس العربي",
        englishName = "IBM Plex Sans Arabic Bold",
        actualWeight = 700,
        fontResId = R.font.sub_ibm_plex_sans_arabic_bold,
    ),
    ALMARAI_EXTRABOLD(
        id = "almarai_extrabold",
        displayNameAr = "المراعي عريض جداً",
        englishName = "Almarai ExtraBold",
        actualWeight = 800,
        fontResId = R.font.sub_almarai_extrabold,
    ),
    READEX_PRO_BOLD(
        id = "readex_pro_bold",
        displayNameAr = "ريدكس برو عريض",
        englishName = "Readex Pro Bold",
        actualWeight = 700,
        fontResId = R.font.sub_readex_pro_bold,
    ),
    ALEXANDRIA_BOLD(
        id = "alexandria_bold",
        displayNameAr = "الإسكندرية عريض",
        englishName = "Alexandria Bold",
        actualWeight = 700,
        fontResId = R.font.sub_alexandria_bold,
    ),
    CHANGA_BOLD(
        id = "changa_bold",
        displayNameAr = "تشانجا عريض",
        englishName = "Changa Bold",
        actualWeight = 700,
        fontResId = R.font.sub_changa_bold,
    ),
    EL_MESSIRI_BOLD(
        id = "el_messiri_bold",
        displayNameAr = "المسيري عريض",
        englishName = "El Messiri Bold",
        actualWeight = 700,
        fontResId = R.font.sub_el_messiri_bold,
    );

    companion object {
        fun fromId(id: String?): SubtitleFontOption =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NOTO_SANS_ARABIC_BOLD
    }
}

@Serializable
enum class SubtitleColorOption(
    val id: String,
    val labelAr: String,
    val argbHex: Long,
) {
    WHITE("white", "أبيض", 0xFFFFFFFF),
    YELLOW("yellow", "أصفر", 0xFFFFFF00),
    CYAN("cyan", "سماوي", 0xFF00FFFF),
    GREEN("green", "أخضر", 0xFF00FF66);

    companion object {
        fun fromId(id: String?): SubtitleColorOption =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: WHITE
    }
}

@Serializable
enum class SubtitleBackgroundOption(
    val id: String,
    val labelAr: String,
) {
    OFF("off", "بدون خلفية"),
    TRANSLUCENT("translucent", "شبه شفافة"),
    SOLID("solid", "سوداء داكنة");

    companion object {
        fun fromId(id: String?): SubtitleBackgroundOption =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: TRANSLUCENT
    }
}

@Serializable
enum class SubtitleEdgeOption(
    val id: String,
    val labelAr: String,
) {
    NONE("none", "بدون حدود"),
    SHADOW("shadow", "ظل خفيف"),
    OUTLINE("outline", "حدود واضحة");

    companion object {
        fun fromId(id: String?): SubtitleEdgeOption =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OUTLINE
    }
}

@Serializable
enum class SubtitlePositionOption(
    val id: String,
    val labelAr: String,
) {
    BOTTOM("bottom", "أسفل الشاشة"),
    RAISED("raised", "مرتفع قليلاً"),
    MIDDLE("middle", "المنتصف");

    companion object {
        fun fromId(id: String?): SubtitlePositionOption =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BOTTOM
    }
}

@Serializable
data class SubtitleStyleSettings(
    val fontId: String = SubtitleFontOption.NOTO_SANS_ARABIC_BOLD.id,
    val fontSizeSp: Int = 24,
    val colorId: String = SubtitleColorOption.WHITE.id,
    val backgroundId: String = SubtitleBackgroundOption.TRANSLUCENT.id,
    val edgeId: String = SubtitleEdgeOption.OUTLINE.id,
    val positionId: String = SubtitlePositionOption.BOTTOM.id,
    val delayMs: Int = 0,
) {
    val fontOption: SubtitleFontOption get() = SubtitleFontOption.fromId(fontId)
    val colorOption: SubtitleColorOption get() = SubtitleColorOption.fromId(colorId)
    val backgroundOption: SubtitleBackgroundOption get() = SubtitleBackgroundOption.fromId(backgroundId)
    val edgeOption: SubtitleEdgeOption get() = SubtitleEdgeOption.fromId(edgeId)
    val positionOption: SubtitlePositionOption get() = SubtitlePositionOption.fromId(positionId)

    fun normalized(): SubtitleStyleSettings {
        val allowedSizes = listOf(18, 22, 24, 26, 32)
        val closestSize = allowedSizes.minByOrNull { kotlin.math.abs(it - fontSizeSp) } ?: 24
        return copy(
            fontId = fontOption.id,
            fontSizeSp = closestSize,
            colorId = colorOption.id,
            backgroundId = backgroundOption.id,
            edgeId = edgeOption.id,
            positionId = positionOption.id,
            delayMs = delayMs.coerceIn(-5000, 5000),
        )
    }

    companion object {
        val SUPPORTED_SIZES = listOf(18, 22, 24, 26, 32)
        const val MIN_DELAY_MS = -5000
        const val MAX_DELAY_MS = 5000
    }
}

data class SubtitleLanguageChoice(
    val code: String,
    val labelAr: String,
)

object SubtitleLanguageCatalog {
    val choices = listOf(
        SubtitleLanguageChoice("ar", "العربية"),
        SubtitleLanguageChoice("en", "الإنجليزية"),
        SubtitleLanguageChoice("hi", "الهندية"),
        SubtitleLanguageChoice("ur", "الأردية"),
        SubtitleLanguageChoice("fr", "الفرنسية"),
        SubtitleLanguageChoice("tr", "التركية"),
        SubtitleLanguageChoice("all", "كل اللغات"),
    )

    fun normalize(raw: String?): String {
        val cleaned = raw.orEmpty().trim().lowercase().replace('_', '-')
        return when (cleaned) {
            "ara", "ar-sa", "ar-eg", "arabic" -> "ar"
            "eng", "en-us", "en-gb", "english" -> "en"
            "hin", "hindi" -> "hi"
            "urd", "urdu" -> "ur"
            "fra", "fre", "french" -> "fr"
            "tur", "turkish" -> "tr"
            "spa", "spanish" -> "es"
            "deu", "ger", "german" -> "de"
            else -> cleaned
        }
    }
}

data class OnlineSubtitleResult(
    val url: String,
    val label: String,
    val language: String,
    val addonName: String,
)

data class TimedSubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

data class SubtitleMediaMetadata(
    val title: String,
    val mediaType: MediaType,
    val seriesName: String? = null,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val imdbId: String? = null,
) {
    val isSearchEligible: Boolean
        get() = mediaType == MediaType.MOVIE || mediaType == MediaType.SERIES_EPISODE

    val effectiveSearchTitle: String
        get() = if (mediaType == MediaType.SERIES_EPISODE) {
            seriesName?.takeIf { it.isNotBlank() } ?: title
        } else {
            title
        }
}

/**
 * Canonical default Stremio addons migrated from SONIX, explicitly EXCLUDING `SONIX AUDIO`.
 */
object DefaultAddonsRegistry {
    const val CINEMETA_ID = "com.linvo.cinemeta"
    const val OPENSUBTITLES_V3_ID = "org.stremio.opensubtitlesv3"
    const val OPENSUBTITLES_PRO_ID = "opensubtitles-pro"

    private val EXCLUDED_LEGACY_IDS = setOf("sonix.audio")

    val defaultAddons: List<StremioAddon> = listOf(
        StremioAddon(
            id = CINEMETA_ID,
            name = "Cinemeta",
            version = "3.0.14",
            description = "كتالوج بيانات الأفلام والمسلسلات",
            url = "https://v3-cinemeta.strem.io/manifest.json",
            types = listOf("movie", "series"),
            resources = listOf("catalog", "meta", "addon_catalog"),
            enabled = true,
            source = AddonSource.DEFAULT,
            hasSearchCatalog = true,
            searchCatalogIdsByType = mapOf(
                "movie" to listOf("top"),
                "series" to listOf("top"),
            ),
        ),
        StremioAddon(
            id = OPENSUBTITLES_V3_ID,
            name = "OpenSubtitles V3",
            version = "1.0.0",
            description = "إضافة الترجمات الرسمية متعددة اللغات للأفلام والمسلسلات",
            url = "https://opensubtitles-v3.strem.io/manifest.json",
            types = listOf("movie", "series"),
            resources = listOf("subtitles"),
            enabled = true,
            source = AddonSource.DEFAULT,
        ),
        StremioAddon(
            id = OPENSUBTITLES_PRO_ID,
            name = "OpenSubtitles PRO",
            version = "0.1.0",
            description = "مصدر إضافي موثوق للترجمات متعددة اللغات",
            url = "https://opensubtitlesv3-pro.dexter21767.com/manifest.json",
            types = listOf("movie", "series"),
            resources = listOf("subtitles"),
            enabled = true,
            source = AddonSource.DEFAULT,
        ),
    )

    private val defaultIds = defaultAddons.map { it.id }.toSet()

    fun isDefaultAddon(id: String): Boolean = id in defaultIds

    fun isExcludedLegacyAddon(id: String?, name: String?): Boolean {
        val cleanId = id.orEmpty().trim().lowercase()
        val cleanName = name.orEmpty().trim().lowercase()
        return cleanId in EXCLUDED_LEGACY_IDS || cleanName == "sonix audio"
    }

    fun ensureDefaults(stored: List<StremioAddon>): List<StremioAddon> {
        val filtered = stored.filterNot { isExcludedLegacyAddon(it.id, it.name) }.toMutableList()
        for (seed in defaultAddons) {
            val idx = filtered.indexOfFirst { it.id == seed.id || it.url == seed.url }
            if (idx < 0) {
                filtered.add(seed)
            } else {
                val existing = filtered[idx]
                filtered[idx] = seed.copy(enabled = existing.enabled)
            }
        }
        return filtered.distinctBy { it.id }
    }
}
