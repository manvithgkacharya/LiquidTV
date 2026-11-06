package com.liquidtv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val tvgId: String?,
    val tvgName: String?,
    val country: String?,
    val language: String?,
    val channelNumber: Int?,
    val isFavorite: Boolean = false,
    val lastWatchedAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val channelName: String,
    val watchedAt: Long = System.currentTimeMillis(),
)

