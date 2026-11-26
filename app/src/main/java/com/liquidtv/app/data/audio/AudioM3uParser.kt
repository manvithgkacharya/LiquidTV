package com.liquidtv.app.data.audio

import com.liquidtv.app.domain.model.AudioChannelBrand
import com.liquidtv.app.domain.model.ExternalAudioChannel
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.URL

object AudioM3uParser {
    const val MAX_M3U_BYTES: Long = 128L * 1024L * 1024L
    const val MAX_M3U_CHARS: Int = 128 * 1024 * 1024
    private val ATTRIBUTE_REGEX_CACHE = HashMap<String, Regex>()

    fun parse(
        input: InputStream,
        baseUrl: String,
        dnsResolver: (String) -> Array<InetAddress> = { host -> InetAddress.getAllByName(host) },
    ): List<ExternalAudioChannel> {
        val rawChannels = mutableListOf<ExternalAudioChannel>()
        val seenUrls = HashSet<String>()
        val validatedHosts = HashSet<String>()
        val base = runCatching { URL(baseUrl) }.getOrNull()

        var pendingInfo: String? = null
        var pendingGroup = ""
        var channelNumber = 0
        var totalChars = 0
        var sawFirstContentLine = false
        var sawExtM3uHeader = false

        val boundedStream = BoundedInputStream(input, MAX_M3U_BYTES)
        BufferedReader(InputStreamReader(boundedStream, Charsets.UTF_8), 64 * 1024).use { reader ->
            while (true) {
                val rawLine = reader.readLine() ?: break
                totalChars += rawLine.length
                if (totalChars > MAX_M3U_CHARS) {
                    throw IllegalStateException("حجم قائمة M3U تجاوز الحد الآمن (128 ميغابايت)")
                }
                val line = rawLine.removePrefix("\uFEFF").trim()
                if (line.isEmpty()) continue

                if (!sawFirstContentLine) {
                    sawFirstContentLine = true
                    if (looksLikeMarkupOrJson(line, checkSubstring = true)) {
                        throw IllegalStateException("أعاد المصدر صفحة HTML أو بيانات غير صالحة بدلاً من ملف M3U")
                    }
                    if (!line.startsWith("#EXTM3U", ignoreCase = true)) {
                        throw IllegalStateException("ملف الصوت الخارجي لا يبدأ بترويسة #EXTM3U صالحة")
                    }
                    sawExtM3uHeader = true
                    continue
                }

                val firstChar = line[0]
                if ((firstChar == '<' || firstChar == '{' || firstChar == '[') &&
                    looksLikeMarkupOrJson(line, checkSubstring = false)
                ) {
                    throw IllegalStateException("أعاد المصدر محتوى HTML/JSON غير صالح أثناء قراءة M3U")
                }

                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        pendingInfo = line
                        if (line.contains("group-title", ignoreCase = true)) {
                            val attrGroup = extractAttribute(line, "group-title")
                            if (attrGroup.isNotBlank()) pendingGroup = attrGroup
                        }
                    }
                    line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                        pendingGroup = line.substringAfter(':').trim()
                    }
                    firstChar == '#' -> Unit
                    pendingInfo != null -> {
                        val info = pendingInfo.orEmpty()
                        val displayName = info.substringAfterLast(',', "").trim()
                        val name = if (info.contains("tvg-name", ignoreCase = true)) {
                            extractAttribute(info, "tvg-name").ifBlank { displayName }
                        } else {
                            displayName
                        }
                        val group = pendingGroup.ifBlank {
                            if (info.contains("group-title", ignoreCase = true)) {
                                extractAttribute(info, "group-title")
                            } else {
                                ""
                            }
                        }
                        val brand = AudioSourceClassifier.classify(name, group)

                        if (brand != AudioChannelBrand.OTHER) {
                            val resolved = resolveUrl(base, baseUrl, line)
                            val uri = resolved?.let { runCatching { URL(it) }.getOrNull() }
                            if (uri != null &&
                                (uri.protocol.equals("http", true) || uri.protocol.equals("https", true)) &&
                                !uri.host.isNullOrBlank()
                            ) {
                                val hostKey = uri.host.lowercase()
                                val hostSafe = if (validatedHosts.contains(hostKey)) {
                                    true
                                } else {
                                    val ok = runCatching {
                                        AudioNetworkSecurityValidator.assertSafeUrl(resolved, dnsResolver)
                                    }.isSuccess
                                    if (ok) validatedHosts.add(hostKey)
                                    ok
                                }
                                if (hostSafe && seenUrls.add(resolved)) {
                                    channelNumber++
                                    val chNo = if (info.contains("tvg-chno", ignoreCase = true)) {
                                        extractAttribute(info, "tvg-chno").ifBlank { channelNumber.toString() }
                                    } else {
                                        channelNumber.toString()
                                    }
                                    val cleanName = name.ifBlank { "قناة صوتية $channelNumber" }
                                    val qRank = AudioSourceClassifier.qualityRank(cleanName, group)
                                    val extension = uri.path.substringAfterLast('.', "").lowercase()
                                        .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "m3u8"
                                    rawChannels += ExternalAudioChannel(
                                        id = resolved,
                                        name = cleanName,
                                        number = chNo,
                                        streamUrl = resolved,
                                        brand = brand,
                                        categoryName = group.ifBlank { brand.displayName },
                                        containerExtension = extension,
                                        qualityRank = qRank,
                                        qualityLabel = AudioSourceClassifier.qualityLabel(qRank),
                                    )
                                }
                            }
                        }
                        pendingInfo = null
                        pendingGroup = ""
                    }
                }
            }
        }

        if (!sawFirstContentLine || !sawExtM3uHeader) {
            throw IllegalStateException("ملف الصوت الخارجي فارغ أو لا يحتوي على ترويسة #EXTM3U")
        }

        return AudioSourceClassifier.sortChannels(rawChannels)
    }

    private fun looksLikeMarkupOrJson(line: String, checkSubstring: Boolean): Boolean {
        val lower = line.lowercase()
        return lower.startsWith("<!doctype") ||
            lower.startsWith("<html") ||
            lower.startsWith("<head") ||
            lower.startsWith("<body") ||
            lower.startsWith("<script") ||
            lower.startsWith("<title") ||
            lower.startsWith("<?xml") ||
            lower.startsWith("<error") ||
            lower.startsWith("{") ||
            lower.startsWith("[") ||
            (checkSubstring && (lower.contains("<!doctype html") || lower.contains("<html")))
    }

    private fun resolveUrl(base: URL?, baseUrl: String, line: String): String? {
        return try {
            if (base != null) URL(base, line).toString() else URL(URL(baseUrl), line).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAttribute(line: String, name: String): String {
        val regex = synchronized(ATTRIBUTE_REGEX_CACHE) {
            ATTRIBUTE_REGEX_CACHE.getOrPut(name.lowercase()) {
                Regex("""(?:^|\s)${Regex.escape(name)}="([^"]*)"""", RegexOption.IGNORE_CASE)
            }
        }
        return regex.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }
}

internal class BoundedInputStream(
    delegate: InputStream,
    private val maxBytes: Long,
) : FilterInputStream(delegate) {
    private var bytesRead: Long = 0L

    override fun read(): Int {
        val b = super.read()
        if (b != -1) {
            bytesRead++
            if (bytesRead > maxBytes) {
                throw IllegalStateException("حجم قائمة M3U تجاوز الحد الآمن (128 ميغابايت)")
            }
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = super.read(b, off, len)
        if (count > 0) {
            bytesRead += count
            if (bytesRead > maxBytes) {
                throw IllegalStateException("حجم قائمة M3U تجاوز الحد الآمن (128 ميغابايت)")
            }
        }
        return count
    }
}
