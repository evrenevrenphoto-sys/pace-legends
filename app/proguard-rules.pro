# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# ================== GENEL KURALLAR ==================

# Hata raporlarında okunabilir stack trace için
-keepattributes SourceFile,LineNumberTable

# Obfuscation sonrası mapping dosyasını sakla
-printmapping mapping.txt

# ================== KOTLIN ==================
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlinx.coroutines.**

# ================== HILT / DAGGER ==================
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}

# ================== ROOM DATABASE ==================
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# ================== FIREBASE ==================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firestore model sınıfları (PropertyName annotation korunmalı)
-keepclassmembers class com.pace.legends.domain.model.** {
    *;
}

# ================== GSON (JSON PARSING) ==================
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Gson ile serialize edilen modellerimiz
-keep class com.pace.legends.domain.model.** { *; }

# ================== GOOGLE MAPS ==================
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.maps.android.** { *; }

# ================== REVENUECAT (SUBSCRIPTIONS) ==================
-keep class com.revenuecat.** { *; }
-dontwarn com.revenuecat.**

# ================== ADMOB ==================
-keep class com.google.android.gms.ads.** { *; }

# ================== COMPOSE ==================
# Compose stability için bu genellikle gereksiz ama garanti olsun
-dontwarn androidx.compose.**

# ================== API KEY SECURITY ==================
# Prevent API keys from appearing in plain text in release builds
# NOTE: This only removes dead code. Real protection requires:
# 1. Google Cloud Console API key restrictions (package name + SHA-1)
# 2. Key rotation if keys were previously exposed
-assumenosideeffects class com.pace.legends.BuildConfig {
    public static final java.lang.String MAPS_API_KEY;
    public static final java.lang.String WEB_CLIENT_ID;
    public static final java.lang.String ADMOB_AD_UNIT_ID;
}

# ================== API KEY SECURITY ==================
# Cloud Console'da package + SHA-1 kısıtlaması ZORUNLU
# assumenosideeffects kullanılmadı - Maps çağrılarını silebilir
-keep class com.pace.legends.BuildConfig { *; }

# ================== ANTI-TAMPER (Opsiyonel) ==================
# Eğer ileride root/tamper detection eklerseniz:
# -keep class com.pace.legends.security.** { *; }
