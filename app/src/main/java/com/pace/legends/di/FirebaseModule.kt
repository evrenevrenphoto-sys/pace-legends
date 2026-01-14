package com.pace.legends.di

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.firebase.functions.ktx.functions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        val db = Firebase.firestore
        
        // ⚠️ P1 FIX: Persistence sadece DEBUG modunda kapalı (Emulator testi için)
        // Production'da Offline-First UX için persistence AKTİF
        if (com.pace.legends.BuildConfig.DEBUG) {
            val settings = com.google.firebase.firestore.ktx.firestoreSettings { 
                isPersistenceEnabled = false 
            }
            db.firestoreSettings = settings
            android.util.Log.d("FirebaseModule", "🔧 Firestore persistence DISABLED (Debug Mode)")
        }
        // Production: Default persistence = true (No action needed)
        
        return db
    }

    @Provides
    @Singleton
    fun provideRemoteConfig(): FirebaseRemoteConfig {
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 // 1 hour for prod
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        return remoteConfig
    }

    @Provides
    @Singleton
    fun provideAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFunctions(): com.google.firebase.functions.FirebaseFunctions {
        return Firebase.functions
    }
}
