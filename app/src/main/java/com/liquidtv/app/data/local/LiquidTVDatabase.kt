package com.liquidtv.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.liquidtv.app.data.local.dao.ChannelDao
import com.liquidtv.app.data.local.dao.WatchHistoryDao
import com.liquidtv.app.data.local.entity.ChannelEntity
import com.liquidtv.app.data.local.entity.WatchHistoryEntity

@Database(
    entities = [ChannelEntity::class, WatchHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LiquidTVDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun watchHistoryDao(): WatchHistoryDao
}

