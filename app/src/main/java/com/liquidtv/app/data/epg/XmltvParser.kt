package com.liquidtv.app.data.epg

import android.util.Xml
import com.liquidtv.app.data.local.entity.EpgProgramEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streaming XMLTV parser for EPG guides (`xmltv.php` / `x-tvg-url`).
 * Keeps only programs relevant to the current/upcoming window so TV memory stays low.
 */
@Singleton
class XmltvParser @Inject constructor() {

    suspend fun parse(
        inputStream: InputStream,
        subscriptionId: String,
        channelTvgIdsFilter: Set<String>? = null,
        windowStartMs: Long = System.currentTimeMillis() - 3 * 3600_000L,
        windowEndMs: Long = System.currentTimeMillis() + 48 * 3600_000L,
        maxPrograms: Int = 40_000,
    ): List<EpgProgramEntity> {
        val programs = ArrayList<EpgProgramEntity>(512)
        val lowercaseFilter = channelTvgIdsFilter?.map { it.lowercase() }?.toSet()

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, "UTF-8")

        var inProgramme = false
        var currentChannelId: String? = null
        var currentStartMs: Long? = null
        var currentEndMs: Long? = null
        var currentTitle: String? = null
        var currentDesc: String? = null
        var currentCategory: String? = null
        var currentTag: String? = null
        var eventCount = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            eventCount++
            if (eventCount % 1000 == 0) {
                currentCoroutineContext().ensureActive()
            }
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name
                    if (tagName.equals("programme", ignoreCase = true)) {
                        val ch = parser.getAttributeValue(null, "channel")?.trim()
                        val startRaw = parser.getAttributeValue(null, "start")?.trim()
                        val stopRaw = parser.getAttributeValue(null, "stop")?.trim()
                        val startMs = parseXmltvTimestamp(startRaw)
                        val stopMs = parseXmltvTimestamp(stopRaw)
                        val matchesChannel = ch != null &&
                            (lowercaseFilter == null || ch.lowercase() in lowercaseFilter)
                        val inWindow = startMs != null && stopMs != null &&
                            stopMs >= windowStartMs && startMs <= windowEndMs

                        if (matchesChannel && inWindow) {
                            inProgramme = true
                            currentChannelId = ch
                            currentStartMs = startMs
                            currentEndMs = stopMs
                            currentTitle = null
                            currentDesc = null
                            currentCategory = null
                        } else {
                            inProgramme = false
                        }
                    } else if (inProgramme) {
                        currentTag = tagName.lowercase()
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inProgramme && currentTag != null) {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "title" -> if (currentTitle == null) currentTitle = text
                                "desc" -> if (currentDesc == null) currentDesc = text
                                "category" -> if (currentCategory == null) currentCategory = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("programme", ignoreCase = true) && inProgramme) {
                        val ch = currentChannelId
                        val start = currentStartMs
                        val end = currentEndMs
                        val title = currentTitle
                        if (ch != null && start != null && end != null && !title.isNullOrBlank()) {
                            programs.add(
                                EpgProgramEntity(
                                    subscriptionId = subscriptionId,
                                    channelTvgId = ch,
                                    title = title,
                                    description = currentDesc,
                                    startTimeMs = start,
                                    endTimeMs = end,
                                    category = currentCategory,
                                ),
                            )
                            if (programs.size >= maxPrograms) {
                                return programs
                            }
                        }
                        inProgramme = false
                        currentTag = null
                    } else {
                        currentTag = null
                    }
                }
            }
            eventType = parser.next()
        }
        return programs
    }

    companion object {
        /**
         * Parses XMLTV timestamps such as:
         * - `20261123143000 +0300`
         * - `20261123143000 -0500`
         * - `20261123143000 +0000`
         * - `20261123143000` (defaults to UTC)
         */
        fun parseXmltvTimestamp(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            val parts = trimmed.split(Regex("\\s+"))
            val datePart = parts.getOrNull(0)?.take(14) ?: return null
            if (datePart.length < 12) return null
            val paddedDate = datePart.padEnd(14, '0')
            val tzPart = parts.getOrNull(1)?.trim()

            return try {
                val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                fmt.timeZone = when {
                    tzPart.isNullOrEmpty() -> TimeZone.getTimeZone("UTC")
                    tzPart.equals("UTC", ignoreCase = true) || tzPart == "Z" -> TimeZone.getTimeZone("UTC")
                    tzPart.matches(Regex("^[+-]\\d{4}$")) -> TimeZone.getTimeZone("GMT$tzPart")
                    else -> TimeZone.getTimeZone(tzPart)
                }
                fmt.parse(paddedDate)?.time
            } catch (_: Exception) {
                null
            }
        }
    }
}
