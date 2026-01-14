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
        com.pace.legends.data.local.entity.LeaderboardCacheEntity::class,
        com.pace.legends.data.local.entity.UserCacheEntity::class // 🆕 P2 FIX
    ], 
    version = 17,
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
    abstract fun userCacheDao(): UserCacheDao // 🆕 P2 FIX

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
                // 🛡️ SAFE MIGRATION: Veri kaybını önlemek için temp tablo kullanımı
                
                // 2.1 Yeni şemaya uygun geçici tablo oluştur
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `daily_step_log_new` (
                        `epochDay` INTEGER NOT NULL, 
                        `trackId` TEXT NOT NULL, 
                        `userId` TEXT NOT NULL, 
                        `steps` INTEGER NOT NULL, 
                        `distance` REAL NOT NULL, 
                        `dateString` TEXT NOT NULL,
                        PRIMARY KEY(`epochDay`, `trackId`, `userId`)
                    )
                """)
                
                // 2.2 Verileri aktar (dateString -> epochDay dönüşümü)
                // SQLite julianday fonksiyonu ile 'YYYY-MM-DD' formatını epoch day'e çeviriyoruz
                db.execSQL("""
                    INSERT INTO daily_step_log_new (epochDay, trackId, userId, steps, distance, dateString)
                    SELECT 
                        CAST(julianday(dateString) - julianday('1970-01-01') AS INTEGER) AS epochDay,
                        trackId,
                        userId,
                        steps,
                        distance,
                        dateString
                    FROM daily_step_log
                """)
                
                // 2.3 Eski tabloyu sil
                db.execSQL("DROP TABLE IF EXISTS `daily_step_log`")
                
                // 2.4 Yeni tabloyu asıl ismine taşı
                db.execSQL("ALTER TABLE `daily_step_log_new` RENAME TO `daily_step_log`")
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

        // 🆕 v13→v14: Audit Report P1 (Performance Indexes)
        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. DailyStepLog composite index
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_step_log_userId_trackId` ON `daily_step_log` (`userId`, `trackId`)")
                
                // 2. LapHistory composite index
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lap_history_userId_trackId` ON `lap_history` (`userId`, `trackId`)")
                
                // 3. PeriodHistory index
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_period_history_userId` ON `period_history` (`userId`)")
            }
        }

        // 🆕 v14→v15: DAO Analysis Report (Range Query & Filter Indexes)
        val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. DailyStepLog epochDay index (Range queries)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_step_log_epochDay` ON `daily_step_log` (`epochDay`)")
                
                // 2. LapHistory endTime index (Cleanup queries)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lap_history_endTime` ON `lap_history` (`endTime`)")
                
                // 3. PeriodHistory composite filtering index
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_period_history_userId_trackId` ON `period_history` (`userId`, `trackId`)")
            }
        }

        // 🆕 v15→v16: Firestore Audit P2 (Data Integrity / Foreign Keys)
        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Re-create DailyStepLog with FK
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `daily_step_log_new_fk` (
                        `epochDay` INTEGER NOT NULL, 
                        `trackId` TEXT NOT NULL, 
                        `userId` TEXT NOT NULL, 
                        `steps` INTEGER NOT NULL, 
                        `distance` REAL NOT NULL, 
                        `dateString` TEXT NOT NULL,
                        PRIMARY KEY(`epochDay`, `trackId`, `userId`),
                        FOREIGN KEY(`userId`, `trackId`) REFERENCES `user_progress`(`userId`, `trackId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                
                // Copy data ONLY for valid parents (Clean up orphans)
                db.execSQL("""
                    INSERT INTO daily_step_log_new_fk (epochDay, trackId, userId, steps, distance, dateString)
                    SELECT dsl.epochDay, dsl.trackId, dsl.userId, dsl.steps, dsl.distance, dsl.dateString
                    FROM daily_step_log dsl
                    INNER JOIN user_progress up ON dsl.userId = up.userId AND dsl.trackId = up.trackId
                """)
                
                db.execSQL("DROP TABLE IF EXISTS `daily_step_log`")
                db.execSQL("ALTER TABLE `daily_step_log_new_fk` RENAME TO `daily_step_log`")
                
                // Restore indexes for DailyStepLog
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_step_log_userId_trackId` ON `daily_step_log` (`userId`, `trackId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_step_log_epochDay` ON `daily_step_log` (`epochDay`)")

                // 2. Re-create LapHistory with FK
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `lap_history_new_fk` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `trackId` TEXT NOT NULL, 
                        `userId` TEXT NOT NULL, 
                        `startTime` INTEGER NOT NULL, 
                        `endTime` INTEGER NOT NULL, 
                        `durationSeconds` INTEGER NOT NULL, 
                        `totalSteps` INTEGER NOT NULL, 
                        `lapNumber` INTEGER NOT NULL, 
                        `periodId` TEXT NOT NULL,
                        FOREIGN KEY(`userId`, `trackId`) REFERENCES `user_progress`(`userId`, `trackId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                
                // Copy data ONLY for valid parents
                db.execSQL("""
                    INSERT INTO lap_history_new_fk (id, trackId, userId, startTime, endTime, durationSeconds, totalSteps, lapNumber, periodId)
                    SELECT lh.id, lh.trackId, lh.userId, lh.startTime, lh.endTime, lh.durationSeconds, lh.totalSteps, lh.lapNumber, lh.periodId
                    FROM lap_history lh
                    INNER JOIN user_progress up ON lh.userId = up.userId AND lh.trackId = up.trackId
                """)
                
                db.execSQL("DROP TABLE IF EXISTS `lap_history`")
                db.execSQL("ALTER TABLE `lap_history_new_fk` RENAME TO `lap_history`")
                
                // Restore indexes for LapHistory
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lap_history_userId_trackId` ON `lap_history` (`userId`, `trackId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lap_history_endTime` ON `lap_history` (`endTime`)")
            }
        }

        // 🆕 v16→v17: Caching Strategy P2 (User Cache)
        val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_cache` (
                        `userId` TEXT NOT NULL, 
                        `userDataJson` TEXT NOT NULL, 
                        `cachedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`userId`)
                    )
                """)
            }
        }
    }
}
