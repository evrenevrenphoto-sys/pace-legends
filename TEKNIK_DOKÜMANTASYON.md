# 📋 PACE LEGENDS - KAPSAMLI TEKNİK DOKÜMANTASYON

**Proje:** Pace Legends - Adım Sayar ve Yarış Uygulaması  
**Platform:** Android (Kotlin, Jetpack Compose)  
**Mimari:** Clean Architecture + MVVM  
**Versiyon:** 1.0  
**Tarih:** 2026

---

## 📑 İÇİNDEKİLER

1. [Yönetici Özeti](#1-yönetici-özeti)
2. [Teknik Yığın (Tech Stack) Analizi](#2-teknik-yığın-tech-stack-analizi)
3. [Proje Dosya Yapısı (File Tree)](#3-proje-dosya-yapısı-file-tree)
4. [Detaylı İşleyiş Senaryoları (User Flows)](#4-detaylı-işleyiş-senaryoları-user-flows)
5. [Veri Akış Diyagramı (Data Flow)](#5-veri-akış-diyagramı-data-flow)
6. [Veritabanı Şeması](#6-veritabanı-şeması)
7. [Eksiklik ve Risk Analizi](#7-eksiklik-ve-risk-analizi)

---

## 1. YÖNETİCİ ÖZETİ

### 1.1 Proje Amacı

**Pace Legends**, kullanıcıların günlük fiziksel aktivitelerini (adım sayma) Formula 1 temalı bir yarış deneyimine dönüştüren, gamification prensiplerini sağlık ve fitness alanına uygulayan bir Android mobil uygulamasıdır.

### 1.2 Hedef Kitle

Uygulama, fiziksel aktiviteyi eğlenceli bir rekabet deneyimine dönüştürmek isteyen, F1 meraklısı veya yarış temasını seven, sosyal rekabet ve başarı sistemi ile motive olan kullanıcıları hedeflemektedir. Yaş grubu olarak geniş bir kitleye hitap ederken, özellikle 18-45 yaş arası aktif kullanıcılar odak alınmıştır.

### 1.3 Temel Çözüm Önerisi

Uygulama, Android Health Connect entegrasyonu ile gerçek zamanlı adım verilerini toplar ve bu verileri:

- **Sanal F1 Pistleri:** Gerçek F1 pist rotaları (İstanbul Park, Spa-Francorchamps vb.) üzerinde ilerleme gösterimi
- **Lig Sistemi:** 7 kademeli rekabet sistemi (Eleme → Bronz → Gümüş → Altın → Platin → Elmas → Efsane)
- **Dönemsel Yarışmalar:** Periyodik yarışmalar (aylık/özel süreler) ile sürekli rekabet
- **Başarı Sistemi:** Rozetler, coin ödülleri, podyum finişleri
- **Liderlik Tabloları:** Global ve lig bazlı sıralamalar

şeklinde kullanıcı deneyimine dönüştürür.

### 1.4 Temel Özellikler

- **Gerçek Zamanlı Adım Takibi:** Health Connect API ile doğrulanmış adım verisi
- **Offline-First Yaklaşım:** Room Database ile lokal cache ve senkronizasyon
- **Anti-Cheat Sistemi:** Cloud Functions ile server-side doğrulama
- **Dinamik Konfigürasyon:** Firebase Remote Config ile anında güncelleme
- **Monetizasyon:** AdMob reklamları ve RevenueCat abonelik sistemi

---

## 2. TEKNİK YIĞIN (TECH STACK) ANALİZİ

### 2.1 Programlama Dili

**Kotlin 1.9.22**

**Neden Kotlin?**
- Modern, güvenli ve expressif bir dil
- Null safety özellikleri ile runtime hatalarının önlenmesi
- Coroutine desteği ile asenkron programlamada üstünlük
- Android'in birinci sınıf dil desteği
- Java ile tam uyumluluk

### 2.2 UI Framework

**Jetpack Compose (BOM 2024.09.00)**

**Neden Jetpack Compose?**
- Declarative UI yaklaşımı ile daha az kod ve daha temiz mantık
- State-driven reactive UI
- Modern Material Design 3 desteği
- XML yerine Kotlin DSL ile type-safe UI
- Performanslı recomposition mekanizması
- Preview desteği ile hızlı geliştirme

**Kullanılan Bileşenler:**
- `androidx.compose.ui:ui`
- `androidx.compose.material3:material3`
- `androidx.compose.ui:ui-tooling-preview`

### 2.3 Mimari

**Clean Architecture + MVVM (Model-View-ViewModel)**

**Katman Yapısı:**
```
UI Layer (Compose Screens)
    ↓
ViewModel Layer (State Management)
    ↓
Domain Layer (Business Logic, Use Cases, Repository Interfaces)
    ↓
Data Layer (Repository Implementations, Room DAOs, Firebase)
    ↓
External Services (Health Connect, Firebase, Maps)
```

**Neden Clean Architecture?**
- **Separation of Concerns:** Her katmanın tek bir sorumluluğu var
- **Testability:** Domain layer testleri external dependency'lerden bağımsız
- **Maintainability:** Kod değişiklikleri izole edilebilir
- **Scalability:** Yeni özellikler kolayca eklenebilir
- **Dependency Rule:** Dış katmanlar iç katmanları bilmez

### 2.4 Dependency Injection

**Hilt (Dagger Hilt) 2.50**

**Neden Hilt?**
- Dagger'ın Android için sadeleştirilmiş versiyonu
- Standart Android lifecycle bileşenleri ile entegrasyon
- Compile-time dependency injection (performans)
- Annotation-based configuration
- ViewModel, Worker, Service injection desteği

**Kullanılan Modüller:**
- `@Singleton` için `@InstallIn(SingletonComponent::class)`
- ViewModel injection: `@HiltViewModel`
- Worker injection: `@HiltWorker`
- Navigation Compose integration: `hilt-navigation-compose`

### 2.5 Local Database

**Room Database 2.6.1**

**Neden Room?**
- SQLite üzerinde abstraction layer
- Type-safe SQL queries (compile-time kontrol)
- Kotlin Coroutine ve Flow desteği
- Migration yönetimi
- DAO pattern ile temiz data access

**Kullanılan Özellikler:**
- Entity definitions (`@Entity`)
- DAO interfaces (`@Dao`)
- Database class (`@Database`)
- Type converters (`@TypeConverter`)
- Migration strategies

### 2.6 Cloud Database

**Firebase Firestore**

**Neden Firestore?**
- Real-time synchronization
- Offline persistence
- Scalable NoSQL database
- Serverless backend
- Security Rules ile güvenlik
- Cloud Functions entegrasyonu

**Kullanılan Koleksiyonlar:**
- `users/{userId}` - Kullanıcı profilleri
- `leaderboards/{trackId}/monthly/{periodId}/entries/{userId}` - Global liderlik tabloları
- `leagues/{leagueId}/members/{userId}` - Lig üyelikleri
- `qualifying/{poolId}/entries/{userId}` - Eleme havuzu
- `antiCheatLogs/{logId}` - Anti-cheat logları

### 2.7 Authentication

**Firebase Authentication**

**Kullanılan Yöntemler:**
- Google Sign-In (Credential Manager API)
- Anonymous Authentication

**Neden Firebase Auth?**
- Güvenli authentication servisi
- Multiple provider desteği
- Token yönetimi
- Account linking desteği

### 2.8 Backend Functions

**Firebase Cloud Functions (TypeScript)**

**Kullanılan Fonksiyonlar:**
- `onUserStepSync` - Adım senkronizasyonu tetikleyicisi
- `onLeaderboardUpdate` - Anti-cheat analizi
- `processPeriodEnd` - Dönem sonu işlemleri
- `unflagUser` - Admin fonksiyonu

**Neden Cloud Functions?**
- Server-side logic execution
- Real-time database triggers
- Anti-cheat kontrolü
- Scheduled tasks (cron jobs)
- Admin operations

### 2.9 Health Data

**Android Health Connect 1.1.0**

**Neden Health Connect?**
- Unified health data platform
- Multiple data source aggregation (phone + wearable)
- Privacy-first approach
- Permissions-based access
- Aggregate API ile deduplication

**Kullanılan Data Types:**
- `Steps` (READ_STEPS)
- `TotalCaloriesBurned` (READ_TOTAL_CALORIES_BURNED)

### 2.10 Maps Integration

**Google Maps SDK (Maps Compose 4.3.0)**

**Neden Google Maps?**
- F1 pist rotalarının görselleştirilmesi
- Kullanıcı konumunun gösterilmesi
- Polylines ile pist çizgileri
- Marker ve info windows

### 2.11 Background Work

**WorkManager 2.9.0**

**Neden WorkManager?**
- Guaranteed execution (Android 12+)
- Battery-efficient scheduling
- Network constraints
- HiltWorker injection desteği
- Periodic work support

**Kullanılan Worker:**
- `DataSyncWorker` - 15 dakikada bir periyodik sync

### 2.12 Monetization

**AdMob 22.6.0 + RevenueCat 6.9.0**

**AdMob:**
- Interstitial ads
- Banner ads (future)
- Rewarded ads (future)

**RevenueCat:**
- In-app purchase management
- Subscription handling
- Cross-platform sync

### 2.13 Configuration Management

**Firebase Remote Config**

**Neden Remote Config?**
- A/B testing desteği
- Feature flags
- Dynamic configuration (lig atamaları, pist mapping)
- Instant updates (app restart gerektirmez)

**Kullanılan Parametreler:**
- `tier_tracks` - Lig → Pist mapping
- `show_interstitial_ads` - Reklam kontrolü
- `ad_frequency` - Reklam sıklığı
- `period_config` - Dönem yapılandırması

### 2.14 Build System

**Gradle (Kotlin DSL) + Version Catalog**

**Versiyon Yönetimi:**
- `gradle/libs.versions.toml` - Merkezi dependency yönetimi
- Kotlin DSL ile type-safe build scripts

**Kullanılan Plugins:**
- `com.android.application`
- `org.jetbrains.kotlin.android`
- `com.google.dagger.hilt.android`
- `com.google.devtools.ksp` (Kotlin Symbol Processing)
- `com.google.gms.google-services`
- `com.google.firebase.crashlytics`

### 2.15 Testing

**Mevcut Test Framework'ler:**
- JUnit 4.13.2
- AndroidX Test (JUnit, Espresso)
- Compose UI Testing

**Not:** Test coverage genişletilmesi önerilir (bkz. Risk Analizi)

---

## 3. PROJE DOSYA YAPISI (FILE TREE)

```
app/src/main/java/com/pace/legends/
│
├── 📱 PaceLegendsApp.kt              # Application class (Hilt, WorkManager setup)
├── 📱 MainActivity.kt                 # Main activity (Navigation, Permission handling)
│
├── 📂 data/                           # DATA LAYER
│   ├── 📂 config/                     # Configuration classes
│   │
│   ├── 📂 local/                      # Room Database
│   │   ├── AppDatabase.kt            # Database definition (@Database)
│   │   ├── Converters.kt             # Type converters (Date, Map, etc.)
│   │   ├── UserProgressDao.kt        # UserProgress CRUD operations
│   │   ├── UserBadgeDao.kt           # UserBadge CRUD operations
│   │   ├── LapHistoryDao.kt          # LapHistory CRUD operations
│   │   ├── DailyStepLogDao.kt        # DailyStepLog CRUD operations
│   │   └── PeriodHistoryDao.kt       # PeriodHistory CRUD operations
│   │
│   ├── 📂 monetization/               # Monetization logic
│   │   ├── AdManager.kt              # AdMob ad management
│   │   └── SubscriptionManager.kt    # RevenueCat subscription management
│   │
│   └── 📂 repository/                 # Repository implementations
│       ├── StepRepositoryImpl.kt     # StepRepository implementation (Health Connect + Room)
│       ├── FirebaseAuthRepository.kt  # AuthRepository implementation (Firebase Auth)
│       ├── FirebaseUserRepository.kt  # UserRepository implementation (Firestore)
│       ├── FirebaseLeaderboardRepository.kt  # LeaderboardRepository implementation
│       ├── FirebaseLeagueRepository.kt       # LeagueRepository implementation
│       └── FirebaseTrackRepository.kt        # TrackRepository implementation
│
├── 📂 di/                             # DEPENDENCY INJECTION
│   ├── AppModule.kt                  # Application-level dependencies (Room, WorkManager)
│   ├── FirebaseModule.kt             # Firebase dependencies (Firestore, Auth, Remote Config)
│   └── RepositoryModule.kt           # Repository bindings (interface → implementation)
│
├── 📂 domain/                         # DOMAIN LAYER (Business Logic)
│   ├── 📂 manager/                    # Business logic managers
│   │   ├── HealthConnectManager.kt   # Health Connect API wrapper
│   │   ├── StepSyncManager.kt        # Step synchronization logic
│   │   ├── LeagueManager.kt          # League assignment and promotion logic
│   │   ├── BadgeManager.kt           # Badge awarding logic
│   │   ├── RewardManager.kt          # Coin and reward logic
│   │   ├── RemoteConfigManager.kt    # Remote Config wrapper
│   │   ├── SafetyCarManager.kt       # Safety car (period transition) logic
│   │   └── TrackLockedException.kt   # Custom exception
│   │
│   ├── 📂 model/                      # Domain models (Entities, Data classes)
│   │   ├── User.kt                   # User domain model
│   │   ├── UserProgress.kt           # UserProgress entity (Room)
│   │   ├── Track.kt                  # Track entity (Room + Firestore)
│   │   ├── UserBadge.kt              # UserBadge entity (Room)
│   │   ├── LapHistory.kt             # LapHistory entity (Room)
│   │   ├── DailyStepLog.kt           # DailyStepLog entity (Room)
│   │   ├── PeriodHistory.kt          # PeriodHistory entity (Room)
│   │   ├── LeagueTier.kt             # League tier enum
│   │   ├── Sector.kt                 # Track sector model
│   │   ├── AppConfig.kt              # App configuration model
│   │   ├── AvatarFrame.kt            # Avatar frame model
│   │   ├── CoinReward.kt             # Coin reward model
│   │   ├── DownloadState.kt          # Track download state
│   │   ├── LockStatusResult.kt       # Track lock status
│   │   └── PitStopMessages.kt        # Pit stop messages
│   │
│   ├── 📂 repository/                 # Repository interfaces (Domain contracts)
│   │   ├── AuthRepository.kt         # Authentication interface
│   │   ├── UserRepository.kt         # User data interface
│   │   ├── StepRepository.kt         # Step data interface
│   │   ├── TrackRepository.kt        # Track data interface
│   │   ├── LeaderboardRepository.kt  # Leaderboard interface
│   │   ├── LeagueRepository.kt       # League interface
│   │   └── StatsRepository.kt        # Statistics interface (concrete class)
│   │
│   ├── 📂 usecase/                    # Use cases (şu an boş, genişletilebilir)
│   │
│   └── 📂 util/                       # Domain utilities
│       └── (utility classes)
│
├── 📂 ui/                             # UI LAYER (Jetpack Compose)
│   ├── MainViewModel.kt              # Main view model (Navigation state, Permissions)
│   │
│   ├── 📂 auth/                       # Authentication screens
│   │   ├── LoginScreen.kt            # Login/Register screen
│   │   └── LoginViewModel.kt         # Login view model
│   │
│   ├── 📂 onboarding/                 # Onboarding flow
│   │   └── OnboardingScreen.kt       # First-time user onboarding
│   │
│   ├── 📂 permission/                 # Permission setup
│   │   └── PermissionSetupScreen.kt  # Health Connect permission request
│   │
│   ├── 📂 league/                     # League screens
│   │   ├── LeagueHomeScreen.kt       # League home (track selection)
│   │   └── LeagueHomeViewModel.kt    # League home view model
│   │
│   ├── 📂 track/                      # Track detail screens
│   │   ├── TrackDetailScreen.kt      # Track detail (map, progress, stats)
│   │   └── TrackDetailViewModel.kt   # Track detail view model
│   │
│   ├── 📂 leaderboard/                # Leaderboard screens
│   │   ├── LeaderboardScreen.kt      # Global leaderboard
│   │   └── LeaderboardViewModel.kt   # Leaderboard view model
│   │
│   ├── 📂 profile/                    # Profile screens
│   │   ├── ProfileScreen.kt          # User profile
│   │   └── ProfileViewModel.kt       # Profile view model
│   │
│   ├── 📂 stats/                      # Statistics screens
│   │   ├── GlobalStatsScreen.kt      # Global statistics
│   │   ├── GlobalStatsViewModel.kt   # Global stats view model
│   │   └── AppGlobalStatsViewModel.kt # App-wide stats view model
│   │
│   ├── 📂 store/                      # Store screens
│   │   ├── StoreScreen.kt            # In-app store
│   │   └── StoreViewModel.kt         # Store view model
│   │
│   ├── 📂 components/                 # Reusable UI components
│   │   ├── (dialog components)
│   │   └── (custom composables)
│   │
│   └── 📂 theme/                      # UI Theme
│       ├── Color.kt                  # Color definitions
│       ├── Theme.kt                  # Material 3 theme
│       └── Type.kt                   # Typography definitions
│
├── 📂 receiver/                       # Broadcast receivers
│   └── BootReceiver.kt               # Boot receiver (future: auto-start service)
│
├── 📂 service/                        # Background services
│   └── StepCounterService.kt         # Step counter service (future: foreground service)
│
├── 📂 worker/                         # WorkManager workers
│   └── DataSyncWorker.kt             # Periodic background sync worker
│
└── 📂 utils/                          # Utility classes
    ├── LanguageHelper.kt             # Language/locale utilities
    ├── MapExtensions.kt              # Google Maps extension functions
    └── PathUtils.kt                  # Path/route utilities
```

### 3.1 Katman Açıklamaları

#### Data Layer (`data/`)
- **Sorumluluk:** Veri kaynaklarına (Room, Firebase, Health Connect) erişim
- **Repository Implementations:** Domain layer'daki interface'lerin concrete implementasyonları
- **Local Database:** Room entities, DAOs, database definition
- **External Services:** Firebase, Health Connect client wrapper'ları

#### Domain Layer (`domain/`)
- **Sorumluluk:** İş mantığı (business logic)
- **Managers:** Kompleks iş mantığı yöneticileri (League, StepSync, Badge vb.)
- **Models:** Domain entities ve value objects
- **Repository Interfaces:** Data layer'a olan bağımlılığı tersine çevirme (Dependency Inversion)

#### UI Layer (`ui/`)
- **Sorumluluk:** Kullanıcı arayüzü ve state yönetimi
- **Screens:** Compose screen'ler
- **ViewModels:** UI state yönetimi (StateFlow, LiveData)
- **Components:** Reusable UI bileşenleri

#### Dependency Injection (`di/`)
- **Sorumluluk:** Tüm dependency'lerin sağlanması
- **Modules:** Hilt modülleri (AppModule, RepositoryModule, FirebaseModule)

---

## 4. DETAYLI İŞLEYİŞ SENARYOLARI (USER FLOWS)

### 4.1 Senaryo: Uygulama İlk Açılışı (Onboarding)

**Amaç:** Yeni kullanıcının uygulamayı ilk kez açması ve kurulum sürecini tamamlaması.

#### 4.1.1 Akış Diyagramı

```
App Start (PaceLegendsApp.onCreate)
    ↓
MainActivity.onCreate()
    ↓
Splash Screen (Android 12+)
    ↓
AdMob Initialization
    ↓
Remote Config Fetch & Activate
    ↓
Navigation Setup
    ↓
Auth Check (FirebaseAuth.currentUser)
    ↓
┌─────────────────┬──────────────────┐
│  Not Logged In  │   Logged In      │
│       ↓         │        ↓         │
│  LoginScreen    │  Check Setup     │
│       ↓         │        ↓         │
│  Google/Anon    │  ┌────────────┐  │
│  Sign-In        │  │!isSetupComp│  │
│       ↓         │  │    ↓       │  │
│  Onboarding?    │  │ Onboarding │  │
└─────────────────┴──┴────────────┴──┘
                        ↓
                 Permission Setup
                        ↓
                 League Registration
                        ↓
                 LeagueHomeScreen
```

#### 4.1.2 Kod Yolu (Code Path)

**1. Application Başlatma**

```kotlin
PaceLegendsApp.onCreate()
  → Hilt.androidApp(this) // Dependency injection setup
  → ProcessLifecycleOwner.get().lifecycle.addObserver(...) // Lifecycle tracking
  → WorkManager.initialize(...) // WorkManager setup with HiltWorkerFactory
  → scheduleSyncWorker() // Periodic sync worker scheduling
```

**2. MainActivity Başlatma**

```kotlin
MainActivity.onCreate()
  → installSplashScreen() // Android 12+ splash screen
  → MobileAds.initialize(context) // AdMob initialization
  → remoteConfigManager.fetchAndActivate() // Remote Config fetch
  → adManager.loadInterstitial() // Pre-load ads
  → setContent { Navigation() } // Compose UI setup
```

**3. Navigation ve Auth Kontrolü**

```kotlin
MainActivity.setContent { NavHost }
  → MainViewModel.isUserLoggedIn.collectAsState()
  → if (!isUserLoggedIn) → composable("login") { LoginScreen }
  → if (isUserLoggedIn) → checkUserSetupStatus()
```

**4. Login İşlemi**

```kotlin
LoginScreen → LoginViewModel.signInWithGoogle()
  → CredentialManager.getCredential(request)
  → FirebaseAuthRepository.signInWithGoogle(idToken)
    → FirebaseAuth.signInWithCredential(credential)
    → FirebaseAuth.currentUser (success)
  → UserRepository.getUser(userId)
    → Firestore: users/{userId}.get()
  → if (user == null) → UserRepository.saveUser(newUser)
  → LoginState.Success(userId, user.isSetupCompleted)
  → Navigation: if (!isSetupCompleted) → "onboarding" else → "track_selection"
```

**5. Onboarding İşlemi**

```kotlin
OnboardingScreen { onComplete }
  → UserRepository.updateSetupCompleted(userId, true)
    → Firestore: users/{userId}.update({ isSetupCompleted: true })
  → FirebaseRemoteConfig.fetchAndActivate().await() // Wait for config
  → LeagueManager.registerNewUser()
    → RemoteConfigManager.getTierTracksMapping()
    → LeagueRepository.createQualifyingEntry(userId, trackId, periodId)
      → Firestore: qualifying/{trackId}_{periodId}/entries/{userId}.set()
    → UserRepository.updateUserLeagueInfo(QUALIFYING, qualifyingId)
  → checkAndRequestPermissions()
  → Navigation: "track_selection"
```

#### 4.1.3 İlgili Sınıflar ve Fonksiyonlar

| Sınıf | Fonksiyon | Sorumluluk |
|-------|-----------|------------|
| `PaceLegendsApp` | `onCreate()` | Application initialization |
| `MainActivity` | `onCreate()` | Activity setup, navigation |
| `LoginViewModel` | `signInWithGoogle()` | Google sign-in logic |
| `FirebaseAuthRepository` | `signInWithGoogle()` | Firebase auth integration |
| `UserRepository` | `getUser()`, `saveUser()`, `updateSetupCompleted()` | User data operations |
| `LeagueManager` | `registerNewUser()` | League assignment logic |
| `OnboardingScreen` | `onComplete` callback | Onboarding completion handler |

---

### 4.2 Senaryo: Ana Fonksiyon - Adım Sayma ve Senkronizasyon

**Amaç:** Kullanıcının adımlarının Health Connect'ten okunması, lokal veritabanına kaydedilmesi ve Firestore'a senkronize edilmesi.

#### 4.2.1 Akış Diyagramı

```
User Opens App (LeagueHomeScreen / TrackDetailScreen)
    ↓
MainActivity: LaunchedEffect(isUserLoggedIn, permissionsGranted)
    ↓
stepRepository.refreshUserData() // Restore from Firestore
    ↓
stepRepository.syncHealthConnectSteps(force = true) // Initial sync
    ↓
┌──────────────────────────────────────────────────┐
│  Periodic Sync Loop (every 10 seconds)          │
│  ┌──────────────────────────────────────────┐   │
│  │ stepRepository.syncHealthConnectSteps()  │   │
│  │   ↓                                      │   │
│  │ HealthConnectManager.readStepsByTimeRange│   │
│  │   ↓                                      │   │
│  │ StepSyncManager.getCurrentPeriodInfo()  │   │
│  │   ↓                                      │   │
│  │ UserProgressDao.getProgressByTrack()    │   │
│  │   ↓                                      │   │
│  │ if (newPeriod) → create new UserProgress│   │
│  │ else → update existing UserProgress     │   │
│  │   ↓                                      │   │
│  │ StepSyncManager.syncIfNeeded()          │   │
│  │   ↓                                      │   │
│  │ FirebaseUserRepository.updateMonthlySteps│   │
│  │   ↓                                      │   │
│  │ Firestore: users/{userId}.update()      │   │
│  │   ↓                                      │   │
│  │ Cloud Function: onUserStepSync triggered│   │
│  │   ↓                                      │   │
│  │ Leaderboard & League updates            │   │
│  └──────────────────────────────────────────┘   │
│         ↓                                        │
│  delay(10000) // 10 seconds                     │
│         ↓                                        │
│  Loop continues...                              │
└──────────────────────────────────────────────────┘
```

#### 4.2.2 Kod Yolu (Code Path)

**1. İlk Sync (App Start)**

```kotlin
MainActivity.LaunchedEffect(isUserLoggedIn, permissionsGranted)
  → if (isUserLoggedIn && permissionsGranted)
    → stepRepository.refreshUserData()
      → FirebaseUserRepository.getUser(userId)
        → Firestore: users/{userId}.get()
      → UserProgressDao.getProgressByTrack(userId, trackId)
      → if (localProgress == null) → Create local progress from Firestore
    → stepRepository.syncHealthConnectSteps(force = true)
```

**2. Periyodik Sync Loop**

```kotlin
MainActivity.LaunchedEffect {
  while (true) {
    delay(10000) // 10 seconds
    stepRepository.syncHealthConnectSteps(force = false)
  }
}
```

**3. Adım Senkronizasyonu (StepRepositoryImpl)**

```kotlin
StepRepositoryImpl.syncHealthConnectSteps(force: Boolean)
  → StepSyncManager.getCurrentPeriodInfo()
    → RemoteConfigManager.getPeriodConfig()
    → Calculate currentPeriodId, periodStartTime, periodEndTime
  → HealthConnectManager.readStepsByTimeRange(periodStartTime, now)
    → HealthConnectClient.aggregate(AggregateRequest(
        metrics = setOf(StepsRecord.COUNT_TOTAL),
        timeRangeFilter = TimeRangeFilter.between(start, end)
      ))
    → checkForManualEntries() // Anti-cheat: filter manual entries
    → return verifiedSteps
  → UserProgressDao.getProgressByTrack(userId, trackId)
  → if (progress == null || newPeriod)
    → UserProgressDao.insert(newProgress)
  → else
    → UserProgressDao.update(existingProgress.copy(totalSteps = newSteps))
  → StepSyncManager.syncIfNeeded(monthlySteps, trackId, force)
    → if (needsSync || force)
      → FirebaseUserRepository.updateMonthlySteps(userId, monthlySteps)
        → Firestore: users/{userId}.update({ monthlySteps: steps })
        → Cloud Function: onUserStepSync triggered
```

**4. Cloud Function: onUserStepSync**

```typescript
functions.firestore.document("users/{userId}").onWrite(async (change, context) => {
  const newSteps = change.after.data().monthlySteps;
  const activeTrackId = change.after.data().activeTrackId;
  const currentPeriod = change.after.data().currentMonth;
  
  // Update Global Leaderboard
  await db.collection("leaderboards")
    .doc(activeTrackId).collection("monthly")
    .doc(currentPeriod).collection("entries")
    .doc(userId).set({ steps: newSteps, lastUpdated: now });
  
  // Update League/Qualifying
  if (leagueTier === "QUALIFYING") {
    await db.collection("qualifying").doc(`${trackId}_${periodId}`)
      .collection("entries").doc(userId).set({ steps: newSteps });
  } else {
    await db.collection("leagues").doc(leagueId)
      .collection("members").doc(userId).set({ steps: newSteps });
  }
});
```

#### 4.2.3 İlgili Sınıflar ve Fonksiyonlar

| Sınıf | Fonksiyon | Sorumluluk |
|-------|-----------|------------|
| `StepRepositoryImpl` | `syncHealthConnectSteps()` | Main sync orchestration |
| `HealthConnectManager` | `readStepsByTimeRange()` | Health Connect API wrapper |
| `StepSyncManager` | `getCurrentPeriodInfo()`, `syncIfNeeded()` | Period calculation, sync logic |
| `UserProgressDao` | `getProgressByTrack()`, `insert()`, `update()` | Local database operations |
| `FirebaseUserRepository` | `updateMonthlySteps()` | Firestore write operations |
| `DataSyncWorker` | `doWork()` | Background periodic sync (15 min) |

---

### 4.3 Senaryo: Arka Plan İşlemleri (WorkManager, Cloud Functions)

**Amaç:** Uygulama kapalıyken veya arka plandayken veri senkronizasyonu ve dönem sonu işlemlerinin yapılması.

#### 4.3.1 Arka Plan Sync (WorkManager)

**Akış:**
```
App Start (PaceLegendsApp.onCreate)
    ↓
scheduleSyncWorker()
    ↓
WorkManager.enqueueUniquePeriodicWork("DataSyncWork")
    ↓
Every 15 minutes (with constraints):
    ↓
DataSyncWorker.doWork()
    ↓
┌──────────────────────────────────────┐
│ 1. checkForPeriodTransition()       │
│    → StepSyncManager.checkPeriodEnd()│
│    → if (periodEnded)                │
│      → Trigger period end logic      │
│                                     │
│ 2. stepRepository.syncHealthConnectSteps(force=true)│
│    → Health Connect read            │
│    → Room update                    │
│    → Firestore sync                 │
│                                     │
│ 3. stepSyncManager.syncIfNeeded(force=true)│
│    → Force sync to Firestore        │
└──────────────────────────────────────┘
    ↓
Result.success() / Result.retry() / Result.failure()
```

**Kod Yolu:**

```kotlin
PaceLegendsApp.scheduleSyncWorker()
  → WorkManager.getInstance(this).enqueueUniquePeriodicWork(
      "DataSyncWork",
      ExistingPeriodicWorkPolicy.KEEP,
      PeriodicWorkRequestBuilder<DataSyncWorker>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .setRequiresBatteryNotLow(true)
          .build())
        .build()
    )

DataSyncWorker.doWork()
  → stepSyncManager.checkForPeriodTransition()
    → RemoteConfigManager.getPeriodConfig()
    → if (now >= periodEndDate)
      → Process period end (future: trigger Cloud Function)
  → stepRepository.syncHealthConnectSteps(force = true)
  → stepSyncManager.syncIfNeeded(monthlySteps, trackId, force = true)
  → return Result.success()
```

#### 4.3.2 Cloud Functions - Dönem Sonu İşlemleri

**Akış:**
```
Scheduled Trigger (every day 00:01)
    ↓
processPeriodEnd()
    ↓
┌─────────────────────────────────────────────┐
│ 1. Check if period ended                    │
│    → RemoteConfig.getPeriodConfig()         │
│    → if (now < periodEndDate) return        │
│                                             │
│ 2. Process All Leagues                      │
│    → db.collection("leagues").get()         │
│    → for each league:                       │
│      → processLeague(leagueDoc)             │
│        → Get members (orderBy steps desc)   │
│        → Top 3: Award badges & coins        │
│        → Top N: Promote to next tier        │
│        → Bottom N: Demote to prev tier      │
│                                             │
│ 3. Process Qualifying Pools                 │
│    → db.collection("qualifying").get()      │
│    → for each pool:                         │
│      → processQualifyingPool(poolDoc)       │
│        → Top 3: Promote to BRONZE           │
│        → Award badges & coins               │
│                                             │
│ 4. Update Period Config                     │
│    → Calculate next period                  │
│    → RemoteConfig.publishTemplate()         │
└─────────────────────────────────────────────┘
```

**Kod Yolu:**

```typescript
functions.pubsub.schedule("every day 00:01").onRun(async () => {
  // 1. Check period end
  const periodConfig = await getPeriodConfigFromRemoteConfig();
  if (now < periodConfig.currentPeriodEndDate) return;
  
  // 2. Process leagues
  const leaguesSnapshot = await db.collection("leagues").get();
  for (const leagueDoc of leaguesSnapshot.docs) {
    await processLeague(leagueDoc, periodConfig);
  }
  
  // 3. Process qualifying
  const qualifyingSnapshot = await db.collection("qualifying").get();
  for (const poolDoc of qualifyingSnapshot.docs) {
    await processQualifyingPool(poolDoc, periodConfig);
  }
  
  // 4. Update period
  await updatePeriodConfig(periodConfig);
});

async function processLeague(leagueDoc, config) {
  const membersSnapshot = await leagueDoc.ref
    .collection("members")
    .orderBy("steps", "desc")
    .get();
  
  let rank = 0;
  for (const memberDoc of membersSnapshot.docs) {
    rank++;
    const userId = memberDoc.id;
    
    // Top 3: Badges & Coins
    if (rank <= 3) {
      await awardChampionBadge(userId, rank, leagueId);
      await awardCoins(userId, config.coinRewards[rank - 1]);
    }
    
    // Top N: Promote
    if (rank <= config.promotionThreshold) {
      await promoteUser(userId, nextTier);
    }
    
    // Bottom N: Demote
    if (rank >= totalMembers - config.demotionThreshold + 1) {
      await demoteUser(userId, prevTier);
    }
  }
}
```

#### 4.3.3 İlgili Sınıflar ve Fonksiyonlar

| Sınıf/Fonksiyon | Sorumluluk |
|-----------------|------------|
| `DataSyncWorker.doWork()` | Periodic background sync |
| `StepSyncManager.checkForPeriodTransition()` | Period end detection |
| Cloud Function: `processPeriodEnd` | Scheduled period end processing |
| Cloud Function: `processLeague` | League promotion/demotion logic |
| Cloud Function: `processQualifyingPool` | Qualifying pool processing |

---

## 5. VERİ AKIŞ DİYAGRAMI (DATA FLOW)

### 5.1 Adım Verisi Akışı (Sensor → UI)

```
┌─────────────────────────────────────────────────────────────────┐
│                    DATA FLOW: STEP SYNC                         │
└─────────────────────────────────────────────────────────────────┘

[EXTERNAL] Health Connect (Sensors: Phone + Wearable)
    │
    │ Aggregate API (deduplication)
    │
    ▼
[DOMAIN] HealthConnectManager.readStepsByTimeRange()
    │
    │ Manual entry filtering (anti-cheat)
    │
    ▼
[DATA] StepRepositoryImpl.syncHealthConnectSteps()
    │
    │ Period calculation (StepSyncManager.getCurrentPeriodInfo())
    │
    ▼
[DATA] UserProgressDao.getProgressByTrack() / insert() / update()
    │
    │ Local cache (Room Database)
    │
    ▼
[DOMAIN] StepSyncManager.syncIfNeeded()
    │
    │ Sync threshold check (time-based or force)
    │
    ▼
[DATA] FirebaseUserRepository.updateMonthlySteps()
    │
    │ Firestore write: users/{userId}.update({ monthlySteps })
    │
    ▼
[CLOUD] Cloud Function: onUserStepSync (triggered)
    │
    │ Server-side processing
    │
    ├─→ leaderboards/{trackId}/monthly/{periodId}/entries/{userId}
    ├─→ leagues/{leagueId}/members/{userId} (or qualifying pool)
    └─→ Anti-cheat analysis (onLeaderboardUpdate)
    │
    ▼
[UI] StateFlow / State updates
    │
    │ Reactive UI recomposition
    │
    ▼
[UI] TrackDetailScreen / LeaderboardScreen (updated)
```

### 5.2 Kullanıcı Akışı (Authentication → Home)

```
┌─────────────────────────────────────────────────────────────────┐
│                    DATA FLOW: USER FLOW                         │
└─────────────────────────────────────────────────────────────────┘

[UI] LoginScreen
    │
    │ User action: "Google Sign-In" button
    │
    ▼
[DOMAIN] LoginViewModel.signInWithGoogle()
    │
    │ CredentialManager API
    │
    ▼
[DATA] FirebaseAuthRepository.signInWithGoogle(idToken)
    │
    │ Firebase Authentication
    │
    ▼
[DATA] UserRepository.getUser(userId)
    │
    │ Firestore: users/{userId}.get()
    │
    ├─→ if (user == null) → UserRepository.saveUser(newUser)
    └─→ if (!user.isSetupCompleted) → OnboardingScreen
    │
    ▼
[DOMAIN] LeagueManager.registerNewUser()
    │
    │ RemoteConfig: tier_tracks mapping
    │
    ▼
[DATA] LeagueRepository.createQualifyingEntry()
    │
    │ Firestore: qualifying/{trackId}_{periodId}/entries/{userId}
    │
    ▼
[UI] LeagueHomeScreen (track selection)
    │
    │ User selects track / View assigned track
    │
    ▼
[UI] TrackDetailScreen
    │
    │ Periodic sync (every 10s)
    │
    └─→ [Loop back to Step Sync Flow]
```

### 5.3 Liderlik Tablosu Verisi Akışı

```
┌─────────────────────────────────────────────────────────────────┐
│                    DATA FLOW: LEADERBOARD                       │
└─────────────────────────────────────────────────────────────────┘

[UI] LeaderboardScreen
    │
    │ User action: Navigate to leaderboard
    │
    ▼
[DOMAIN] LeaderboardViewModel.loadLeaderboard()
    │
    │ Track ID, Period ID
    │
    ▼
[DATA] LeaderboardRepository.getLeaderboardEntries()
    │
    │ Firestore query:
    │ leaderboards/{trackId}/monthly/{periodId}/entries
    │   .orderBy("steps", "desc")
    │   .limit(100)
    │
    ▼
[CLOUD] Firestore (Real-time listener)
    │
    │ Real-time updates
    │
    ▼
[UI] StateFlow<List<LeaderboardEntry>>
    │
    │ Compose recomposition
    │
    ▼
[UI] LeaderboardScreen (updated list)
```

### 5.4 Katmanlar Arası İletişim

```
┌──────────────────────────────────────────────────────────────┐
│              LAYER COMMUNICATION PATTERN                     │
└──────────────────────────────────────────────────────────────┘

UI Layer (Compose Screens)
    │
    │ Observes: StateFlow, State
    │ Calls: ViewModel functions
    │
    ▼
ViewModel Layer
    │
    │ Uses: Repository interfaces (Domain layer)
    │ Uses: Manager classes (Domain layer)
    │ Exposes: StateFlow, State
    │
    ▼
Domain Layer
    │
    │ Defines: Repository interfaces
    │ Implements: Business logic (Managers)
    │ Uses: Domain models
    │
    ▼
Data Layer
    │
    │ Implements: Repository interfaces
    │ Uses: Room DAOs, Firebase clients
    │ Returns: Domain models
    │
    ▼
External Services
    │
    │ Health Connect API
    │ Firebase (Auth, Firestore, Remote Config)
    │ Google Maps API
    │ AdMob SDK
```

---

## 6. VERİTABANI ŞEMASI

### 6.1 Room Database (Local - SQLite)

#### 6.1.1 Tablo: `user_progress`

Kullanıcının pist bazlı ilerlemesini saklar.

| Kolon | Tip | Açıklama |
|-------|-----|----------|
| `userId` | TEXT (PK) | Firebase User ID |
| `trackId` | TEXT (PK) | Pist ID (örn: "istanbul_park") |
| `displayName` | TEXT | Kullanıcı görünen adı |
| `totalSteps` | INTEGER | Dönem içi toplam adım |
| `completedLoops` | INTEGER | Tamamlanan tur sayısı |
| `totalActiveTimeMillis` | INTEGER | Toplam aktif süre (ms) |
| `lastUpdateTimestamp` | INTEGER | Son güncelleme zamanı (epoch ms) |
| `bestLapTimeSeconds` | INTEGER | En iyi tur süresi (saniye) |
| `isSynced` | INTEGER (Boolean) | Firestore'a senkronize edildi mi |
| `lastSyncedTimestamp` | INTEGER | Son senkronizasyon zamanı |
| `totalDistanceWalked` | REAL | Pist üzerinde toplam mesafe (metre) |
| `currentLapStartTime` | INTEGER | Mevcut turun başlangıç zamanı |
| `periodStartTime` | INTEGER | Dönem başlangıç zamanı |
| `currentLapNumber` | INTEGER | Şu anki tur numarası |
| `allTimeSteps` | INTEGER | Tüm zamanların toplam adımı (period bağımsız) |
| `allTimeLaps` | INTEGER | Tüm zamanların toplam turu |
| `allTimeDistanceMeters` | REAL | Tüm zamanların toplam mesafesi |

**Primary Key:** (`userId`, `trackId`)  
**Index:** (önerilen) `userId`, `trackId` (composite)

#### 6.1.2 Tablo: `tracks`

Pist bilgilerini cache'ler (Firestore'dan indirilen).

| Kolon | Tip | Açıklama |
|-------|-----|----------|
| `id` | TEXT (PK) | Pist ID |
| `genericName` | TEXT | Çok dilli isim (JSON string, Map<String, String>) |
| `description` | TEXT | Çok dilli açıklama (JSON string) |
| `geoJsonUrl` | TEXT | GeoJSON URL (pist rotası) |
| `totalDistanceMeters` | INTEGER | Pist toplam mesafesi (metre) |
| `isActive` | INTEGER (Boolean) | Aktif mi |
| `isPremium` | INTEGER (Boolean) | Premium pist mi |
| `recordTimeSeconds` | INTEGER | Rekor tur süresi |
| `version` | INTEGER | Pist versiyonu |
| `reverseDirection` | INTEGER (Boolean) | Ters yön mü |

**Primary Key:** `id`

#### 6.1.3 Tablo: `user_badges`

Kullanıcının kazandığı rozetler.

| Kolon | Tip | Açıklama |
|-------|-----|----------|
| `badgeId` | TEXT (PK) | Rozet ID (örn: "formation_lap") |
| `earnedTimestamp` | INTEGER | Kazanma zamanı (epoch ms) |

**Primary Key:** `badgeId`

#### 6.1.4 Tablo: `lap_history`

Tur geçmişi (lap completion records).

| Kolon | Tip | Açıklama |
|-------|-----|----------|
| `id` | INTEGER (PK, AutoIncrement) | Kayıt ID |
| `trackId` | TEXT | Pist ID |
| `userId` | TEXT | Kullanıcı ID |
| `startTime` | INTEGER | Tur başlangıç zamanı |
| `endTime` | INTEGER | Tur bitiş zamanı |
| `durationSeconds` | INTEGER | Tur süresi (saniye) |
| `totalSteps` | INTEGER | Bu turdaki adım sayısı |
| `lapNumber` | INTEGER | Tur numarası |
| `periodId` | TEXT | Dönem ID |

**Primary Key:** `id`  
**Index:** (önerilen) `userId`, `trackId`, `periodId` (composite)

#### 6.1.5 Tablo: `daily_step_log`

Günlük adım logları (istatistikler için).

| Kolon | Tip | Açıklama |
|-------|-----|----------|
| `date` | TEXT (PK) | Tarih (YYYY-MM-DD formatı) |
| `trackId` | TEXT (PK) | Pist ID |
| `userId` | TEXT (PK) | Kullanıcı ID |
| `steps` | INTEGER | O günkü adım sayısı |
| `distance` | REAL | O günkü mesafe (metre) |

**Primary Key:** (`date`, `trackId`, `userId`)  
**Index:** (önerilen) `userId`, `trackId`, `date` (composite)

#### 6.1.6 Tablo: `period_history`

Dönem geçmişi (bitmiş dönemlerin arşiv kayıtları).

| Kolon | Tip | Açıklama |
|-------|-----|----------|
| `periodId` | TEXT (PK) | Dönem ID (örn: "period_0") |
| `userId` | TEXT (PK) | Kullanıcı ID |
| `trackId` | TEXT (PK) | Pist ID |
| `displayName` | TEXT | Dönem görünen adı (nullable) |
| `startTimestamp` | INTEGER | Dönem başlangıç zamanı |
| `endTimestamp` | INTEGER | Dönem bitiş zamanı |
| `totalSteps` | INTEGER | Dönem toplam adımı |
| `completedLaps` | INTEGER | Dönem toplam turu |
| `bestLapTimeSeconds` | INTEGER | Dönem en iyi tur süresi |
| `finalRank` | INTEGER | Liderlik tablosundaki son sıralama (nullable) |
| `totalParticipants` | INTEGER | Toplam katılımcı sayısı (nullable) |
| `archivedAt` | INTEGER | Arşivleme zamanı |

**Primary Key:** (`periodId`, `userId`, `trackId`)  
**Index:** (önerilen) `userId`, `trackId` (composite)

### 6.2 Firestore Database (Cloud - NoSQL)

#### 6.2.1 Koleksiyon: `users/{userId}`

Kullanıcı profili ve durum bilgileri.

```typescript
{
  uid: string;                    // Firebase Auth UID
  email: string | null;           // Email (Google sign-in)
  displayName: string | null;     // Görünen ad
  isSetupCompleted: boolean;      // Onboarding tamamlandı mı
  createdAt: Timestamp;           // Hesap oluşturma zamanı
  
  // League Info
  leagueTier: string;             // "QUALIFYING" | "BRONZE" | "SILVER" | ...
  leagueId: string | null;        // Aktif lig ID (null if QUALIFYING)
  
  // Step Info
  monthlySteps: number;           // Mevcut dönem adım sayısı
  currentMonth: string;           // Dönem ID (örn: "period_0")
  activeTrackId: string;          // Aktif pist ID
  
  // Sync Info
  lastSyncTimestamp: Timestamp;   // Son senkronizasyon zamanı
  
  // Anti-Cheat
  antiCheat: {
    flagged: boolean;             // Şüpheli kullanıcı mı
    suspicionScore: number;       // Şüphe skoru
    lastViolation: Timestamp;     // Son ihlal zamanı
    violations: string[];         // İhlal listesi
  } | null;
  
  // Rewards (future)
  coins: number;                  // Coin bakiyesi
  unlockedFrames: string[];       // Açılmış avatar çerçeveleri
}
```

#### 6.2.2 Koleksiyon: `leaderboards/{trackId}/monthly/{periodId}/entries/{userId}`

Global liderlik tablosu girişleri.

```typescript
{
  userId: string;
  displayName: string;
  steps: number;                  // Toplam adım
  lastUpdated: Timestamp;         // Son güncelleme zamanı
  flagged: boolean;               // Anti-cheat flag (optional)
}
```

**Index:** `steps` (DESCENDING) - Leaderboard sıralaması için

#### 6.2.3 Koleksiyon: `leagues/{leagueId}`

Lig bilgileri.

```typescript
{
  tier: string;                   // "BRONZE" | "SILVER" | ...
  trackId: string;                // Atanmış pist
  periodId: string;               // Mevcut dönem
  memberCount: number;            // Üye sayısı (max 50)
  createdAt: Timestamp;
}
```

**Alt Koleksiyon:** `members/{userId}` - Lig üyeleri

```typescript
{
  userId: string;
  displayName: string;
  steps: number;
  lastUpdated: Timestamp;
}
```

**Index:** `steps` (DESCENDING) - Lig içi sıralama için

#### 6.2.4 Koleksiyon: `qualifying/{poolId}/entries/{userId}`

Eleme havuzu girişleri (poolId formatı: `{trackId}_{periodId}`).

```typescript
{
  userId: string;
  displayName: string;
  steps: number;
  lastUpdated: Timestamp;
}
```

**Index:** `steps` (DESCENDING)

#### 6.2.5 Koleksiyon: `antiCheatLogs/{logId}`

Anti-cheat log kayıtları.

```typescript
{
  userId: string;
  trackId: string;
  periodId: string;
  violations: string[];           // İhlal türleri
  suspicionScore: number;         // Şüphe skoru
  stepDelta: number;              // Adım artışı
  timeDeltaMinutes: number;       // Zaman farkı (dakika)
  timestamp: Timestamp;           // Log zamanı
  newSteps: number;               // Yeni adım sayısı
  oldSteps: number;               // Eski adım sayısı
}
```

**Index:** 
- `userId`, `trackId`, `periodId`, `timestamp` (DESCENDING) - Kullanıcı log sorguları için
- `timestamp` (ASCENDING) - Genel log sorguları için

#### 6.2.6 Koleksiyon: `tracks/{trackId}`

Pist bilgileri (Room cache source).

```typescript
{
  id: string;
  genericName: { [lang: string]: string };  // Çok dilli isim
  description: { [lang: string]: string };  // Çok dilli açıklama
  geoJsonUrl: string;                       // GeoJSON URL
  totalDistanceMeters: number;              // Pist mesafesi
  isActive: boolean;
  isPremium: boolean;
  recordTimeSeconds: number;
  version: number;
  reverseDirection: boolean;
  sectors: Sector[];                        // Sektör bilgileri (runtime)
}
```

#### 6.2.7 Koleksiyon: `users/{userId}/championBadges/{badgeId}`

Kullanıcının kazandığı şampiyonluk rozetleri (Cloud Function tarafından oluşturulur).

```typescript
{
  type: string;                  // "PERIOD_CHAMPION" | "PODIUM_FINISH" | ...
  rank: number;                  // Sıralama (1, 2, 3)
  leagueId: string | null;       // Lig ID (null if qualifying)
  tier: string;                  // Lig kademesi
  periodId: string;              // Dönem ID
  earnedAt: Timestamp;           // Kazanma zamanı
}
```

### 6.3 Veritabanı İlişkileri

**Room Database:**
- `user_progress` ↔ `tracks` (trackId foreign key, soft relation)
- `lap_history` ↔ `user_progress` (userId + trackId composite key)
- `daily_step_log` ↔ `user_progress` (userId + trackId composite key)
- `period_history` ↔ `user_progress` (userId + trackId composite key)

**Firestore:**
- `users/{userId}` → `leaderboards/{trackId}/monthly/{periodId}/entries/{userId}` (1:1)
- `users/{userId}` → `leagues/{leagueId}/members/{userId}` (1:1, conditional)
- `users/{userId}` → `qualifying/{poolId}/entries/{userId}` (1:1, conditional)
- `leaderboards/{trackId}/monthly/{periodId}/entries/{userId}` → `antiCheatLogs/{logId}` (1:N, triggered)

---

## 7. EKSİKLİK VE RİSK ANALİZİ

### 7.1 Güvenlik Açıkları

#### 7.1.1 Kritik: API Key Exposure Risk

**Sorun:**
- `local.properties` dosyasında API key'ler plain text olarak saklanıyor
- BuildConfig'e yazılan key'ler APK'da görülebilir
- Eğer `local.properties` git'e commit edilmişse, geçmiş commit'lerde key'ler görülebilir

**Risk Seviyesi:** 🔴 **KRİTİK**

**Etki:**
- API key'lerin kötüye kullanılması
- Maliyet artışı (Maps API, AdMob)
- Güvenlik ihlali

**Çözüm Önerileri:**
1. **API Key Restrictions:** Google Cloud Console'da key'leri kısıtla
   - Android package name ile kısıtla
   - SHA-1 fingerprint ile kısıtla
2. **ProGuard/R8 Obfuscation:** BuildConfig'i obfuscate et
3. **Git History Cleanup:** Eğer commit edilmişse, git history'den kaldır
4. **Key Rotation:** Mevcut key'leri rotate et

#### 7.1.2 Yüksek: Firestore Rules Güvenlik Açığı

**Sorun:**
- `antiCheatLogs` koleksiyonunda herhangi bir authenticated kullanıcı log oluşturabiliyor
- Kötü niyetli kullanıcılar sahte loglar oluşturabilir

**Risk Seviyesi:** 🟠 **YÜKSEK**

**Mevcut Rule:**
```javascript
match /antiCheatLogs/{logId} {
  allow create: if request.auth != null; // ⚠️ Herhangi bir kullanıcı
}
```

**Çözüm:**
```javascript
match /antiCheatLogs/{logId} {
  allow create: if false; // Sadece Cloud Functions
  allow read: if request.auth != null && request.auth.token.admin == true;
}
```

#### 7.1.3 Orta: SharedPreferences Güvenliği

**Sorun:**
- `MODE_PRIVATE` kullanılıyor (iyi), ancak root'lu cihazlarda okunabilir
- Hassas veriler (userId, trackId) plain text olarak saklanıyor

**Risk Seviyesi:** 🟡 **ORTA**

**Çözüm:**
- `EncryptedSharedPreferences` kullan (AndroidX Security Crypto)

### 7.2 Kod Kalitesi ve Hata Yönetimi

#### 7.2.1 Kritik: Eksik Error Handling

**Sorun:**
- Birçok ViewModel ve Repository fonksiyonunda try-catch blokları eksik
- Hata durumlarında kullanıcıya bilgi verilmiyor
- Crash riski yüksek

**Risk Seviyesi:** 🔴 **KRİTİK**

**Etkilenen Sınıflar:**
- `AppGlobalStatsViewModel.loadStats()`
- `StepRepositoryImpl.syncHealthConnectSteps()`
- `LeagueManager.registerNewUser()`

**Çözüm:**
- Tüm repository çağrılarını try-catch ile sarmala
- Error state'leri StateFlow ile UI'ya aktar
- Logging ve Crashlytics entegrasyonu

#### 7.2.2 Yüksek: Null Safety Eksiklikleri

**Sorun:**
- Bazı durumlarda null kontrolleri eksik
- `maxByOrNull` gibi nullable dönen fonksiyonlarda else durumu ele alınmıyor

**Risk Seviyesi:** 🟠 **YÜKSEK**

**Çözüm:**
- Nullable değerler için default değerler kullan
- Safe call operator (`?.`) ve elvis operator (`?:`) kullan

#### 7.2.3 Orta: Race Condition Potansiyeli

**Sorun:**
- `loadStats()` gibi fonksiyonlar birden fazla kez çağrılırsa paralel çalışabilir
- StateFlow güncellemeleri sırası bozulabilir

**Risk Seviyesi:** 🟡 **ORTA**

**Çözüm:**
- Mutex kullanarak eşzamanlı çağrıları önle
- `isLoading` flag'i ile duplicate çağrıları engelle

### 7.3 Performans Sorunları

#### 7.3.1 Yüksek: Sequential Database Queries

**Sorun:**
- `AppGlobalStatsViewModel.loadStats()` içinde 3 query sırayla çalışıyor
- Toplam süre = Query1 + Query2 + Query3

**Risk Seviyesi:** 🟠 **YÜKSEK**

**Çözüm:**
- Coroutine scope ile paralel çalıştır (`async/await`)
- Toplam süre = max(Query1, Query2, Query3)

#### 7.3.2 Orta: In-Memory Filtering

**Sorun:**
- `dailyStepLogDao.getLogsByDateRange()` tüm ayın loglarını çekiyor
- Memory'de filter ediliyor (gereksiz data transfer)

**Risk Seviyesi:** 🟡 **ORTA**

**Çözüm:**
- Database'de aggregate et (`SUM`, `COUNT`)
- Sadece gerekli veriyi çek

#### 7.3.3 Düşük: Gereksiz StateFlow Güncellemeleri

**Sorun:**
- Her `loadStats()` çağrısında tüm StateFlow'lar güncelleniyor
- UI recomposition tetikleniyor (performans maliyeti)

**Risk Seviyesi:** 🟢 **DÜŞÜK**

**Çözüm:**
- Sadece değişen değerleri güncelle
- State comparison yap

### 7.4 Test Coverage Eksikliği

#### 7.4.1 Yüksek: Unit Test Eksikliği

**Sorun:**
- Sadece 1 test dosyası mevcut (`StepRepositoryTest.kt`)
- ViewModel'ler test edilmiyor
- Manager sınıfları test edilmiyor
- Repository'ler test edilmiyor

**Risk Seviyesi:** 🟠 **YÜKSEK**

**Etki:**
- Refactoring riski yüksek
- Regression bug'ları tespit edilemez
- Kod kalitesi düşük

**Çözüm:**
- ViewModel testleri ekle (MockRepository kullan)
- Manager testleri ekle
- Repository testleri ekle (Mock Firebase/Room)

#### 7.4.2 Orta: Integration Test Eksikliği

**Sorun:**
- End-to-end test yok
- UI test yok (Compose UI Testing)

**Risk Seviyesi:** 🟡 **ORTA**

**Çözüm:**
- Critical user flow'lar için integration testler
- Compose UI test framework kullan

### 7.5 Ölçeklenebilirlik Sorunları

#### 7.5.1 Yüksek: Firestore Query Performansı

**Sorun:**
- Leaderboard query'leri büyüdükçe yavaşlayabilir
- Index eksiklikleri olabilir
- Pagination yok (limit 100, daha fazlası için scroll yok)

**Risk Seviyesi:** 🟠 **YÜKSEK**

**Çözüm:**
- Composite index'ler ekle
- Pagination implementasyonu (cursor-based)
- Cache mekanizması (Room'da cache)

#### 7.5.2 Orta: Cloud Functions Timeout Riski

**Sorun:**
- `processPeriodEnd` fonksiyonu tüm ligleri işlerken timeout riski var
- Büyük lig sayısında (100+ lig) işlem süresi uzayabilir

**Risk Seviyesi:** 🟡 **ORTA**

**Çözüm:**
- Batch processing (her seferinde N lig)
- Cloud Tasks kullan (async processing)
- Progress tracking

### 7.6 Kullanıcı Deneyimi (UX) Sorunları

#### 7.6.1 Orta: Offline Modu Eksikliği

**Sorun:**
- Offline modda kullanıcı hiçbir şey göremez
- Room cache var ama UI'da gösterilmiyor

**Risk Seviyesi:** 🟡 **ORTA**

**Çözüm:**
- Offline state detection
- Cached data gösterimi
- Sync indicator

#### 7.6.2 Düşük: Loading State Eksikliği

**Sorun:**
- Bazı ekranlarda loading indicator yok
- Kullanıcı veri yüklenirken bekliyor mu bilmiyor

**Risk Seviyesi:** 🟢 **DÜŞÜK**

**Çözüm:**
- Loading state'leri ekle
- Skeleton screens kullan

### 7.7 Öncelik Sıralaması

#### Hemen Düzeltilmeli (P0 - Critical):

1. ✅ **API Key Security:** Key'leri kısıtla, rotate et, git history'den kaldır
2. ✅ **Error Handling:** Tüm repository çağrılarında try-catch ekle
3. ✅ **Null Safety:** Null kontrolleri ekle, default değerler kullan
4. ✅ **Firestore Rules:** `antiCheatLogs` create rule'ını kapat

#### Yakında Düzeltilmeli (P1 - High):

5. ✅ **Unit Tests:** ViewModel ve Manager testleri ekle
6. ✅ **Performance:** Sequential query'leri paralel yap
7. ✅ **Firestore Indexing:** Composite index'ler ekle, pagination implementasyonu
8. ✅ **Race Conditions:** Mutex kullan, duplicate call prevention

#### İyileştirme (P2 - Medium):

9. ✅ **Integration Tests:** Critical flow'lar için E2E testler
10. ✅ **Offline Mode:** Cached data gösterimi
11. ✅ **Cloud Functions:** Batch processing, timeout handling
12. ✅ **UX Improvements:** Loading states, error messages

### 7.8 Özet Tablo

| Kategori | Kritik | Yüksek | Orta | Düşük | Toplam |
|----------|--------|--------|------|-------|--------|
| Güvenlik | 1 | 2 | 1 | 0 | 4 |
| Kod Kalitesi | 2 | 1 | 1 | 0 | 4 |
| Performans | 0 | 1 | 2 | 1 | 4 |
| Test Coverage | 0 | 2 | 1 | 0 | 3 |
| Ölçeklenebilirlik | 0 | 1 | 1 | 0 | 2 |
| UX | 0 | 0 | 1 | 1 | 2 |
| **TOPLAM** | **3** | **7** | **7** | **2** | **19** |

---

## 8. SONUÇ

**Pace Legends**, modern Android geliştirme pratiklerini kullanan, Clean Architecture prensiplerine uygun, ölçeklenebilir bir fitness gamification uygulamasıdır. Proje, sağlam bir mimari temele sahiptir, ancak güvenlik, test coverage ve performans açısından iyileştirme potansiyeli bulunmaktadır.

**Güçlü Yönler:**
- ✅ Temiz mimari (Clean Architecture + MVVM)
- ✅ Modern teknolojiler (Jetpack Compose, Hilt, Room)
- ✅ Offline-first yaklaşım
- ✅ Anti-cheat sistemi
- ✅ Dinamik konfigürasyon (Remote Config)

**İyileştirme Alanları:**
- ⚠️ Güvenlik (API key management, Firestore rules)
- ⚠️ Test coverage (Unit tests, integration tests)
- ⚠️ Error handling (Exception handling, user feedback)
- ⚠️ Performans (Query optimization, caching)

**Önerilen Yol Haritası:**
1. **Kısa Vadede (1-2 hafta):** Kritik güvenlik açıklarını kapat, error handling ekle
2. **Orta Vadede (1 ay):** Unit test coverage'ı %70+ seviyesine çıkar, performans optimizasyonları
3. **Uzun Vadede (2-3 ay):** Integration testler, ölçeklenebilirlik iyileştirmeleri, UX enhancements

---

**Dokümantasyon Sonu** ✅

*Bu dokümantasyon, projenin mevcut kod tabanı analiz edilerek oluşturulmuştur. Tüm bilgiler proje dosyalarından çıkarılmıştır.*
