package com.pace.legends.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

import com.pace.legends.data.local.entity.TrackEntity
import com.pace.legends.data.local.entity.UserProgressEntity
// import com.pace.legends.domain.model.UserBadge // Assuming this stays or moves later, keeping simple

@Database(
    entities = [
        TrackEntity::class, 
        UserProgressEntity::class, 
        com.pace.legends.domain.model.UserBadge::class,
        com.pace.legends.domain.model.LapHistory::class,
        com.pace.legends.domain.model.DailyStepLog::class,
        com.pace.legends.data.local.entity.PeriodHistoryEntity::class,
        com.pace.legends.data.local.entity.LeaderboardCacheEntity::class
    ], 
    version = 13,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProgressDao(): UserProgressDao
    abstract fun userBadgeDao(): UserBadgeDao
    abstract fun lapHistoryDao(): LapHistoryDao
    abstract fun dailyStepLogDao(): DailyStepLogDao
    abstract fun periodHistoryDao(): PeriodHistoryDao
    abstract fun leaderboardCacheDao(): LeaderboardCacheDao

    companion object {
        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Leaderboard Cache Tablosu
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `leaderboard_cache` (
                        `trackId` TEXT NOT NULL,
                        `periodId` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `steps` INTEGER NOT NULL,
                        `rank` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`trackId`, `periodId`, `userId`)
                    )
                """)
                
                // 2. DailyStepLog Schema Change (String Date -> Long EpochDay)
                // Mevcut tabloyu silip yeniden oluşturuyoruz (Veri kaybı kabul edildi - Dev Phase)
                db.execSQL("DROP TABLE IF EXISTS `daily_step_log`")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `daily_step_log` (
                        `epochDay` INTEGER NOT NULL, 
                        `trackId` TEXT NOT NULL, 
                        `userId` TEXT NOT NULL, 
                        `steps` INTEGER NOT NULL, 
                        `distance` REAL NOT NULL, 
                        `dateString` TEXT NOT NULL,
                        PRIMARY KEY(`epochDay`, `trackId`, `userId`)
                    )
                """)
            }
        }
        
        // 🆕 v12→v13: Performance index'leri
        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Sync sorguları için isSynced index'i (Full Table Scan önleme)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_progress_isSynced` ON `user_progress` (`isSynced`)")
                
                // Pist detay sayfası açılışı için trackId index'i
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_progress_trackId` ON `user_progress` (`trackId`)")
            }
        }
    }
}
