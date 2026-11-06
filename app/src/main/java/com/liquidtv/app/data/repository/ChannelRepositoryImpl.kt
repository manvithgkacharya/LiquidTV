package com.liquidtv.app.data.repository

import com.liquidtv.app.data.local.dao.ChannelDao
import com.liquidtv.app.data.local.dao.WatchHistoryDao
import com.liquidtv.app.data.local.entity.ChannelEntity
import com.liquidtv.app.data.local.entity.WatchHistoryEntity
import com.liquidtv.app.data.parser.M3UParser
import com.liquidtv.app.data.remote.IptvApiService
import com.liquidtv.app.data.remote.PlaylistUrls
import com.liquidtv.app.data.datastore.UserPreferencesDataStore
import com.liquidtv.app.domain.model.*
import com.liquidtv.app.domain.repository.ChannelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val channelDao: ChannelDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val apiService: IptvApiService,
    private val parser: M3UParser,
    private val prefs: UserPreferencesDataStore,
) : ChannelRepository {

    // Internal state for playlist loading progress
    private val _playlistInfo = MutableStateFlow(PlaylistInfo(
        url = PlaylistUrls.INDEX_ALL,
        name = "iptv-org Main Playlist",
    ))

    override fun getAllChannels(): Flow<List<Channel>> =
        channelDao.getAllChannels().map { list -> list.map { it.toDomain() } }

    override fun getFavoriteChannels(): Flow<List<Channel>> =
        channelDao.getFavoriteChannels().map { list -> list.map { it.toDomain() } }

    override fun getRecentlyWatched(): Flow<List<Channel>> =
        channelDao.getRecentlyWatched().map { list -> list.map { it.toDomain() } }

    override fun getChannelsByCategory(category: String): Flow<List<Channel>> =
        channelDao.getChannelsByCategory(category).map { list -> list.map { it.toDomain() } }

    override fun getChannelsByLanguage(language: String): Flow<List<Channel>> =
        channelDao.getChannelsByLanguage(language).map { list -> list.map { it.toDomain() } }

    override fun getChannelsByCountry(country: String): Flow<List<Channel>> =
        channelDao.getChannelsByCountry(country).map { list -> list.map { it.toDomain() } }

    override fun searchChannels(query: String): Flow<List<Channel>> =
        channelDao.searchChannels(query).map { list -> list.map { it.toDomain() } }

    override fun getAllCategories(): Flow<List<Category>> =
        channelDao.getCategoriesWithCount().map { list ->
            list.map { row ->
                Category(
                    id = row.groupTitle.lowercase().replace(" ", "_"),
                    name = row.groupTitle,
                    channelCount = row.count,
                    iconName = categoryIconName(row.groupTitle),
                )
            }
        }

    override fun getAllLanguages(): Flow<List<Language>> =
        channelDao.getLanguagesWithCount().map { list ->
            list.map { row ->
                Language(
                    code = row.language.lowercase(),
                    name = row.language,
                    channelCount = row.count,
                )
            }
        }

    override fun getAllCountries(): Flow<List<Country>> =
        channelDao.getCountriesWithCount().map { list ->
            list.map { row ->
                val cleanCountry = row.country.trim().lowercase()
                val isoCode = COUNTRY_TO_CODE[cleanCountry]
                    ?: if (row.country.length == 2) row.country.uppercase() else row.country.take(2).uppercase()
                Country(
                    code = isoCode,
                    name = row.country,
                    flagEmoji = countryCodeToFlag(isoCode),
                    channelCount = row.count,
                )
            }
        }

    override suspend fun getChannelById(id: String): Channel? =
        channelDao.getChannelById(id)?.toDomain()

    override suspend fun toggleFavorite(channelId: String) =
        channelDao.toggleFavorite(channelId)

    override suspend fun recordWatch(channelId: String) {
        val channel = channelDao.getChannelById(channelId) ?: return
        val now = System.currentTimeMillis()
        channelDao.updateLastWatched(channelId, now)
        watchHistoryDao.insertEntry(
            WatchHistoryEntity(
                channelId = channelId,
                channelName = channel.name,
                watchedAt = now,
            )
        )
    }

    override suspend fun refreshPlaylists(): Result<Unit> = withContext(Dispatchers.IO) {
        if (_playlistInfo.value.isLoading) {
            return@withContext Result.success(Unit)
        }
        try {
            _playlistInfo.update { it.copy(isLoading = true, error = null, loadingProgress = 0f) }

            // 1. Fetch language and country playlists concurrently to build authoritative metadata maps
            val langDeferred = async {
                try {
                    val langBody = apiService.fetchPlaylist(PlaylistUrls.INDEX_LANGUAGE)
                    parser.parseGroupTitleMap(langBody.string())
                } catch (_: Exception) {
                    emptyMap<String, String>()
                }
            }
            val countryDeferred = async {
                try {
                    val countryBody = apiService.fetchPlaylist(PlaylistUrls.INDEX_COUNTRY)
                    parser.parseGroupTitleMap(countryBody.string())
                } catch (_: Exception) {
                    emptyMap<String, String>()
                }
            }
            val languageMap = langDeferred.await()
            val countryMap = countryDeferred.await()

            _playlistInfo.update { it.copy(loadingProgress = 0.3f) }

            // 2. Fetch main playlist
            val responseBody = apiService.fetchPlaylist(PlaylistUrls.INDEX_ALL)
            val content = responseBody.string()

            _playlistInfo.update { it.copy(loadingProgress = 0.5f) }

            val result = parser.parse(content, languageMap, countryMap) { processed, total ->
                val progress = 0.5f + (processed.toFloat() / total) * 0.45f
                _playlistInfo.update { it.copy(loadingProgress = progress) }
            }

            _playlistInfo.update { it.copy(loadingProgress = 0.95f) }

            // Preserve favorites and watch history
            val existingFavorites = buildSet {
                channelDao.getFavoriteChannels().first().forEach { add(it.id) }
            }
            val existingLastWatched = buildMap<String, Long> {
                channelDao.getRecentlyWatched().first().forEach { ch ->
                    ch.lastWatchedAt?.let { put(ch.id, it) }
                }
            }

            val entities = result.channels.map { channel ->
                ChannelEntity(
                    id = channel.id,
                    name = channel.name,
                    streamUrl = channel.streamUrl,
                    logoUrl = channel.logoUrl,
                    groupTitle = channel.groupTitle,
                    tvgId = channel.tvgId,
                    tvgName = channel.tvgName,
                    country = channel.country,
                    language = channel.language,
                    channelNumber = channel.channelNumber,
                    isFavorite = channel.id in existingFavorites,
                    lastWatchedAt = existingLastWatched[channel.id],
                    addedAt = channel.addedAt,
                )
            }

            // Clear old records to prevent stale/null-metadata channels, then batch insert
            channelDao.deleteAllChannels()
            entities.chunked(500).forEach { chunk ->
                channelDao.upsertChannels(chunk)
            }

            prefs.setLastPlaylistRefresh(System.currentTimeMillis())

            _playlistInfo.update {
                it.copy(
                    isLoading = false,
                    loadingProgress = 1f,
                    totalChannels = result.parsedEntries,
                    lastRefreshed = System.currentTimeMillis(),
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            _playlistInfo.update {
                it.copy(isLoading = false, error = e.message ?: "Unknown error")
            }
            Result.failure(e)
        }
    }

    override suspend fun clearWatchHistory() = watchHistoryDao.clearHistory()

    override fun getPlaylistInfo(): Flow<PlaylistInfo> = _playlistInfo.asStateFlow()

    private fun ChannelEntity.toDomain() = Channel(
        id = id,
        name = name,
        streamUrl = streamUrl,
        logoUrl = logoUrl,
        groupTitle = groupTitle,
        tvgId = tvgId,
        tvgName = tvgName,
        country = country,
        language = language,
        channelNumber = channelNumber,
        isFavorite = isFavorite,
        lastWatchedAt = lastWatchedAt,
        addedAt = addedAt,
    )

    private fun categoryIconName(category: String): String = when (category.lowercase()) {
        "news" -> "newspaper"
        "sports" -> "sports_soccer"
        "movies", "movies/film" -> "movie"
        "entertainment" -> "celebration"
        "music" -> "music_note"
        "documentary" -> "video_library"
        "kids" -> "child_care"
        "animation" -> "animation"
        "education" -> "school"
        "business" -> "business"
        "lifestyle" -> "spa"
        "religious" -> "church"
        "cooking" -> "restaurant"
        "travel" -> "flight"
        "science" -> "science"
        "auto" -> "directions_car"
        "weather" -> "wb_sunny"
        "shopping" -> "shopping_cart"
        else -> "live_tv"
    }

    private fun countryCodeToFlag(code: String): String? {
        if (code.length != 2) return null
        return try {
            val first = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
            val second = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
            String(Character.toChars(first)) + String(Character.toChars(second))
        } catch (e: Exception) {
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
            "bangladesh" to "BD", "sri lanka" to "LK", "nepal" to "NP",
            "ireland" to "IE", "israel" to "IL", "ukraine" to "UA", "colombia" to "CO",
            "chile" to "CL", "peru" to "PE", "venezuela" to "VE", "greece" to "GR",
            "czech republic" to "CZ", "hungary" to "HU", "romania" to "RO", "vietnam" to "VN",
            "taiwan" to "TW", "hong kong" to "HK"
        )
    }
}

