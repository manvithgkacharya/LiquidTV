package com.liquidtv.app.data.audio

import com.liquidtv.app.domain.model.AudioBrandFilter
import com.liquidtv.app.domain.model.AudioChannelBrand
import com.liquidtv.app.domain.model.ExternalAudioChannel

/**
 * Strict allow-list classifier and ranker for supported external audio brands
 * (beIN SPORTS & ALWAN), excluding VOD/movie/series entries.
 */
object AudioSourceClassifier {
    private val diacritics = Regex("[\\u064B-\\u065F\\u0670]")
    private val separators = Regex("[^a-z0-9\\u0600-\\u06FF]+")
    private val firstDigits = Regex("""(\d+)""")

    private val nonLiveTerms = listOf(
        "movie", "movies", "film", "films", "cinema", "series", "serial", "episode", "episodes",
        "season", "vod", "مشاهدة", "فيلم", "افلام", "سينما", "مسلسل", "مسلسلات",
        "حلقة", "حلقات", "موسم", "الموسم",
    )

    private val episodePattern = Regex(
        """(^| )(s[0-9]{1,2}e[0-9]{1,3}|season [0-9]+ episode [0-9]+|episode [0-9]+)( |$)""",
    )

    private val beinPhrases = listOf(
        "bein sports", "bein sport", "beinsports", "beinsport",
        "be in sports", "be in sport",
        "بي ان سبورت", "بين سبورت", "بين سبورتس", "بيان سبورت",
    )

    private val alwanPhrases = listOf("alwan", "al wan", "elwan", "الوان")

    private val qualityLevels = listOf(
        0 to Regex("""(^| )sd( |$)"""),
        1 to Regex("""(^| )360p?( |$)"""),
        2 to Regex("""(^| )480p?( |$)"""),
        3 to Regex("""(^| )576p?( |$)"""),
        4 to Regex("""(^| )(720p?|hd)( |$)"""),
        5 to Regex("""(^| )(1080p?|fhd|full hd)( |$)"""),
        6 to Regex("""(^| )(1440p?|qhd)( |$)"""),
        7 to Regex("""(^| )(2160p?|uhd|4k)( |$)"""),
        8 to Regex("""(^| )(4320p?|8k)( |$)"""),
    )

    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return value.trim()
            .replace(diacritics, "")
            .replace(Regex("[أإآٱ]"), "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .lowercase()
            .replace(separators, " ")
            .trim()
    }

    private fun hasPhrase(text: String, phrase: String): Boolean {
        val normalizedPhrase = normalize(phrase)
        val phraseWords = normalizedPhrase.split(' ').filter { it.isNotBlank() }
        val textWords = text.split(' ').filter { it.isNotBlank() }
        if (phraseWords.isEmpty() || textWords.size < phraseWords.size) return false
        return textWords.windowed(size = phraseWords.size, partialWindows = false)
            .any { it == phraseWords }
    }

    fun classify(name: String?, categoryName: String? = null): AudioChannelBrand {
        val normalizedName = normalize(name)
        val normalizedCategory = normalize(categoryName)
        val text = listOf(normalizedName, normalizedCategory)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (text.isBlank()) return AudioChannelBrand.OTHER

        if (nonLiveTerms.any { hasPhrase(text, it) } || episodePattern.containsMatchIn(text)) {
            return AudioChannelBrand.OTHER
        }

        if (beinPhrases.any { hasPhrase(text, it) }) return AudioChannelBrand.BEIN_SPORTS
        if (alwanPhrases.any { hasPhrase(text, it) }) return AudioChannelBrand.ALWAN
        return AudioChannelBrand.OTHER
    }

    fun isAudioCandidate(name: String?, categoryName: String? = null): Boolean =
        classify(name, categoryName) != AudioChannelBrand.OTHER

    /**
     * Ascending quality rank: SD/low resolutions first (less bandwidth because
     * secondary player disables video), then HD/FHD/UHD/4K/8K, unknown last (99).
     */
    fun qualityRank(name: String?, categoryName: String? = null): Int {
        val text = normalize(listOf(name, categoryName).filterNotNull().joinToString(" "))
        return qualityLevels.filter { (_, pattern) -> pattern.containsMatchIn(text) }
            .maxOfOrNull { it.first } ?: 99
    }

    fun qualityLabel(rank: Int): String = when (rank) {
        0 -> "SD"
        1 -> "360p"
        2 -> "480p"
        3 -> "576p"
        4 -> "HD"
        5 -> "FHD"
        6 -> "QHD"
        7 -> "4K"
        8 -> "8K"
        else -> ""
    }

    fun sortChannels(channels: List<ExternalAudioChannel>): List<ExternalAudioChannel> {
        return channels.sortedWith(
            compareBy<ExternalAudioChannel>(
                {
                    when (it.brand) {
                        AudioChannelBrand.BEIN_SPORTS -> 0
                        AudioChannelBrand.ALWAN -> 1
                        AudioChannelBrand.OTHER -> 2
                    }
                },
                { it.qualityRank },
                { extractSortNumber(it.name, it.number) },
                { normalize(it.name) },
            ),
        )
    }

    fun filterChannels(
        channels: List<ExternalAudioChannel>,
        brandFilter: AudioBrandFilter,
        query: String,
    ): List<ExternalAudioChannel> {
        val normalizedQuery = normalize(query)
        return channels.filter { channel ->
            val matchesBrand = when (brandFilter) {
                AudioBrandFilter.ALL -> true
                AudioBrandFilter.BEIN_SPORTS -> channel.brand == AudioChannelBrand.BEIN_SPORTS
                AudioBrandFilter.ALWAN -> channel.brand == AudioChannelBrand.ALWAN
            }
            if (!matchesBrand) return@filter false
            if (normalizedQuery.isBlank()) return@filter true
            val brandAliases = when (channel.brand) {
                AudioChannelBrand.BEIN_SPORTS -> "bein sports بي ان سبورت بين سبورت"
                AudioChannelBrand.ALWAN -> "alwan الوان"
                AudioChannelBrand.OTHER -> ""
            }
            val searchable = normalize("${channel.name} ${channel.categoryName} ${channel.number} ${channel.qualityLabel} $brandAliases")
            searchable.contains(normalizedQuery)
        }
    }

    private fun extractSortNumber(name: String, chNo: String): Int {
        val fromName = firstDigits.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (fromName != null) return fromName
        return chNo.toIntOrNull() ?: Int.MAX_VALUE
    }
}
