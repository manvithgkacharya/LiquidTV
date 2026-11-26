package com.liquidtv.app.data.repository

import com.liquidtv.app.data.local.dao.*
import com.liquidtv.app.data.local.entity.*
import com.liquidtv.app.data.remote.XtreamApiService
import com.liquidtv.app.domain.model.*
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val channelDao: ChannelDao,
    private val seriesDao: SeriesDao,
    private val categoryDao: CategoryDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val epgDao: EpgDao,
    private val subscriptionRepository: SubscriptionRepository,
    private val xtreamApi: XtreamApiService,
    private val prefs: UserPreferencesRepository,
) : ChannelRepository {

    private fun activeSubIdFlow(): Flow<String?> =
        subscriptionDao.getActiveSubscription().map { it?.id }.distinctUntilChanged()

    override fun getAllChannels(): Flow<List<Channel>> =
        activeSubIdFlow().flatMapLatest { subId ->
            if (subId == null) flowOf(emptyList())
            else channelDao.getLiveChannels(subId).map { list -> list.map { it.toDomain() } }
        }

    override fun getMovies(): Flow<List<Channel>> =
        activeSubIdFlow().flatMapLatest { subId ->
            if (subId == null) flowOf(emptyList())
            else channelDao.getMovies(subId).map { list -> list.map { it.toDomain() } }
        }

    override fun getSeries(): Flow<List<SeriesItem>> =
        activeSubIdFlow().flatMapLatest { subId ->
            if (subId == null) flowOf(emptyList())
            else seriesDao.getSeries(subId).map { list -> list.map { it.toDomain() } }
        }

    override fun getSeriesById(seriesId: String): Flow<SeriesItem?> =
        seriesDao.observeSeriesById(seriesId).map { it?.toDomain() }

    override fun getEpisodesForSeries(seriesId: String): Flow<List<EpisodeItem>> =
        seriesDao.getEpisodesForSeries(seriesId).map { list -> list.map { it.toDomain() } }

    override suspend fun loadSeriesDetails(seriesId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val series = seriesDao.getSeriesById(seriesId)
                ?: return@withContext Result.failure(IllegalArgumentException("Series not found"))
            val sub = subscriptionDao.getSubscriptionById(series.subscriptionId)
                ?: return@withContext Result.failure(IllegalStateException("Subscription not found"))
            if (sub.type != SubscriptionType.XTREAM.name) {
                return@withContext Result.success(Unit)
            }
            val username = sub.username ?: return@withContext Result.failure(IllegalStateException("Missing username"))
            val password = subscriptionRepository.getDecryptedXtreamPassword(sub.id)
                ?: return@withContext Result.failure(IllegalStateException("Missing password"))
            val timeoutSec = prefs.userPreferences.first().connectionTimeout

            val info = xtreamApi.fetchSeriesInfo(
                serverUrl = sub.serverUrl,
                username = username,
                password = password,
                seriesId = series.externalSeriesId,
                timeoutSeconds = timeoutSec,
            )

            val preserved = seriesDao.getPreservedEpisodeState(seriesId).associateBy { it.id }
            val episodes = info.episodes.map { ep ->
                val epId = "${sub.id}_ep_${ep.id}"
                val prev = preserved[epId]
                EpisodeEntity(
                    id = epId,
                    subscriptionId = sub.id,
                    seriesId = seriesId,
                    externalEpisodeId = ep.id,
                    seasonNumber = ep.seasonNumber,
                    episodeNumber = ep.episodeNum,
                    title = ep.title,
                    streamUrl = xtreamApi.buildSeriesStreamUrl(
                        serverUrl = sub.serverUrl,
                        username = username,
                        password = password,
                        episodeId = ep.id,
                        containerExtension = ep.containerExtension,
                        directSource = ep.directSource,
                    ),
                    containerExtension = ep.containerExtension,
                    plot = ep.plot,
                    durationSec = ep.durationSec,
                    coverUrl = ep.coverUrl ?: series.coverUrl,
                    lastWatchedAt = prev?.lastWatchedAt,
                    resumePositionMs = prev?.resumePositionMs ?: 0L,
                )
            }

            seriesDao.upsertSingleSeries(
                series.copy(
                    plot = info.plot ?: series.plot,
                    cast = info.cast ?: series.cast,
                    director = info.director ?: series.director,
                    genre = info.genre ?: series.genre,
                    releaseDate = info.releaseDate ?: series.releaseDate,
                    rating = info.rating ?: series.rating,
                    coverUrl = info.cover ?: series.coverUrl,
                ),
            )
            seriesDao.replaceEpisodesForSeriesAtomically(seriesId, episodes)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getFavoriteChannels(): Flow<List<Channel>> =
        activeSubIdFlow().flatMapLatest { subId ->
            if (subId == null) flowOf(emptyList())
            else channelDao.getFavoriteChannels(subId).map { list -> list.map { it.toDomain() } }
        }

    override fun getRecentlyWatched(): Flow<List<Channel>> =
        activeSubIdFlow().flatMapLatest { subId ->
            if (subId == null) flowOf(emptyList())
            else channelDao.getRecentlyWatched(subId).map { list -> list.map { it.toDomain() } }
        }

    override fun getChannelsByCategory(category: String, mediaType: MediaType): Flow<List<Channel>> =
        activeSubIdFlow().flatMapLatest { subId ->
            if (subId == null) flowOf(emptyList())
            else channelDao.getChannelsByCategory(subId, category, mediaType.name)
                .map { list -> list.map { it.toDomain() } }
        }

    override fun getChannelsByLanguage(language: String): Flow<List<Channel>> =
        activeSubIdFlow().flatMapLatest { subId ->
            if (subId == null) flowOf(emptyList())
            else channelDao.getChannelsByLanguage(subId, language).map { list -> list.map { it.toDomain() } }
        }

    override fun getChannelsByCountry(country: String): Flow<List<Channel>> =
        activeSubIdFlow().flatMapLatest { subId ->
            if (subId == null) flowOf(emptyList())
            else channelDao.getChannelsByCountry(subId, country).map { list -> list.map { it.toDomain() } }
        }

    override fun searchChannels(query: String): Flow<List<Channel>> =
        activeSubIdFlow().flatMapLatest { subId ->
            if (subId == null || query.isBlank()) flowOf(emptyList())
            else channelDao.searchChannels(subId, query).map { list -> list.map { it.toDomain() } }
        }

    override fun searchSeries(query: String): Flow<List<SeriesItem>> =
        activeSubIdFlow().flatMapLatest { subId ->
            if (subId == null || query.isBlank()) flowOf(emptyList())
            else seriesDao.searchSeries(subId, query).map { list -> list.map { it.toDomain() } }
        }

    override fun getAllCategories(mediaType: MediaType): Flow<List<Category>> =
        activeSubIdFlow().flatMapLatest { subId ->
            if (subId == null) flowOf(emptyList())
            else if (mediaType == MediaType.SERIES_EPISODE) {
                combine(
                    seriesDao.getSeries(subId),
                    categoryDao.getCategories(subId, MediaType.SERIES_EPISODE.name),
                    categoryDao.getLockedCategoryNames(subId),
                ) { seriesList, categoryEntities, locked ->
                    val lockedSet = locked.map { it.trim().lowercase() }.toSet()
                    val countByName = mutableMapOf<String, Int>()
                    val displayNameByKey = mutableMapOf<String, String>()
                    for (item in seriesList) {
                        val rawName = item.categoryName?.trim().orEmpty()
                        if (rawName.isNotEmpty()) {
                            val key = rawName.lowercase()
                            countByName[key] = (countByName[key] ?: 0) + 1
                            displayNameByKey.putIfAbsent(key, rawName)
                        }
                    }
                    val orderByKey = categoryEntities.associateBy { it.name.trim().lowercase() }
                    val result = mutableListOf<Category>()
                    val seenKeys = mutableSetOf<String>()

                    // Preserve backend/database sort order for categories that actually contain series
                    for (cat in categoryEntities) {
                        val key = cat.name.trim().lowercase()
                        val count = countByName[key] ?: 0
                        if (count > 0 && seenKeys.add(key)) {
                            result += Category(
                                id = "${subId}_${mediaType.name}_$key",
                                subscriptionId = subId,
                                contentType = mediaType,
                                name = cat.name,
                                channelCount = count,
                                iconName = categoryIconName(cat.name),
                                isLocked = key in lockedSet,
                            )
                        }
                    }

                    // Include any series category names not present in categories table
                    for ((key, count) in countByName) {
                        if (seenKeys.add(key)) {
                            val name = displayNameByKey[key] ?: continue
                            result += Category(
                                id = "${subId}_${mediaType.name}_$key",
                                subscriptionId = subId,
                                contentType = mediaType,
                                name = name,
                                channelCount = count,
                                iconName = categoryIconName(name),
                                isLocked = key in lockedSet,
                            )
                        }
                    }

                    if (orderByKey.isEmpty()) result.sortedByDescending { it.channelCount } else result
                }
            } else {
                combine(
                    channelDao.getCategoriesWithCount(subId, mediaType.name),
                    categoryDao.getLockedCategoryNames(subId),
                ) { counts, locked ->
                    val lockedSet = locked.toSet()
                    counts.map { row ->
                        Category(
                            id = "${subId}_${mediaType.name}_${row.groupTitle.lowercase()}",
                            subscriptionId = subId,
                            contentType = mediaType,
                            name = row.groupTitle,
                            channelCount = row.count,
                            iconName = categoryIconName(row.groupTitle),
                            isLocked = row.groupTitle in lockedSet,
                        )
                    }
                }
            }
        }

    override fun getAllLanguages(): Flow<List<Language>> =
        activeSubIdFlow().flatMapLatest { subId ->
            if (subId == null) flowOf(emptyList())
            else channelDao.getLanguagesWithCount(subId).map { list ->
                list.map { row ->
                    Language(
                        code = row.language.lowercase().take(3),
                        name = row.language,
                        channelCount = row.count,
                    )
                }
            }
        }

    override fun getAllCountries(): Flow<List<Country>> =
        activeSubIdFlow().flatMapLatest { subId ->
            if (subId == null) flowOf(emptyList())
            else channelDao.getCountriesWithCount(subId).map { list ->
                list.map { row ->
                    val clean = row.country.trim().lowercase()
                    val iso = COUNTRY_TO_CODE[clean]
                        ?: if (row.country.length == 2) row.country.uppercase() else row.country.take(2).uppercase()
                    Country(
                        code = iso,
                        name = row.country,
                        flagEmoji = countryCodeToFlag(iso),
                        channelCount = row.count,
                    )
                }
            }
        }

    override fun getLockedCategoryNames(): Flow<Set<String>> =
        activeSubIdFlow().flatMapLatest { subId ->
            if (subId == null) flowOf(emptySet())
            else categoryDao.getLockedCategoryNames(subId).map { it.toSet() }
        }

    override suspend fun setCategoryLocked(categoryName: String, locked: Boolean) {
        val subId = subscriptionDao.getActiveSubscriptionOnce()?.id ?: return
        categoryDao.setCategoryLocked(subId, categoryName, locked)
    }

    override suspend fun getChannelById(id: String): Channel? =
        channelDao.getChannelById(id)?.toDomain()

    override suspend fun getEpisodeById(episodeId: String): EpisodeItem? =
        seriesDao.getEpisodeById(episodeId)?.toDomain()

    override suspend fun toggleFavorite(channelId: String) =
        channelDao.toggleFavorite(channelId)

    override suspend fun toggleSeriesFavorite(seriesId: String) =
        seriesDao.toggleFavorite(seriesId)

    override suspend fun recordWatch(channelId: String, positionMs: Long, durationMs: Long) {
        val channel = channelDao.getChannelById(channelId) ?: return
        val now = System.currentTimeMillis()
        channelDao.updateLastWatched(channelId, now)
        if (positionMs > 0L) {
            channelDao.updateResumePosition(channelId, positionMs)
        }
        watchHistoryDao.insertEntry(
            WatchHistoryEntity(
                subscriptionId = channel.subscriptionId,
                channelId = channelId,
                channelName = channel.name,
                mediaType = channel.mediaType,
                watchedAt = now,
                positionMs = positionMs,
                durationMs = durationMs,
            ),
        )
    }

    override suspend fun recordEpisodeWatch(episodeId: String, positionMs: Long, durationMs: Long) {
        val episode = seriesDao.getEpisodeById(episodeId) ?: return
        val now = System.currentTimeMillis()
        seriesDao.updateEpisodeProgress(episodeId, now, positionMs)
        watchHistoryDao.insertEntry(
            WatchHistoryEntity(
                subscriptionId = episode.subscriptionId,
                channelId = episodeId,
                channelName = episode.title,
                mediaType = MediaType.SERIES_EPISODE.name,
                watchedAt = now,
                positionMs = positionMs,
                durationMs = durationMs,
            ),
        )
    }

    override suspend fun updateResumePosition(itemId: String, mediaType: MediaType, positionMs: Long) {
        when (mediaType) {
            MediaType.SERIES_EPISODE -> seriesDao.updateEpisodeProgress(itemId, System.currentTimeMillis(), positionMs)
            else -> channelDao.updateResumePosition(itemId, positionMs)
        }
    }

    override fun observeChannelEpg(channel: Channel): Flow<ChannelEpgSummary> {
        val tvgKey = channel.tvgId?.takeIf { it.isNotBlank() } ?: return flowOf(ChannelEpgSummary())
        val now = System.currentTimeMillis()
        return epgDao.observeCurrentAndNext(channel.subscriptionId, tvgKey, now).map { list ->
            val current = list.firstOrNull { it.startTimeMs <= now && it.endTimeMs > now }
            val next = list.firstOrNull { it.startTimeMs > now }
            ChannelEpgSummary(
                current = current?.toDomain(),
                next = next?.toDomain(),
            )
        }
    }

    override suspend fun fetchShortEpgIfNeeded(channel: Channel): ChannelEpgSummary = withContext(Dispatchers.IO) {
        val tvgKey = channel.tvgId?.takeIf { it.isNotBlank() } ?: channel.externalId
        if (tvgKey.isNullOrBlank()) return@withContext ChannelEpgSummary()
        val now = System.currentTimeMillis()
        val cached = epgDao.getUpcomingPrograms(channel.subscriptionId, tvgKey, now)
        if (cached.isNotEmpty()) {
            return@withContext ChannelEpgSummary(
                current = cached.firstOrNull { it.startTimeMs <= now && it.endTimeMs > now }?.toDomain(),
                next = cached.firstOrNull { it.startTimeMs > now }?.toDomain(),
            )
        }

        val sub = subscriptionDao.getSubscriptionById(channel.subscriptionId)
            ?: return@withContext ChannelEpgSummary()
        if (sub.type != SubscriptionType.XTREAM.name || channel.externalId.isNullOrBlank()) {
            return@withContext ChannelEpgSummary()
        }
        val username = sub.username ?: return@withContext ChannelEpgSummary()
        val password = subscriptionRepository.getDecryptedXtreamPassword(sub.id)
            ?: return@withContext ChannelEpgSummary()

        val items = xtreamApi.fetchShortEpg(sub.serverUrl, username, password, channel.externalId, limit = 4)
        val entities = items.mapNotNull { dto ->
            val startMs = dto.startTimestampSec?.times(1000L) ?: return@mapNotNull null
            val stopMs = dto.stopTimestampSec?.times(1000L) ?: return@mapNotNull null
            EpgProgramEntity(
                subscriptionId = sub.id,
                channelTvgId = tvgKey,
                title = dto.title,
                description = dto.description,
                startTimeMs = startMs,
                endTimeMs = stopMs,
            )
        }
        if (entities.isNotEmpty()) {
            epgDao.deleteEpgForChannel(sub.id, tvgKey)
            epgDao.insertPrograms(entities)
        }
        ChannelEpgSummary(
            current = entities.firstOrNull { it.startTimeMs <= now && it.endTimeMs > now }?.toDomain(),
            next = entities.firstOrNull { it.startTimeMs > now }?.toDomain(),
        )
    }

    override suspend fun refreshPlaylists(): Result<Unit> =
        subscriptionRepository.syncActiveSubscription()

    override suspend fun clearWatchHistory() {
        val subId = subscriptionDao.getActiveSubscriptionOnce()?.id
        if (subId != null) {
            watchHistoryDao.clearHistoryForSubscription(subId)
        } else {
            watchHistoryDao.clearHistory()
        }
    }

    override fun getPlaylistInfo(): Flow<PlaylistInfo> = subscriptionRepository.syncInfo

    private fun ChannelEntity.toDomain(): Channel {
        val headers = if (!httpHeadersJson.isNullOrBlank()) {
            runCatching {
                val json = JSONObject(httpHeadersJson)
                buildMap {
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        put(k, json.optString(k, ""))
                    }
                }
            }.getOrDefault(emptyMap())
        } else emptyMap()

        return Channel(
            id = id,
            subscriptionId = subscriptionId,
            externalId = externalId,
            mediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.LIVE),
            name = name,
            streamUrl = streamUrl,
            logoUrl = logoUrl,
            groupTitle = groupTitle,
            categoryId = categoryId,
            tvgId = tvgId,
            tvgName = tvgName,
            country = country,
            language = language,
            channelNumber = channelNumber,
            httpHeaders = headers,
            containerExtension = containerExtension,
            plot = plot,
            rating = rating,
            releaseYear = releaseYear,
            durationSec = durationSec,
            resumePositionMs = resumePositionMs,
            isFavorite = isFavorite,
            lastWatchedAt = lastWatchedAt,
            addedAt = addedAt,
        )
    }

    private fun SeriesEntity.toDomain() = SeriesItem(
        id = id,
        subscriptionId = subscriptionId,
        externalSeriesId = externalSeriesId,
        name = name,
        coverUrl = coverUrl,
        categoryId = categoryId,
        categoryName = categoryName,
        plot = plot,
        cast = cast,
        director = director,
        genre = genre,
        releaseDate = releaseDate,
        rating = rating,
        lastModified = lastModified,
        isFavorite = isFavorite,
        lastWatchedAt = lastWatchedAt,
    )

    private fun EpisodeEntity.toDomain() = EpisodeItem(
        id = id,
        subscriptionId = subscriptionId,
        seriesId = seriesId,
        externalEpisodeId = externalEpisodeId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        title = title,
        streamUrl = streamUrl,
        containerExtension = containerExtension,
        plot = plot,
        durationSec = durationSec,
        coverUrl = coverUrl,
        lastWatchedAt = lastWatchedAt,
        resumePositionMs = resumePositionMs,
    )

    private fun EpgProgramEntity.toDomain() = EpgProgram(
        id = id,
        subscriptionId = subscriptionId,
        channelTvgId = channelTvgId,
        title = title,
        description = description,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        category = category,
    )

    private fun categoryIconName(category: String): String = when {
        category.contains("news", true) -> "newspaper"
        category.contains("sport", true) -> "sports"
        category.contains("movie", true) || category.contains("film", true) -> "movies"
        category.contains("entertain", true) -> "entertainment"
        category.contains("music", true) -> "music"
        category.contains("doc", true) -> "documentary"
        category.contains("kid", true) || category.contains("cartoon", true) -> "kids"
        category.contains("cook", true) -> "cooking"
        category.contains("travel", true) -> "travel"
        category.contains("science", true) -> "science"
        category.contains("lifestyle", true) -> "lifestyle"
        category.contains("business", true) -> "business"
        else -> "live_tv"
    }

    private fun countryCodeToFlag(code: String): String? {
        if (code.length != 2) return null
        return try {
            val upper = code.uppercase()
            val first = Character.codePointAt(upper, 0) - 0x41 + 0x1F1E6
            val second = Character.codePointAt(upper, 1) - 0x41 + 0x1F1E6
            String(Character.toChars(first)) + String(Character.toChars(second))
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val COUNTRY_TO_CODE = mapOf(
            "united states" to "US", "united kingdom" to "GB", "india" to "IN",
            "germany" to "DE", "france" to "FR", "spain" to "ES", "italy" to "IT",
            "japan" to "JP", "china" to "CN", "brazil" to "BR", "russia" to "RU",
            "canada" to "CA", "australia" to "AU", "mexico" to "MX", "south korea" to "KR",
            "netherlands" to "NL", "argentina" to "AR", "turkey" to "TR", "saudi arabia" to "SA",
            "united arab emirates" to "AE", "egypt" to "EG", "pakistan" to "PK",
            "nigeria" to "NG", "south africa" to "ZA", "thailand" to "TH",
            "indonesia" to "ID", "poland" to "PL", "sweden" to "SE", "norway" to "NO",
            "denmark" to "DK", "finland" to "FI", "portugal" to "PT", "belgium" to "BE",
            "switzerland" to "CH", "austria" to "AT", "new zealand" to "NZ",
            "singapore" to "SG", "malaysia" to "MY", "philippines" to "PH",
            "morocco" to "MA", "algeria" to "DZ", "tunisia" to "TN", "jordan" to "JO",
            "kuwait" to "KW", "qatar" to "QA", "bahrain" to "BH", "oman" to "OM",
            "lebanon" to "LB", "iraq" to "IQ",
        )
    }
}
