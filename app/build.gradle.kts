import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.hiltAndroid)
    alias(libs.plugins.ksp)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

// KSP + Hilt: Disable Hilt's aggregating task to prevent duplicate class generation
hilt {
    enableAggregatingTask = false
}


android {
    namespace = "com.pace.legends"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pace.legends"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        //noinspection WrongGradleMethod
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
        
        // API Key'i local.properties'den oku ve Manifest'e aktar
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { localProperties.load(it) }
        }
        val mapsApiKey = localProperties.getProperty("MAPS_API_KEY") ?: ""
        
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")

        // AdMob App ID (Default Test ID)
        val admobAppId = localProperties.getProperty("ADMOB_APP_ID") ?: "ca-app-pub-3940256099942544~3347511713"
        manifestPlaceholders["ADMOB_APP_ID"] = admobAppId
        
        // P1 FIX: Secrets from local.properties
        val webClientId = localProperties.getProperty("WEB_CLIENT_ID") ?: ""
        buildConfigField("String", "WEB_CLIENT_ID", "\"$webClientId\"")
        
        val adUnitId = localProperties.getProperty("ADMOB_AD_UNIT_ID") ?: "ca-app-pub-3940256099942544/1033173712"
        buildConfigField("String", "ADMOB_AD_UNIT_ID", "\"$adUnitId\"")
        
        // 🆕 P1 FIX: RevenueCat API Key from local.properties
        val revenueCatApiKey = localProperties.getProperty("REVENUECAT_API_KEY") ?: ""
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true  // BuildConfig sınıfını aktif et
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    
    // 🆕 P1: Linting Configuration
    lint {
        abortOnError = false        // Hata olsa bile build devam etsin (Raporda görünür)
        checkReleaseBuilds = false  // Release build sırasında lint yapma
        textReport = true           // Konsol çıktısı
        xmlReport = true            // CI/CD için rapor
        htmlReport = true           // Okunabilir HTML raporu
        
        // Gereksiz uyarıları kapat
        disable += setOf("MissingTranslation", "GoogleAppIndexingWarning")
        // Kritik hataları "Error" olarak işaretle
        error += setOf("HardcodedText", "UnknownNullness")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation("androidx.lifecycle:lifecycle-service:2.8.2")
    
    // Compose Bundle
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Firebase Bundle
    implementation(platform(libs.firebase.bom))
    implementation(libs.bundles.firebase)

    // Maps
    implementation(libs.maps.compose)
    implementation(libs.android.maps.utils)
    
    // 🆕 GPS Location Services (Anti-Cheat)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    
    // Coroutines Play Services & Gson
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.google.gson)

    // Monetization
    implementation(libs.play.services.ads)
    implementation("com.revenuecat.purchases:purchases:6.9.0")

    // Credential Manager (Google Sign-In)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)

    // Health Connect
    implementation(libs.health.connect)

    // Security (EncryptedSharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3") // 🆕 P2: Coroutine Tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // Firebase Crashlytics & Analytics
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")
}

