package com.pace.legends.di

import android.content.Context
import androidx.room.Room
import com.pace.legends.data.local.AppDatabase

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): com.google.gson.Gson {
        return com.google.gson.Gson()
    }

    // 🆕 Mükemmellik: Clock injection - Time Travel testleri için
    @Provides
    @Singleton
    fun provideClock(): java.time.Clock {
        return java.time.Clock.systemDefaultZone()
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        val builder = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "pace_legends_db"
        )
        .addMigrations(AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14, AppDatabase.MIGRATION_14_15, AppDatabase.MIGRATION_15_16, AppDatabase.MIGRATION_16_17)
        
        // ⚠️ Sadece DEBUG modunda yıkıcı migrasyona izin ver
        // Production'da migration eksikse crash olur ama veri SİLİNMEZ
        if (com.pace.legends.BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration()
        }
        
        return builder.build()
    }
    @Provides
    @Singleton
    fun provideUserBadgeDao(appDatabase: AppDatabase): com.pace.legends.data.local.UserBadgeDao {
        return appDatabase.userBadgeDao()
    }

    @Provides
    @Singleton
    fun provideDailyStepLogDao(appDatabase: AppDatabase): com.pace.legends.data.local.DailyStepLogDao {
        return appDatabase.dailyStepLogDao()
    }

    @Provides
    @Singleton
    fun providePeriodHistoryDao(appDatabase: AppDatabase): com.pace.legends.data.local.PeriodHistoryDao {
        return appDatabase.periodHistoryDao()
    }

    @Provides
    @Singleton
    fun provideUserProgressDao(appDatabase: AppDatabase): com.pace.legends.data.local.UserProgressDao {
        return appDatabase.userProgressDao()
    }

    @Provides
    @Singleton
    fun provideLapHistoryDao(appDatabase: AppDatabase): com.pace.legends.data.local.LapHistoryDao {
        return appDatabase.lapHistoryDao()
    }

    @Provides
    @Singleton
    fun provideLeaderboardCacheDao(appDatabase: AppDatabase): com.pace.legends.data.local.LeaderboardCacheDao {
        return appDatabase.leaderboardCacheDao()
    }

    @Provides
    @Singleton
    fun provideUserCacheDao(appDatabase: AppDatabase): com.pace.legends.data.local.UserCacheDao {
        return appDatabase.userCacheDao()
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): android.content.SharedPreferences {
        // P2 FIX: EncryptedSharedPreferences ile KeyStore sorunları için fallback
        return try {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()

            androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "pace_legends_prefs",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // 🛡️ CRASH PREVENTION: Keystore bozulması durumunda
            // Samsung/Xiaomi cihazlarda güncelleme sonrası oluşabilir
            android.util.Log.e("AppModule", "❌ EncryptedSharedPreferences failed: ${e.message}")
            android.util.Log.w("AppModule", "⚠️ Using fallback prefs - user may need to re-login")
            
            // Bozuk encrypted prefs dosyasını temizle
            context.getSharedPreferences("pace_legends_prefs", Context.MODE_PRIVATE)
                .edit().clear().apply()
            
            // Fallback: Normal SharedPreferences (güvenlik düşer ama crash'ten iyidir)
            context.getSharedPreferences("pace_legends_prefs_fallback", Context.MODE_PRIVATE)
        }
    }
}
