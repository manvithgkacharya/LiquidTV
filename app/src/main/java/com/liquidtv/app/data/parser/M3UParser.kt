package com.liquidtv.app.data.parser

import com.liquidtv.app.domain.model.Channel
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Robust M3U/M3U8 playlist parser for the iptv-org format.
 *
 * Handles EXTINF attributes: tvg-id, tvg-name, tvg-logo, group-title,
 * tvg-country, tvg-language, tvg-chno, and display name after the comma.
 *
 * Resolves country from tvg-country and tvg-id suffixes (e.g. .us, .de, .fr),
 * and language from explicit tags, the language playlist map, or country inference.
 */
@Singleton
class M3UParser @Inject constructor() {

    companion object {
        private const val EXT_M3U = "#EXTM3U"
        private const val EXT_INF = "#EXTINF:"
        private val ATTR_REGEX = Regex("""(\S+?)=["']([^"']*)["']""")

        val COUNTRY_NAMES = mapOf(
            "us" to "United States", "gb" to "United Kingdom", "uk" to "United Kingdom", "in" to "India",
            "de" to "Germany", "fr" to "France", "es" to "Spain", "it" to "Italy",
            "jp" to "Japan", "cn" to "China", "br" to "Brazil", "ru" to "Russia",
            "ca" to "Canada", "au" to "Australia", "mx" to "Mexico", "kr" to "South Korea",
            "nl" to "Netherlands", "ar" to "Argentina", "tr" to "Turkey", "sa" to "Saudi Arabia",
            "ae" to "United Arab Emirates", "eg" to "Egypt", "pk" to "Pakistan",
            "ng" to "Nigeria", "za" to "South Africa", "th" to "Thailand",
            "id" to "Indonesia", "pl" to "Poland", "se" to "Sweden", "no" to "Norway",
            "dk" to "Denmark", "fi" to "Finland", "pt" to "Portugal", "be" to "Belgium",
            "ch" to "Switzerland", "at" to "Austria", "nz" to "New Zealand",
            "sg" to "Singapore", "my" to "Malaysia", "ph" to "Philippines",
            "bd" to "Bangladesh", "lk" to "Sri Lanka", "np" to "Nepal",
            "ie" to "Ireland", "il" to "Israel", "ua" to "Ukraine", "co" to "Colombia",
            "cl" to "Chile", "pe" to "Peru", "ve" to "Venezuela", "gr" to "Greece",
            "cz" to "Czech Republic", "hu" to "Hungary", "ro" to "Romania", "vn" to "Vietnam",
            "tw" to "Taiwan", "hk" to "Hong Kong", "al" to "Albania", "dz" to "Algeria",
            "am" to "Armenia", "az" to "Azerbaijan", "bh" to "Bahrain", "by" to "Belarus",
            "ba" to "Bosnia and Herzegovina", "bg" to "Bulgaria", "hr" to "Croatia",
            "cy" to "Cyprus", "ec" to "Ecuador", "ee" to "Estonia", "et" to "Ethiopia",
            "ge" to "Georgia", "gh" to "Ghana", "gt" to "Guatemala", "hn" to "Honduras",
            "is" to "Iceland", "iq" to "Iraq", "ir" to "Iran", "jo" to "Jordan",
            "kz" to "Kazakhstan", "ke" to "Kenya", "kw" to "Kuwait", "lv" to "Latvia",
            "lb" to "Lebanon", "lt" to "Lithuania", "lu" to "Luxembourg", "mk" to "North Macedonia",
            "mt" to "Malta", "ma" to "Morocco", "om" to "Oman", "pa" to "Panama",
            "py" to "Paraguay", "qa" to "Qatar", "rs" to "Serbia", "sk" to "Slovakia",
            "si" to "Slovenia", "tn" to "Tunisia", "uy" to "Uruguay", "uz" to "Uzbekistan"
        )

        val COUNTRY_TO_LANGUAGE = mapOf(
            "us" to "English", "gb" to "English", "uk" to "English", "au" to "English",
            "ca" to "English", "nz" to "English", "ie" to "English",
            "fr" to "French", "be" to "French",
            "es" to "Spanish", "mx" to "Spanish", "ar" to "Spanish", "co" to "Spanish",
            "cl" to "Spanish", "pe" to "Spanish", "ve" to "Spanish",
            "de" to "German", "at" to "German", "ch" to "German",
            "it" to "Italian",
            "in" to "Hindi",
            "jp" to "Japanese",
            "cn" to "Chinese", "tw" to "Chinese", "hk" to "Chinese",
            "ru" to "Russian", "by" to "Russian", "kz" to "Russian", "ua" to "Ukrainian",
            "br" to "Portuguese", "pt" to "Portuguese",
            "kr" to "Korean",
            "sa" to "Arabic", "eg" to "Arabic", "ae" to "Arabic", "qa" to "Arabic",
            "iq" to "Arabic", "dz" to "Arabic", "ma" to "Arabic",
            "tr" to "Turkish",
            "nl" to "Dutch",
            "pl" to "Polish",
            "se" to "Swedish",
            "no" to "Norwegian",
            "dk" to "Danish",
            "fi" to "Finnish",
            "gr" to "Greek",
            "id" to "Indonesian",
            "th" to "Thai",
            "vn" to "Vietnamese",
            "ph" to "Tagalog",
            "pk" to "Urdu",
            "bd" to "Bengali",
            "ir" to "Persian",
            "il" to "Hebrew"
        )
    }

    data class ParseResult(
        val channels: List<Channel>,
        val totalLines: Int,
        val parsedEntries: Int,
        val skippedEntries: Int,
    )

    /**
     * Extracts a fast map of streamUrl/tvgId -> group-title from index.language.m3u or index.country.m3u
     */
    fun parseLanguageMap(content: String): Map<String, String> = parseGroupTitleMap(content)

    fun parseGroupTitleMap(content: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val lines = content.lines()
        var currentGroup: String? = null
        var currentTvgId: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith(EXT_INF)) {
                val groupTitle = ATTR_REGEX.findAll(trimmed)
                    .firstOrNull { it.groupValues[1].equals("group-title", ignoreCase = true) }
                    ?.groupValues?.get(2)
                    ?.takeIf { it.isNotBlank() }
                val tvgId = ATTR_REGEX.findAll(trimmed)
                    .firstOrNull { it.groupValues[1].equals("tvg-id", ignoreCase = true) }
                    ?.groupValues?.get(2)
                    ?.takeIf { it.isNotBlank() }
                currentGroup = groupTitle
                currentTvgId = tvgId
            } else if (trimmed.startsWith("http") || trimmed.startsWith("rtmp")) {
                if (currentGroup != null) {
                    if (!map.containsKey(trimmed)) {
                        map[trimmed] = currentGroup
                    }
                    if (currentTvgId != null && !map.containsKey(currentTvgId)) {
                        map[currentTvgId] = currentGroup
                    }
                }
                currentGroup = null
                currentTvgId = null
            }
        }
        return map
    }

    /**
     * Parse a full M3U playlist string into Channel domain objects.
     */
    fun parse(
        content: String,
        languageMap: Map<String, String> = emptyMap(),
        countryMap: Map<String, String> = emptyMap(),
        onProgress: ((Int, Int) -> Unit)? = null,
    ): ParseResult {
        val lines = content.lines()
        val totalLines = lines.size
        val channels = mutableListOf<Channel>()
        val seenUrls = mutableSetOf<String>()
        var parsedEntries = 0
        var skippedEntries = 0
        var pendingExtInf: String? = null
        var lineIndex = 0

        for (line in lines) {
            lineIndex++
            if (lineIndex % 1000 == 0) {
                onProgress?.invoke(lineIndex, totalLines)
            }

            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> continue
                trimmed.startsWith(EXT_M3U) -> continue
                trimmed.startsWith("#EXTVLCOPT") -> continue
                trimmed.startsWith("#EXTGRP") -> continue
                trimmed.startsWith(EXT_INF) -> {
                    pendingExtInf = trimmed
                }
                trimmed.startsWith("#") -> continue
                trimmed.startsWith("http") || trimmed.startsWith("rtmp") ||
                        trimmed.startsWith("rtsp") || trimmed.startsWith("udp") ||
                        trimmed.startsWith("rtp") -> {
                    val streamUrl = trimmed
                    if (streamUrl in seenUrls) {
                        skippedEntries++
                        pendingExtInf = null
                        continue
                    }
                    if (pendingExtInf != null) {
                        try {
                            val channel = parseEntry(pendingExtInf!!, streamUrl, languageMap, countryMap)
                            channels.add(channel)
                            seenUrls.add(streamUrl)
                            parsedEntries++
                        } catch (e: Exception) {
                            skippedEntries++
                        }
                    } else {
                        val channel = Channel(
                            id = UUID.randomUUID().toString(),
                            name = extractChannelNameFromUrl(streamUrl),
                            streamUrl = streamUrl,
                        )
                        channels.add(channel)
                        seenUrls.add(streamUrl)
                        parsedEntries++
                    }
                    pendingExtInf = null
                }
                else -> {
                    if (pendingExtInf != null && !trimmed.startsWith("#")) {
                        skippedEntries++
                        pendingExtInf = null
                    }
                }
            }
        }

        onProgress?.invoke(totalLines, totalLines)

        return ParseResult(
            channels = channels,
            totalLines = totalLines,
            parsedEntries = parsedEntries,
            skippedEntries = skippedEntries,
        )
    }

    private fun parseEntry(
        extInf: String,
        streamUrl: String,
        languageMap: Map<String, String>,
        countryMap: Map<String, String>,
    ): Channel {
        val afterPrefix = extInf.removePrefix(EXT_INF)

        val commaIndex = afterPrefix.lastIndexOf(',')
        val displayName = if (commaIndex >= 0) {
            afterPrefix.substring(commaIndex + 1).trim()
        } else {
            ""
        }

        val attrSection = if (commaIndex >= 0) afterPrefix.substring(0, commaIndex) else afterPrefix

        val attrs = mutableMapOf<String, String>()
        ATTR_REGEX.findAll(attrSection).forEach { match ->
            attrs[match.groupValues[1].lowercase()] = match.groupValues[2]
        }

        val tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
        val tvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() }
        val tvgLogo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
        val groupTitle = attrs["group-title"]?.takeIf { it.isNotBlank() }
        val tvgCountry = attrs["tvg-country"]?.takeIf { it.isNotBlank() }?.lowercase()
        val tvgLanguage = attrs["tvg-language"]?.takeIf { it.isNotBlank() }
        val tvgChno = attrs["tvg-chno"]?.toIntOrNull()

        val name = displayName.takeIf { it.isNotBlank() }
            ?: tvgName?.takeIf { it.isNotBlank() }
            ?: extractChannelNameFromUrl(streamUrl)

        val id = tvgId?.takeIf { it.isNotBlank() }
            ?: "${name.lowercase().replace(" ", "_")}_${streamUrl.hashCode().toUInt()}"

        // Extract country code from tvg-id if not explicit (e.g. 00sReplay.us@SD -> us)
        val countryCodeFromId = tvgId
            ?.substringBefore("@")
            ?.substringAfterLast(".", "")
            ?.takeIf { it.length == 2 && it.all { c -> c.isLetter() } }
            ?.lowercase()

        val resolvedCountryCode = tvgCountry ?: countryCodeFromId
        val fallbackCountry = resolvedCountryCode?.let {
            COUNTRY_NAMES[it] ?: it.uppercase()
        }
        val mappedCountry = countryMap[streamUrl] ?: (if (tvgId != null) countryMap[tvgId] else null)
        val finalCountry = mappedCountry ?: fallbackCountry

        // Language resolution priority:
        // 1. Language playlist map (exact authoritative mapping)
        // 2. tvg-language attribute
        // 3. Country-to-language inference
        val mappedLanguage = languageMap[streamUrl] ?: (if (tvgId != null) languageMap[tvgId] else null)
        val inferredLanguage = resolvedCountryCode?.let { COUNTRY_TO_LANGUAGE[it] }
        val finalLanguage = mappedLanguage ?: tvgLanguage ?: inferredLanguage

        return Channel(
            id = id,
            name = name,
            streamUrl = streamUrl,
            logoUrl = tvgLogo,
            groupTitle = groupTitle,
            tvgId = tvgId,
            tvgName = tvgName,
            country = finalCountry,
            language = finalLanguage,
            channelNumber = tvgChno,
            addedAt = System.currentTimeMillis(),
        )
    }

    private fun extractChannelNameFromUrl(url: String): String {
        return try {
            val path = url.substringAfter("://").substringAfter("/")
            val last = path.substringAfterLast("/").substringBeforeLast(".")
            last.replace(Regex("[_\\-+]"), " ").trim().takeIf { it.isNotBlank() }
                ?: "Unknown Channel"
        } catch (e: Exception) {
            "Unknown Channel"
        }
    }
}
