package com.liquidtv.app.data.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.liquidtv.app.data.subtitles.SubtitlePipeline
import com.liquidtv.app.domain.model.AddonMarketResult
import com.liquidtv.app.domain.model.AddonSource
import com.liquidtv.app.domain.model.DefaultAddonsRegistry
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.OnlineSubtitleResult
import com.liquidtv.app.domain.model.StremioAddon
import com.liquidtv.app.domain.model.SubtitleLanguageCatalog
import com.liquidtv.app.domain.model.SubtitleMediaMetadata
import com.liquidtv.app.domain.model.TimedSubtitleCue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class AddonEndpoints(
    val officialCatalogUrl: String = "https://api.strem.io/addonscollection.json",
    val communityCatalogPrimaryUrl: String = "https://stremio-addons.net/api/v0/addons?sort_by=stars&order=desc&limit=100&page=1",
    val communityCatalogFallbackUrl: String = "https://stremio-addons.com/addon_catalog/all/community.json",
    val cinemetaBaseUrl: String = "https://v3-cinemeta.strem.io",
)

data class ResolvedMediaIdentity(
    val type: String,
    val imdbId: String,
    val resourceId: String,
    val matchedTitle: String? = null,
)

data class SubtitleSearchOutcome(
    val identity: ResolvedMediaIdentity,
    val results: List<OnlineSubtitleResult>,
)

data class LoadedExternalSubtitle(
    val label: String,
    val language: String,
    val localFilePath: String,
    val extension: String,
    val cues: List<TimedSubtitleCue>,
)

@Singleton
class StremioAddonClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    var endpoints: AddonEndpoints = AddonEndpoints()

    suspend fun fetchMarketCatalogs(): AddonMarketResult = withContext(Dispatchers.IO) {
        val officialJson = fetchText(endpoints.officialCatalogUrl)
        val official = parseCatalogList(officialJson, AddonSource.OFFICIAL)

        var warning: String? = null
        val community = try {
            val primaryJson = fetchText(endpoints.communityCatalogPrimaryUrl)
            parseCatalogList(primaryJson, AddonSource.COMMUNITY)
        } catch (_: Exception) {
            try {
                val fallbackJson = fetchText(endpoints.communityCatalogFallbackUrl)
                parseCatalogList(fallbackJson, AddonSource.COMMUNITY)
            } catch (e: Exception) {
                warning = "تعذر تحميل إضافات المجتمع حالياً؛ تم عرض السوق الرسمي فقط."
                emptyList()
            }
        }

        AddonMarketResult(
            official = official.distinctBy { it.id },
            community = community.distinctBy { it.id },
            warningMessage = warning,
        )
    }

    suspend fun fetchAndNormalizeManifest(
        manifestUrl: String,
        source: AddonSource = AddonSource.CUSTOM,
        fallbackAddon: StremioAddon? = null,
    ): StremioAddon = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeManifestUrl(manifestUrl)
        require(normalizedUrl.startsWith("http://", true) || normalizedUrl.startsWith("https://", true)) {
            "أدخل رابط إضافة صالحاً يبدأ بـ https:// أو http://"
        }
        val body = fetchText(normalizedUrl)
        val json = JSONObject(body)
        val id = json.optString("id").trim().ifBlank { fallbackAddon?.id.orEmpty() }
        val name = json.optString("name").trim().ifBlank { fallbackAddon?.name.orEmpty() }
        require(id.isNotBlank() && name.isNotBlank()) {
            "ملف تعريف الإضافة غير مكتمل أو لا يحتوي على معرّف واسم صالحين."
        }
        require(!DefaultAddonsRegistry.isExcludedLegacyAddon(id, name)) {
            "هذه الإضافة غير مدعومة في SONIX IPTV."
        }
        normalizeAddonObject(
            raw = json,
            source = source,
            explicitUrl = normalizedUrl,
            fallback = fallbackAddon,
        ) ?: throw IllegalArgumentException("تعذر قراءة بيانات الإضافة من الرابط.")
    }

    suspend fun resolveImdb(
        metadata: SubtitleMediaMetadata,
        installedAddons: List<StremioAddon>,
    ): ResolvedMediaIdentity = withContext(Dispatchers.IO) {
        require(metadata.isSearchEligible) {
            "البحث عن الترجمة متاح للأفلام والحلقات فقط."
        }
        val directImdb = metadata.imdbId?.trim()?.takeIf { IMDB_REGEX.matches(it) }
        val type = if (metadata.mediaType == MediaType.SERIES_EPISODE) "series" else "movie"
        if (directImdb != null) {
            val resourceId = formatResourceId(
                imdbId = directImdb,
                isSeries = type == "series",
                season = metadata.seasonNumber,
                episode = metadata.episodeNumber,
            )
            return@withContext ResolvedMediaIdentity(type, directImdb, resourceId, metadata.effectiveSearchTitle)
        }

        val rawTitle = metadata.effectiveSearchTitle.trim()
        require(rawTitle.isNotBlank()) { "اسم المحتوى غير متوفر للبحث عن الترجمة." }

        val titleQueries = buildSearchQueries(rawTitle)
        val candidates = mutableListOf<CatalogCandidate>()

        for (query in titleQueries) {
            val cinemetaUrl = "${endpoints.cinemetaBaseUrl.trimEnd('/')}/catalog/$type/top/search=${encodePathParam(query)}.json"
            runCatching {
                val jsonText = fetchText(cinemetaUrl)
                extractMetas(jsonText).forEach { candidates += it }
            }
            if (candidates.any { IMDB_REGEX.matches(it.imdbId) }) break
        }

        if (candidates.none { IMDB_REGEX.matches(it.imdbId) }) {
            val enabledAddons = installedAddons.filter {
                it.enabled && !DefaultAddonsRegistry.isExcludedLegacyAddon(it.id, it.name)
            }
            for (addon in enabledAddons) {
                val catalogIds = discoverSearchCatalogIds(addon, type)
                val base = addon.baseUrl()
                for (catalogId in catalogIds) {
                    for (query in titleQueries) {
                        val url = "$base/catalog/$type/${encodePathParam(catalogId)}/search=${encodePathParam(query)}.json"
                        runCatching {
                            extractMetas(fetchText(url)).forEach { candidates += it }
                        }
                    }
                }
            }
        }

        val targetNormalized = normalizeForMatch(titleQueries.lastOrNull() ?: rawTitle)
        val validCandidates = candidates
            .filter { IMDB_REGEX.matches(it.imdbId) }
            .distinctBy { it.imdbId }
            .sortedWith(
                compareBy<CatalogCandidate> { candidate ->
                    val norm = normalizeForMatch(candidate.name)
                    when {
                        norm == targetNormalized -> 0
                        norm.contains(targetNormalized) || targetNormalized.contains(norm) -> 1
                        else -> 2
                    }
                }.thenByDescending { candidate ->
                    tokenOverlapScore(normalizeTokens(candidate.name), normalizeTokens(titleQueries.lastOrNull() ?: rawTitle))
                }
            )

        val match = validCandidates.firstOrNull()
            ?: throw IllegalStateException("لم يتم العثور على معرّف مطابق في الإضافات المثبتة.")

        val resourceId = formatResourceId(
            imdbId = match.imdbId,
            isSeries = type == "series",
            season = metadata.seasonNumber,
            episode = metadata.episodeNumber,
        )
        ResolvedMediaIdentity(
            type = type,
            imdbId = match.imdbId,
            resourceId = resourceId,
            matchedTitle = match.name.ifBlank { rawTitle },
        )
    }

    suspend fun searchSubtitles(
        metadata: SubtitleMediaMetadata,
        preferredLanguage: String,
        installedAddons: List<StremioAddon>,
    ): SubtitleSearchOutcome = withContext(Dispatchers.IO) {
        val identity = resolveImdb(metadata, installedAddons)
        val normalizedPref = SubtitleLanguageCatalog.normalize(preferredLanguage).ifBlank { "all" }

        val subtitleAddons = installedAddons.filter { addon ->
            addon.enabled &&
                !DefaultAddonsRegistry.isExcludedLegacyAddon(addon.id, addon.name) &&
                addon.id != DefaultAddonsRegistry.CINEMETA_ID &&
                addon.supportsResource("subtitles") &&
                !addon.configurationRequired &&
                !addon.url.contains("/[config]/")
        }

        if (subtitleAddons.isEmpty()) {
            return@withContext SubtitleSearchOutcome(identity, emptyList())
        }

        val encodedResourceId = URLEncoder.encode(identity.resourceId, "UTF-8").replace("+", "%20")
        val allResults = coroutineScope {
            subtitleAddons.map { addon ->
                async {
                    val base = addon.baseUrl()
                    val url = "$base/subtitles/${identity.type}/$encodedResourceId.json"
                    runCatching {
                        val payload = fetchText(url)
                        val root = JSONObject(payload)
                        val rows = root.optJSONArray("subtitles") ?: return@runCatching emptyList()
                        buildList {
                            for (i in 0 until rows.length()) {
                                val row = rows.optJSONObject(i) ?: continue
                                val subUrl = row.optString("url").trim()
                                if (!subUrl.startsWith("http://", true) && !subUrl.startsWith("https://", true)) continue
                                val rawLang = row.optString("lang").ifBlank { row.optString("language") }
                                val lang = SubtitleLanguageCatalog.normalize(rawLang)
                                if (normalizedPref != "all" && lang != normalizedPref && !lang.startsWith("$normalizedPref-")) {
                                    continue
                                }
                                val label = row.optString("label").trim()
                                    .ifBlank { row.optString("title").trim() }
                                    .ifBlank { rawLang.trim() }
                                    .ifBlank { addon.name }
                                add(
                                    OnlineSubtitleResult(
                                        url = subUrl,
                                        label = label,
                                        language = lang.ifBlank { "und" },
                                        addonName = addon.name,
                                    )
                                )
                            }
                        }
                    }.getOrElse { emptyList() }
                }
            }.awaitAll().flatten()
        }

        val deduped = allResults
            .distinctBy { it.url }
            .sortedWith(
                compareBy<OnlineSubtitleResult> {
                    if (normalizedPref != "all" && it.language == normalizedPref) 0 else 1
                }.thenBy { it.addonName.lowercase(Locale.ROOT) }
            )

        SubtitleSearchOutcome(identity = identity, results = deduped)
    }

    suspend fun downloadAndParseSubtitle(
        subtitleUrl: String,
        labelHint: String,
        preferredLang: String = "und",
    ): LoadedExternalSubtitle = withContext(Dispatchers.IO) {
        val trimmed = subtitleUrl.trim()
        require(trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            "يجب أن يبدأ رابط الترجمة بـ http:// أو https://"
        }

        val req = Request.Builder()
            .url(trimmed)
            .header("Accept", "text/plain, text/vtt, application/x-subrip, application/zip, application/octet-stream, */*")
            .build()

        okHttpClient.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("تعذر تنزيل الترجمة من الخادم حالياً")
            }
            val body = response.body ?: throw IllegalStateException("استجابة فارغة من خادم الترجمة")
            val contentLength = body.contentLength()
            require(contentLength <= SubtitlePipeline.MAX_SUBTITLE_BYTES) {
                "حجم ملف الترجمة أكبر من الحد المسموح (20MB)"
            }
            val rawBytes = readStreamBounded(body.byteStream(), SubtitlePipeline.MAX_SUBTITLE_BYTES)
            val parsed = SubtitlePipeline.decodeAndParse(
                rawBytes = rawBytes,
                contentEncoding = response.header("Content-Encoding"),
                sourceUrlOrName = trimmed,
                contentDisposition = response.header("Content-Disposition"),
            )

            val savedFile = persistCleanSubtitleFile(
                key = trimmed,
                extension = parsed.decoded.extension,
                content = parsed.decoded.text,
            )

            val resolvedLabel = labelHint.trim().ifBlank { parsed.decoded.fileName }
            val detectedLang = SubtitlePipeline.detectLanguage(resolvedLabel, parsed.decoded.fileName)
                .takeIf { it != "und" }
                ?: preferredLang.ifBlank { "und" }

            LoadedExternalSubtitle(
                label = resolvedLabel,
                language = detectedLang,
                localFilePath = savedFile.absolutePath,
                extension = parsed.decoded.extension,
                cues = parsed.cues,
            )
        }
    }

    suspend fun readAndParseLocalUri(uri: Uri): LoadedExternalSubtitle = withContext(Dispatchers.IO) {
        val displayName = resolveUriDisplayName(uri) ?: "local_subtitle.srt"
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            readStreamBounded(stream, SubtitlePipeline.MAX_SUBTITLE_BYTES)
        } ?: throw IllegalStateException("تعذر فتح ملف الترجمة المختار")

        val parsed = SubtitlePipeline.decodeAndParse(
            rawBytes = rawBytes,
            contentEncoding = null,
            sourceUrlOrName = displayName,
            contentDisposition = null,
        )
        val savedFile = persistCleanSubtitleFile(
            key = uri.toString(),
            extension = parsed.decoded.extension,
            content = parsed.decoded.text,
        )
        val label = displayName.substringAfterLast('/')
        val lang = SubtitlePipeline.detectLanguage(label, parsed.decoded.fileName)
        LoadedExternalSubtitle(
            label = label,
            language = lang,
            localFilePath = savedFile.absolutePath,
            extension = parsed.decoded.extension,
            cues = parsed.cues,
        )
    }

    private fun resolveUriDisplayName(uri: Uri): String? {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        }
        return uri.lastPathSegment
    }

    private fun persistCleanSubtitleFile(key: String, extension: String, content: String): File {
        val dir = File(context.filesDir, "subtitles").apply { mkdirs() }
        // Keep subtitle cache bounded (max 15 most recent files)
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(14)?.forEach { old ->
            runCatching { old.delete() }
        }
        val hash = sha256Short(key)
        val safeExt = extension.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.ifBlank { "srt" }
        val file = File(dir, "sub_$hash.$safeExt")
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    private fun discoverSearchCatalogIds(addon: StremioAddon, type: String): List<String> {
        addon.searchCatalogIdsByType[type]?.takeIf { it.isNotEmpty() }?.let { return it }
        return runCatching {
            val manifestJson = JSONObject(fetchText(addon.url))
            extractSearchCatalogIds(manifestJson)[type].orEmpty()
        }.getOrElse { emptyList() }
    }

    private fun fetchText(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/plain, */*")
            .build()
        okHttpClient.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("تعذر الاتصال بخادم الإضافة حالياً")
            }
            val body = response.body ?: throw IllegalStateException("استجابة فارغة من خادم الإضافة")
            val bytes = readStreamBounded(body.byteStream(), MAX_JSON_BYTES)
            return bytes.toString(Charsets.UTF_8)
        }
    }

    private fun readStreamBounded(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            require(total <= maxBytes) { "حجم الملف أكبر من الحد المسموح (${maxBytes / (1024 * 1024)}MB)" }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun parseCatalogList(jsonPayload: String, source: AddonSource): List<StremioAddon> {
        val parsed = JSONTokener(jsonPayload.trim()).nextValue()
        val array = when (parsed) {
            is JSONArray -> parsed
            is JSONObject -> {
                listOf("addons", "data", "results", "items")
                    .firstNotNullOfOrNull { parsed.optJSONArray(it) }
                    ?: JSONArray()
            }
            else -> JSONArray()
        }
        val list = mutableListOf<StremioAddon>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val normalized = normalizeAddonObject(obj, source) ?: continue
            if (!DefaultAddonsRegistry.isExcludedLegacyAddon(normalized.id, normalized.name)) {
                list += normalized
            }
        }
        return list
    }

    fun normalizeAddonObject(
        raw: JSONObject,
        source: AddonSource,
        explicitUrl: String? = null,
        fallback: StremioAddon? = null,
    ): StremioAddon? {
        val manifest = raw.optJSONObject("manifest") ?: raw
        val transportObj = raw.optJSONObject("transport")
        val rawUrl = explicitUrl
            ?: raw.optString("url").takeIf { it.isNotBlank() }
            ?: raw.optString("manifestUrl").takeIf { it.isNotBlank() }
            ?: raw.optString("transportUrl").takeIf { it.isNotBlank() }
            ?: raw.optString("manifest_url").takeIf { it.isNotBlank() }
            ?: transportObj?.optString("url")?.takeIf { it.isNotBlank() }
            ?: fallback?.url.orEmpty()

        val trimmedUrl = rawUrl.trim()
        if (!trimmedUrl.startsWith("http://", true) && !trimmedUrl.startsWith("https://", true)) {
            return null
        }

        val name = raw.optString("name").trim()
            .ifBlank { manifest.optString("name").trim() }
            .ifBlank { raw.optString("title").trim() }
            .ifBlank { fallback?.name.orEmpty() }
        if (name.isBlank()) return null

        val normalizedUrl = normalizeManifestUrl(trimmedUrl)
        val id = raw.optString("id").trim()
            .ifBlank { manifest.optString("id").trim() }
            .ifBlank { fallback?.id.orEmpty() }
            .ifBlank { normalizedUrl }

        if (DefaultAddonsRegistry.isExcludedLegacyAddon(id, name)) return null

        val version = raw.optString("version").trim()
            .ifBlank { manifest.optString("version").trim() }
            .ifBlank { fallback?.version.orEmpty() }
        val description = raw.optString("description").trim()
            .ifBlank { manifest.optString("description").trim() }
            .ifBlank { fallback?.description.orEmpty() }

        val resources = extractStringList(manifest.optJSONArray("resources")) { item ->
            when (item) {
                is String -> item
                is JSONObject -> item.optString("name")
                else -> ""
            }
        }.ifEmpty { fallback?.resources.orEmpty() }

        val types = extractStringList(manifest.optJSONArray("types")) { it.toString() }
            .ifEmpty { fallback?.types.orEmpty() }
            .ifEmpty { listOf("movie", "series") }

        val behaviorHints = manifest.optJSONObject("behaviorHints")
        val configRequired = raw.optBoolean("configurationRequired", false) ||
            (behaviorHints?.optBoolean("configurationRequired", false) == true) ||
            normalizedUrl.contains("/[config]/")

        val explicitConfigureUrl = raw.optString("configureUrl").trim()
            .ifBlank { manifest.optString("configureUrl").trim() }
        val derivedConfigureUrl = if (configRequired && explicitConfigureUrl.isBlank()) {
            normalizedUrl
                .replace("/[config]", "")
                .replace(Regex("/manifest\\.json(?:\\?.*)?$", RegexOption.IGNORE_CASE), "")
                .trimEnd('/') + "/configure"
        } else {
            explicitConfigureUrl
        }

        val searchCatalogs = extractSearchCatalogIds(manifest)

        return StremioAddon(
            id = id,
            name = name,
            version = version,
            description = description,
            url = normalizedUrl,
            types = types,
            resources = resources,
            enabled = if (raw.has("enabled")) raw.optBoolean("enabled", true) else (fallback?.enabled ?: true),
            source = fallback?.source ?: source,
            configurationRequired = configRequired,
            configureUrl = derivedConfigureUrl,
            hasSearchCatalog = searchCatalogs.values.any { it.isNotEmpty() } || (fallback?.hasSearchCatalog == true),
            searchCatalogIdsByType = if (searchCatalogs.isNotEmpty()) searchCatalogs else fallback?.searchCatalogIdsByType.orEmpty(),
        )
    }

    private fun extractSearchCatalogIds(manifest: JSONObject): Map<String, List<String>> {
        val catalogs = manifest.optJSONArray("catalogs") ?: return emptyMap()
        val result = mutableMapOf<String, MutableList<String>>()
        for (i in 0 until catalogs.length()) {
            val cat = catalogs.optJSONObject(i) ?: continue
            val type = cat.optString("type").trim()
            val id = cat.optString("id").trim()
            if (type.isBlank() || id.isBlank()) continue
            val extra = cat.optJSONArray("extra")
            var supportsSearch = false
            if (extra != null) {
                for (j in 0 until extra.length()) {
                    if (extra.optJSONObject(j)?.optString("name") == "search") {
                        supportsSearch = true
                        break
                    }
                }
            }
            val extraSupported = cat.optJSONArray("extraSupported")
            if (!supportsSearch && extraSupported != null) {
                for (j in 0 until extraSupported.length()) {
                    if (extraSupported.optString(j) == "search") {
                        supportsSearch = true
                        break
                    }
                }
            }
            if (supportsSearch) {
                result.getOrPut(type) { mutableListOf() }.add(id)
            }
        }
        return result
    }

    private fun extractStringList(array: JSONArray?, mapper: (Any) -> String): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.opt(i) ?: continue
                val str = mapper(value).trim()
                if (str.isNotBlank()) add(str)
            }
        }.distinct()
    }

    private fun extractMetas(jsonPayload: String): List<CatalogCandidate> {
        val obj = JSONObject(jsonPayload)
        val metas = obj.optJSONArray("metas") ?: return emptyList()
        return buildList {
            for (i in 0 until metas.length()) {
                val m = metas.optJSONObject(i) ?: continue
                val imdb = m.optString("imdb_id").ifBlank { m.optString("id") }.trim()
                val name = m.optString("name").ifBlank { m.optString("title") }.trim()
                if (imdb.isNotBlank()) add(CatalogCandidate(imdb, name))
            }
        }
    }

    private fun buildSearchQueries(rawTitle: String): List<String> {
        val trimmed = rawTitle.trim()
        val cleaned = cleanMediaTitle(trimmed)
        return listOf(trimmed, cleaned)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    companion object {
        private const val MAX_JSON_BYTES = 10 * 1024 * 1024
        private val IMDB_REGEX = Regex("^tt\\d+$")

        fun normalizeManifestUrl(url: String): String {
            val trimmed = url.trim()
            return if (trimmed.lowercase(Locale.ROOT).endsWith("/manifest.json")) {
                trimmed
            } else {
                trimmed.trimEnd('/') + "/manifest.json"
            }
        }

        fun formatResourceId(
            imdbId: String,
            isSeries: Boolean,
            season: Int,
            episode: Int,
        ): String {
            return if (isSeries && season > 0 && episode > 0) {
                "$imdbId:$season:$episode"
            } else {
                imdbId
            }
        }

        fun cleanMediaTitle(raw: String): String {
            return raw
                .replace(Regex("""\[[^\]]*]"""), " ")
                .replace(Regex("""\((?:19|20)\d{2}\)"""), " ")
                .replace(Regex("""\b(?:4K|8K|UHD|FHD|HD|SD|1080p|720p|2160p|HDR|WEB-DL|WEBRip|BluRay|MULTI|AR|EN)\b""", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("""\s*\|\s*.*$"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }

        private fun normalizeForMatch(value: String): String =
            value.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")

        private fun normalizeTokens(value: String): Set<String> =
            value.lowercase(Locale.ROOT)
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.isNotBlank() }
                .toSet()

        private fun tokenOverlapScore(a: Set<String>, b: Set<String>): Int {
            if (a.isEmpty() || b.isEmpty()) return 0
            return a.intersect(b).size
        }

        private fun encodePathParam(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        private fun sha256Short(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.take(12).joinToString("") { "%02x".format(it) }
        }
    }

    private data class CatalogCandidate(
        val imdbId: String,
        val name: String,
    )
}
