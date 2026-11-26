package com.liquidtv.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.liquidtv.app.data.local.dao.*
import com.liquidtv.app.data.local.entity.*

@Database(
    entities = [
        SubscriptionEntity::class,
        ChannelEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        CategoryEntity::class,
        WatchHistoryEntity::class,
        EpgProgramEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class LiquidTVDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun channelDao(): ChannelDao
    abstract fun seriesDao(): SeriesDao
    abstract fun categoryDao(): CategoryDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun epgDao(): EpgDao

    companion object {
        /**
         * Safe non-destructive migration from v1 (single-playlist channels + watch_history)
         * to v2 (multi-subscription isolated schema with VOD, Series, Episodes, Categories, EPG).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `subscriptions` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `serverUrl` TEXT NOT NULL,
                        `username` TEXT,
                        `encryptedPassword` TEXT,
                        `encryptedM3uUrl` TEXT,
                        `userAgent` TEXT,
                        `referer` TEXT,
                        `xmltvUrl` TEXT,
                        `isActive` INTEGER NOT NULL,
                        `connectionStatus` TEXT NOT NULL,
                        `lastError` TEXT,
                        `accountStatus` TEXT,
                        `expDateEpochSec` INTEGER,
                        `maxConnections` INTEGER,
                        `activeConnections` INTEGER,
                        `isTrial` INTEGER,
                        `lastSyncedAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )

                // Rebuild channels table with foreign key, indices, and new metadata columns
                // while preserving any existing user-entered channels cleanly (excluding default iptv-org rows).
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `channels_new` (
                        `id` TEXT NOT NULL,
                        `subscriptionId` TEXT NOT NULL,
                        `externalId` TEXT,
                        `mediaType` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `streamUrl` TEXT NOT NULL,
                        `logoUrl` TEXT,
                        `groupTitle` TEXT,
                        `categoryId` TEXT,
                        `tvgId` TEXT,
                        `tvgName` TEXT,
                        `country` TEXT,
                        `language` TEXT,
                        `channelNumber` INTEGER,
                        `httpHeadersJson` TEXT,
                        `containerExtension` TEXT,
                        `plot` TEXT,
                        `rating` TEXT,
                        `releaseYear` TEXT,
                        `durationSec` INTEGER,
                        `resumePositionMs` INTEGER NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `lastWatchedAt` INTEGER,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`subscriptionId`) REFERENCES `subscriptions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE IF EXISTS `channels`")
                db.execSQL("ALTER TABLE `channels_new` RENAME TO `channels`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_subscriptionId` ON `channels` (`subscriptionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_subscriptionId_mediaType` ON `channels` (`subscriptionId`, `mediaType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_subscriptionId_groupTitle` ON `channels` (`subscriptionId`, `groupTitle`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_subscriptionId_isFavorite` ON `channels` (`subscriptionId`, `isFavorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_subscriptionId_tvgId` ON `channels` (`subscriptionId`, `tvgId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_subscriptionId_name` ON `channels` (`subscriptionId`, `name`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `series` (
                        `id` TEXT NOT NULL,
                        `subscriptionId` TEXT NOT NULL,
                        `externalSeriesId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `coverUrl` TEXT,
                        `categoryId` TEXT,
                        `categoryName` TEXT,
                        `plot` TEXT,
                        `cast` TEXT,
                        `director` TEXT,
                        `genre` TEXT,
                        `releaseDate` TEXT,
                        `rating` TEXT,
                        `lastModified` INTEGER,
                        `isFavorite` INTEGER NOT NULL,
                        `lastWatchedAt` INTEGER,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`subscriptionId`) REFERENCES `subscriptions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_subscriptionId` ON `series` (`subscriptionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_subscriptionId_categoryName` ON `series` (`subscriptionId`, `categoryName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_subscriptionId_isFavorite` ON `series` (`subscriptionId`, `isFavorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_series_subscriptionId_name` ON `series` (`subscriptionId`, `name`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `episodes` (
                        `id` TEXT NOT NULL,
                        `subscriptionId` TEXT NOT NULL,
                        `seriesId` TEXT NOT NULL,
                        `externalEpisodeId` TEXT NOT NULL,
                        `seasonNumber` INTEGER NOT NULL,
                        `episodeNumber` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `streamUrl` TEXT NOT NULL,
                        `containerExtension` TEXT,
                        `plot` TEXT,
                        `durationSec` INTEGER,
                        `coverUrl` TEXT,
                        `lastWatchedAt` INTEGER,
                        `resumePositionMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`subscriptionId`) REFERENCES `subscriptions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`seriesId`) REFERENCES `series`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_subscriptionId` ON `episodes` (`subscriptionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_seriesId_seasonNumber_episodeNumber` ON `episodes` (`seriesId`, `seasonNumber`, `episodeNumber`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `categories` (
                        `id` TEXT NOT NULL,
                        `subscriptionId` TEXT NOT NULL,
                        `externalCategoryId` TEXT,
                        `contentType` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `isLocked` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`subscriptionId`) REFERENCES `subscriptions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_subscriptionId_contentType` ON `categories` (`subscriptionId`, `contentType`)")

                db.execSQL("ALTER TABLE `watch_history` ADD COLUMN `subscriptionId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `watch_history` ADD COLUMN `mediaType` TEXT NOT NULL DEFAULT 'LIVE'")
                db.execSQL("ALTER TABLE `watch_history` ADD COLUMN `positionMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `watch_history` ADD COLUMN `durationMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_history_subscriptionId_watchedAt` ON `watch_history` (`subscriptionId`, `watchedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_history_channelId` ON `watch_history` (`channelId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `epg_programs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `subscriptionId` TEXT NOT NULL,
                        `channelTvgId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT,
                        `startTimeMs` INTEGER NOT NULL,
                        `endTimeMs` INTEGER NOT NULL,
                        `category` TEXT,
                        FOREIGN KEY(`subscriptionId`) REFERENCES `subscriptions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programs_subscriptionId_channelTvgId_startTimeMs_endTimeMs` ON `epg_programs` (`subscriptionId`, `channelTvgId`, `startTimeMs`, `endTimeMs`)")
            }
        }

        /**
         * Safe non-destructive migration from v2 to v3:
         * - Removes legacy M3U subscriptions (along with their child rows via CASCADE)
         * - Preserves all existing Xtream subscriptions, encrypted passwords, channels, series,
         *   episodes, categories, EPG programs, and watch history cleanly
         * - Adds origin ('NORMAL_URL' | 'PANEL_SERVER'), panelServerId, and panelServerName
         * - Removes legacy M3U-only columns without triggering FK cascade on surviving Xtream data.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Remove legacy M3U child rows explicitly first (in case PRAGMA foreign_keys is OFF during onUpgrade), then M3U subscriptions
                db.execSQL("DELETE FROM `epg_programs` WHERE `subscriptionId` IN (SELECT `id` FROM `subscriptions` WHERE `type` != 'XTREAM')")
                db.execSQL("DELETE FROM `categories` WHERE `subscriptionId` IN (SELECT `id` FROM `subscriptions` WHERE `type` != 'XTREAM')")
                db.execSQL("DELETE FROM `episodes` WHERE `subscriptionId` IN (SELECT `id` FROM `subscriptions` WHERE `type` != 'XTREAM')")
                db.execSQL("DELETE FROM `series` WHERE `subscriptionId` IN (SELECT `id` FROM `subscriptions` WHERE `type` != 'XTREAM')")
                db.execSQL("DELETE FROM `channels` WHERE `subscriptionId` IN (SELECT `id` FROM `subscriptions` WHERE `type` != 'XTREAM')")
                db.execSQL("DELETE FROM `subscriptions` WHERE `type` != 'XTREAM'")

                // 2. Preserve all surviving child rows into TEMP tables so dropping `subscriptions` does not cascade-delete them
                db.execSQL("CREATE TEMP TABLE `_bak_channels` AS SELECT * FROM `channels`")
                db.execSQL("CREATE TEMP TABLE `_bak_series` AS SELECT * FROM `series`")
                db.execSQL("CREATE TEMP TABLE `_bak_episodes` AS SELECT * FROM `episodes`")
                db.execSQL("CREATE TEMP TABLE `_bak_categories` AS SELECT * FROM `categories`")
                db.execSQL("CREATE TEMP TABLE `_bak_epg_programs` AS SELECT * FROM `epg_programs`")

                // 3. Rebuild `subscriptions` with v3 columns (defaulting existing Xtream rows to NORMAL_URL)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `subscriptions_new` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `origin` TEXT NOT NULL,
                        `serverUrl` TEXT NOT NULL,
                        `panelServerId` TEXT,
                        `panelServerName` TEXT,
                        `username` TEXT,
                        `encryptedPassword` TEXT,
                        `xmltvUrl` TEXT,
                        `isActive` INTEGER NOT NULL,
                        `connectionStatus` TEXT NOT NULL,
                        `lastError` TEXT,
                        `accountStatus` TEXT,
                        `expDateEpochSec` INTEGER,
                        `maxConnections` INTEGER,
                        `activeConnections` INTEGER,
                        `isTrial` INTEGER,
                        `lastSyncedAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    INSERT INTO `subscriptions_new` (
                        `id`, `name`, `type`, `origin`, `serverUrl`, `panelServerId`, `panelServerName`,
                        `username`, `encryptedPassword`, `xmltvUrl`, `isActive`, `connectionStatus`,
                        `lastError`, `accountStatus`, `expDateEpochSec`, `maxConnections`,
                        `activeConnections`, `isTrial`, `lastSyncedAt`, `createdAt`
                    )
                    SELECT
                        `id`, `name`, `type`, 'NORMAL_URL', `serverUrl`, NULL, NULL,
                        `username`, `encryptedPassword`, `xmltvUrl`, `isActive`, `connectionStatus`,
                        `lastError`, `accountStatus`, `expDateEpochSec`, `maxConnections`,
                        `activeConnections`, `isTrial`, `lastSyncedAt`, `createdAt`
                    FROM `subscriptions`
                    """.trimIndent(),
                )

                db.execSQL("DROP TABLE `subscriptions`")
                db.execSQL("ALTER TABLE `subscriptions_new` RENAME TO `subscriptions`")

                // Ensure one surviving Xtream subscription stays active if an active M3U was removed
                db.execSQL(
                    """
                    UPDATE `subscriptions`
                    SET `isActive` = 1
                    WHERE `id` = (SELECT `id` FROM `subscriptions` ORDER BY `createdAt` ASC LIMIT 1)
                      AND NOT EXISTS (SELECT 1 FROM `subscriptions` WHERE `isActive` = 1)
                    """.trimIndent(),
                )

                // 4. Restore surviving child rows in FK parent-before-child order (OR IGNORE handles PRAGMA foreign_keys = OFF cleanly)
                db.execSQL("INSERT OR IGNORE INTO `channels` SELECT * FROM `_bak_channels`")
                db.execSQL("INSERT OR IGNORE INTO `series` SELECT * FROM `_bak_series`")
                db.execSQL("INSERT OR IGNORE INTO `episodes` SELECT * FROM `_bak_episodes`")
                db.execSQL("INSERT OR IGNORE INTO `categories` SELECT * FROM `_bak_categories`")
                db.execSQL("INSERT OR IGNORE INTO `epg_programs` SELECT * FROM `_bak_epg_programs`")

                db.execSQL("DROP TABLE `_bak_channels`")
                db.execSQL("DROP TABLE `_bak_series`")
                db.execSQL("DROP TABLE `_bak_episodes`")
                db.execSQL("DROP TABLE `_bak_categories`")
                db.execSQL("DROP TABLE `_bak_epg_programs`")
            }
        }
    }
}
