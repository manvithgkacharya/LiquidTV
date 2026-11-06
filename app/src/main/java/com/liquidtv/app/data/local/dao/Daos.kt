package com.liquidtv.app.data.local.dao

import androidx.room.*
import com.liquidtv.app.data.local.entity.ChannelEntity
import com.liquidtv.app.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Query("SELECT * FROM channels ORDER BY name ASC")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteChannels(): Flow<List<ChannelEntity>>

    @Query("""
        SELECT c.* FROM channels c 
        INNER JOIN (
            SELECT channelId, MAX(watchedAt) as lastWatch FROM watch_history GROUP BY channelId
        ) wh ON c.id = wh.channelId 
        ORDER BY wh.lastWatch DESC 
        LIMIT 50
    """)
    fun getRecentlyWatched(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE groupTitle = :category ORDER BY name ASC")
    fun getChannelsByCategory(category: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE language = :language ORDER BY name ASC")
    fun getChannelsByLanguage(language: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE country = :country ORDER BY name ASC")
    fun getChannelsByCountry(country: String): Flow<List<ChannelEntity>>

    @Query("""
        SELECT * FROM channels 
        WHERE name LIKE '%' || :query || '%'
        OR tvgName LIKE '%' || :query || '%'
        OR groupTitle LIKE '%' || :query || '%'
        OR language LIKE '%' || :query || '%'
        OR country LIKE '%' || :query || '%'
        ORDER BY 
            CASE WHEN name LIKE :query || '%' THEN 0 ELSE 1 END,
            name ASC
        LIMIT 200
    """)
    fun searchChannels(query: String): Flow<List<ChannelEntity>>

    @Query("SELECT DISTINCT groupTitle FROM channels WHERE groupTitle IS NOT NULL ORDER BY groupTitle ASC")
    fun getAllCategoryNames(): Flow<List<String>>

    @Query("SELECT groupTitle, COUNT(*) as count FROM channels WHERE groupTitle IS NOT NULL GROUP BY groupTitle ORDER BY count DESC")
    fun getCategoriesWithCount(): Flow<List<CategoryCount>>

    @Query("SELECT language, COUNT(*) as count FROM channels WHERE language IS NOT NULL GROUP BY language ORDER BY count DESC")
    fun getLanguagesWithCount(): Flow<List<LanguageCount>>

    @Query("SELECT country, COUNT(*) as count FROM channels WHERE country IS NOT NULL GROUP BY country ORDER BY count DESC")
    fun getCountriesWithCount(): Flow<List<CountryCount>>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getChannelById(id: String): ChannelEntity?

    @Query("UPDATE channels SET isFavorite = NOT isFavorite WHERE id = :channelId")
    suspend fun toggleFavorite(channelId: String)

    @Query("UPDATE channels SET lastWatchedAt = :timestamp WHERE id = :channelId")
    suspend fun updateLastWatched(channelId: String, timestamp: Long)

    @Upsert
    suspend fun upsertChannels(channels: List<ChannelEntity>)

    @Upsert
    suspend fun upsertChannel(channel: ChannelEntity)

    @Query("DELETE FROM channels")
    suspend fun deleteAllChannels()

    @Query("SELECT COUNT(*) FROM channels")
    fun getChannelCount(): Flow<Int>
}

@Dao
interface WatchHistoryDao {
    @Insert
    suspend fun insertEntry(entry: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT 100")
    fun getHistory(): Flow<List<WatchHistoryEntity>>

    @Query("DELETE FROM watch_history")
    suspend fun clearHistory()

    @Query("DELETE FROM watch_history WHERE channelId = :channelId")
    suspend fun removeChannelHistory(channelId: String)
}

data class CategoryCount(val groupTitle: String, val count: Int)
data class LanguageCount(val language: String, val count: Int)
data class CountryCount(val country: String, val count: Int)

