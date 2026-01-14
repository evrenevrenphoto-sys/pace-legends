# 📱 PACE LEGENDS - KAPSAMLI İŞLEYİŞ RAPORU

**Proje:** Pace Legends - Adım Sayar ve Yarış Uygulaması  
**Platform:** Android (Kotlin, Jetpack Compose)  
**Tarih:** 2026  
**Versiyon:** 1.0

---

## 📋 İÇİNDEKİLER

1. [Genel Bakış](#genel-bakış)
2. [Mimari Yapı](#mimari-yapı)
3. [İşleyiş Adımları (Detaylı)](#işleyiş-adımları-detaylı)
4. [Ana Bileşenler](#ana-bileşenler)
5. [Veri Akışı](#veri-akışı)
6. [Özellikler](#özellikler)
7. [Teknik Detaylar](#teknik-detaylar)

---

## 🎯 GENEL BAKIŞ

**Pace Legends**, kullanıcıların günlük adımlarını Formula 1 temalı bir yarış deneyimine dönüştüren bir Android fitness uygulamasıdır. Kullanıcılar adımlarını atarak sanal F1 pistlerinde yarışır, liglerde yükselir, rozetler kazanır ve diğer kullanıcılarla rekabet ederler.

### Temel Kavramlar:
- **Adım Sayma:** Health Connect entegrasyonu ile gerçek adımlar ölçülür
- **Pistler:** F1 temalı sanal pistler (İstanbul Park, Spa-Francorchamps vb.)
- **Lig Sistemi:** 7 kademeli lig yapısı (Eleme → Bronz → Gümüş → Altın → Platin → Elmas → Efsane)
- **Dönemler:** Periyodik yarışmalar (aylık veya özel süreler)
- **Rozetler:** Başarılar için ödüller
- **Liderlik Tabloları:** Rekabet ve sosyal etkileşim

---

## 🏗️ MİMARİ YAPI

### Teknoloji Stack:
- **Dil:** Kotlin
- **UI Framework:** Jetpack Compose
- **Mimari:** Clean Architecture + MVVM
- **Dependency Injection:** Hilt (Dagger)
- **Local Database:** Room Database (SQLite)
- **Cloud Database:** Firebase Firestore
- **Authentication:** Firebase Authentication
- **Backend Functions:** Firebase Cloud Functions (TypeScript)
- **Maps:** Google Maps SDK
- **Health Data:** Android Health Connect
- **Work Manager:** Arka plan senkronizasyonu
- **Monetization:** AdMob + RevenueCat

### Katmanlar:
```
UI Layer (Compose Screens)
    ↓
ViewModel Layer (State Management)
    ↓
Domain Layer (Use Cases, Managers, Repositories Interfaces)
    ↓
Data Layer (Repository Implementations, Room DAOs, Firebase)
    ↓
External Services (Health Connect, Firebase, Maps)
```

---

## 🔄 İŞLEYİŞ ADIMLARI (DETAYLI)

### 1️⃣ UYGULAMA BAŞLATMA VE BAŞLANGIÇ

#### 1.1 Uygulama Açılışı (`PaceLegendsApp.kt`)

**Adımlar:**
1. **Application.onCreate()** çağrılır
2. **Hilt** dependency injection sistemi başlatılır
3. **ProcessLifecycleOwner** observer kaydedilir (uygulama yaşam döngüsü takibi)
4. **WorkManager** yapılandırması yapılır (HiltWorkerFactory ile)
5. **Periodic DataSyncWorker** zamanlanır (15 dakikada bir çalışır)

**Kod Yolu:**
```
PaceLegendsApp.onCreate()
  → scheduleSyncWorker()
  → WorkManager.enqueueUniquePeriodicWork("DataSyncWork")
```

#### 1.2 MainActivity Başlatma (`MainActivity.kt`)

**Adımlar:**
1. **Splash Screen** gösterilir (Android 12+)
2. **AdMob SDK** initialize edilir
3. **Remote Config** fetch edilir ve aktifleştirilir
4. **Interstitial Ad** yüklenir (eğer reklamlar aktifse)
5. **Compose UI** oluşturulur
6. **Navigation** sistemi kurulur

**Kod Yolu:**
```
MainActivity.onCreate()
  → installSplashScreen()
  → MobileAds.initialize()
  → remoteConfigManager.fetchAndActivate()
  → adManager.loadInterstitial()
  → setContent { ... }
```

---

### 2️⃣ KULLANICI KAYIT VE GİRİŞ SÜRECİ

#### 2.1 İlk Açılış - Login Ekranı (`LoginScreen.kt`, `LoginViewModel.kt`)

**Durumlar:**
- Kullanıcı **giriş yapmamışsa** → `LoginScreen` gösterilir
- Kullanıcı **anonim veya Google ile giriş** yapabilir

**Google Sign-In Akışı:**
1. Kullanıcı "Google ile Giriş" butonuna tıklar
2. **CredentialManager** ile Google ID token alınır
3. **FirebaseAuthRepository.signInWithGoogle()** çağrılır
4. Firebase Authentication ile giriş yapılır
5. Kullanıcı Firestore'da kontrol edilir
6. Yeni kullanıcıysa → `User` dokümanı oluşturulur
7. `isSetupCompleted` durumu kontrol edilir

**Kod Akışı:**
```
LoginViewModel.signInWithGoogle()
  → CredentialManager.getCredential()
  → FirebaseAuthRepository.signInWithGoogle(idToken)
  → LoginViewModel.checkUserSetupStatus(userId)
  → UserRepository.getUser(userId)
  → if (null) → UserRepository.saveUser(newUser)
  → LoginState.Success(userId, isSetupCompleted)
```

**Anonim Giriş:**
- Kullanıcı "Anonim Devam Et" seçerse
- `FirebaseAuthRepository.signInAnonymously()` çağrılır
- Daha sonra Google hesabına bağlanabilir (account linking)

#### 2.2 Onboarding Süreci (`OnboardingScreen.kt`)

**Eğer `isSetupCompleted == false` ise:**
1. Kullanıcı **Onboarding Screen**'e yönlendirilir
2. Uygulama tanıtımı gösterilir
3. Onboarding tamamlanınca:
   - `UserRepository.updateSetupCompleted(userId, true)` çağrılır
   - Remote Config fetch edilir (lig atama için gerekli)
   - `LeagueManager.registerNewUser()` çağrılır (Eleme Havuzu'na kayıt)
   - İzin kontrolleri başlatılır

**Kod Akışı:**
```
OnboardingScreen { onComplete }
  → UserRepository.updateSetupCompleted(uid, true)
  → RemoteConfig.fetchAndActivate()
  → LeagueManager.registerNewUser()
  → checkAndRequestPermissions()
  → Navigate to track_selection
```

---

### 3️⃣ İZİN YÖNETİMİ VE HEALTH CONNECT KURULUMU

#### 3.1 İzin Kontrolü (`PermissionSetupScreen.kt`)

**Gerekli İzinler:**
1. **ACTIVITY_RECOGNITION** (Android 10+)
2. **POST_NOTIFICATIONS** (Android 13+)
3. **Health Connect Permissions:**
   - `READ_STEPS`
   - `READ_TOTAL_CALORIES_BURNED`

**İzin İsteme Süreci:**
1. Standart runtime izinler istenir (`ActivityResultContracts.RequestMultiplePermissions`)
2. İzinler verilirse → Health Connect durumu kontrol edilir
3. Health Connect kullanılabilirliği kontrol edilir
4. Health Connect izinleri istenir (`PermissionController.createRequestPermissionResultContract`)
5. İzinler verilince → `MainViewModel.updatePermissionStatus(true)` çağrılır

**Kod Akışı:**
```
MainActivity.checkAndRequestPermissions()
  → permissionLauncher.launch(permissions)
  → checkHealthConnectStatusOnly()
  → HealthConnectManager.hasAllPermissions()
  → healthConnectLauncher.launch(healthConnectPermissions)
  → MainViewModel.updatePermissionStatus(true)
```

#### 3.2 Health Connect Entegrasyonu (`HealthConnectManager.kt`)

**Özellikler:**
- Health Connect'ten adım verisi okunur
- **Aggregate API** kullanılır (deduplication - telefon + saat çift sayımı önlenir)
- Manuel giriş tespiti yapılır (anti-cheat)
- Son başarılı okuma değeri cache'lenir

**Adım Okuma Süreci:**
```
HealthConnectManager.readStepsByTimeRange(startTime, endTime)
  → healthConnectClient.aggregate(AggregateRequest)
  → StepsRecord.COUNT_TOTAL (deduplicated)
  → checkForManualEntries() (anti-cheat)
  → return verifiedSteps
```

---

### 4️⃣ LİG SİSTEMİ VE PİST ATAMASI

#### 4.1 Lig Sistemi Konsepti (`LeagueManager.kt`, `LeagueTier.kt`)

**Lig Kademeleri:**
```
QUALIFYING (Eleme Havuzu) - Sınırsız kullanıcı
    ↓
BRONZE (Bronz Lig) - 50 kişi
    ↓
SILVER (Gümüş Lig) - 50 kişi
    ↓
GOLD (Altın Lig) - 50 kişi
    ↓
PLATINUM (Platin Lig) - 50 kişi
    ↓
DIAMOND (Elmas Lig) - 50 kişi
    ↓
LEGEND (Efsane Lig) - 50 kişi
```

**Lig = Pist Konsepti:**
- Her lig kademesi bir pist ile eşlenir
- Kullanıcı pist seçmez, **ligi onun pistini belirler**
- Remote Config'den `tier_tracks` mapping'i alınır
- Örnek:
  - QUALIFYING → "istanbul_park"
  - BRONZE → "spa"
  - SILVER → "istanbul_park"
  - ...

#### 4.2 Yeni Kullanıcı Kaydı (`LeagueManager.registerNewUser()`)

**Süreç:**
1. Kullanıcı Eleme Havuzu'na kaydedilir
2. Eleme Havuzu formatı: `qualifying/{trackId}_{periodId}/entries/{userId}`
3. Kullanıcının `leagueTier = QUALIFYING` olarak ayarlanır
4. `leagueId = null` (Eleme'de lig ID yok)

**Kod Akışı:**
```
LeagueManager.registerNewUser()
  → getAssignedTrack() (Remote Config'den)
  → getCurrentPeriodId()
  → LeagueRepository.registerToQualifyingPool(userId, trackId, periodId)
  → UserRepository.updateUserLeague(userId, QUALIFYING, null)
```

#### 4.3 Pist Ataması (`LeagueManager.getAssignedTrack()`)

**Süreç:**
1. Kullanıcının mevcut ligi alınır (`getCurrentLeagueInfo()`)
2. Lig kademesi ile Remote Config'den pist ID'si alınır
3. `RemoteConfigManager.getTrackForTier(tier.name)` çağrılır
4. Pist ID döndürülür

**Kod Akışı:**
```
LeagueManager.getAssignedTrack()
  → getCurrentLeagueInfo() → UserLeagueInfo(tier, leagueId, ...)
  → RemoteConfigManager.getTrackForTier(tier.name)
  → return trackId
```

---

### 5️⃣ ADIM SAYMA VE SENKRONİZASYON SÜRECİ

#### 5.1 Adım Verisi Kaynağı

**Ana Kaynak:** Android Health Connect
- Telefon sensörleri (accelerometer, step counter)
- Saat/Giyilebilir cihazlar (Wear OS)
- Fitness uygulamaları (Google Fit, Samsung Health vb.)

**Alternatif:** (Gelecekte) Donanım step counter (daha az pil tüketimi)

#### 5.2 Periyodik Senkronizasyon (`MainActivity` - LaunchedEffect)

**Uygulama Açıkken:**
1. Kullanıcı giriş yapmış ve izinler verilmişse
2. Her **10 saniyede bir** Health Connect'ten adım verisi okunur
3. `StepRepository.syncHealthConnectSteps(force = false)` çağrılır

**Kod Akışı:**
```
LaunchedEffect(isUserLoggedIn, permissionsGranted) {
  if (isUserLoggedIn && permissionsGranted) {
    stepRepository.refreshUserData() // İlk açılışta
    stepRepository.syncHealthConnectSteps(force = true) // İlk sync
    
    while (true) {
      delay(10000) // 10 saniye
      stepRepository.syncHealthConnectSteps(force = false)
    }
  }
}
```

#### 5.3 Adım Senkronizasyon Detayları (`StepRepositoryImpl.syncHealthConnectSteps()`)

**Süreç:**
1. **Period Bilgisi Alınır:**
   - `StepSyncManager.getCurrentPeriodInfo()` çağrılır
   - Remote Config'den `race_duration_days` ve `race_start_date` alınır
   - Mevcut period hesaplanır: `period_0`, `period_1`, ...

2. **Health Connect'ten Veri Okunur:**
   - Period başlangıç zamanından şimdiye kadar
   - `HealthConnectManager.readStepsByTimeRange(startTime, now)`
   - Aggregate API kullanılır (deduplication)

3. **Veritabanı Güncellemesi:**
   - `UserProgress` tablosunda kontrol edilir
   - Yeni period ise → Yeni kayıt oluşturulur
   - Aynı period ise → Mevcut kayıt güncellenir
   - `allTimeSteps` hesaplanır (tüm dönemler toplamı)

4. **Firestore'a Gönderim:**
   - `users/{userId}/monthlySteps` güncellenir
   - Firebase Cloud Function tetiklenir (`onUserStepSync`)
   - Cloud Function otomatik olarak leaderboard'u günceller

**Kod Akışı:**
```
StepRepositoryImpl.syncHealthConnectSteps(force)
  → StepSyncManager.getCurrentPeriodInfo()
  → HealthConnectManager.readStepsByTimeRange(periodStart, now)
  → UserProgressDao.getProgressByTrack(userId, trackId)
  → if (newPeriod) → create new UserProgress
  → else → update existing UserProgress
  → FirebaseUserRepository.updateMonthlySteps(userId, steps)
  → Firebase Cloud Function: onUserStepSync triggered
```

#### 5.4 Arka Plan Senkronizasyonu (`DataSyncWorker.kt`)

**WorkManager ile:**
1. **15 dakikada bir** çalışır
2. Sadece şu koşullarda çalışır:
   - İnternet bağlantısı var
   - Batarya düşük değil
3. `StepRepository.syncHealthConnectSteps()` çağrılır

**Kod Akışı:**
```
DataSyncWorker.doWork()
  → StepRepository.syncHealthConnectSteps(force = false)
  → Result.success()
```

#### 5.5 Uygulama Arka Plandayken (`MainActivity.onStop()`)

**Süreç:**
1. Uygulama arka plana geçince
2. **Application Scope** kullanılarak (Activity yok olsa bile çalışır)
3. `StepRepository.forceSync()` çağrılır
4. Birikmiş adımlar Firestore'a gönderilir

**Kod Akışı:**
```
MainActivity.onStop()
  → (application as PaceLegendsApp).applicationScope.launch {
      stepRepository.forceSync()
    }
```

---

### 6️⃣ PİST DETAY EKRANI VE YARIŞ İLERLEMESİ

#### 6.1 Pist Seçimi ve Görüntüleme (`LeagueHomeScreen.kt`)

**Süreç:**
1. Kullanıcı ana ekrana girer
2. `LeagueManager.getAssignedTrack()` ile atanmış pist alınır
3. Pist bilgileri gösterilir (harita, isim, açıklama)
4. Liderlik tablosu yüklenir (top 10)
5. Kullanıcının sıralaması gösterilir

**Kod Akışı:**
```
LeagueHomeViewModel.loadLeagueData()
  → LeagueManager.getCurrentLeagueInfo()
  → LeagueManager.getAssignedTrack()
  → TrackRepository.getTrack(trackId)
  → StepRepository.selectTrackForMonth(trackId) // Aktif pist set edilir
  → LeagueManager.getLeaderboard()
  → LeagueManager.getUserRank()
```

#### 6.2 Pist Detay Ekranı (`TrackDetailScreen.kt`, `TrackDetailViewModel.kt`)

**Gösterilen Bilgiler:**
- **Google Maps Entegrasyonu:**
  - Pist rotası (GeoJSON)
  - Kullanıcının ilerleme noktası
  - Tur sayısı
  - Mevcut sektör

- **İstatistikler:**
  - Toplam adım sayısı
  - Tamamlanan tur sayısı
  - Mesafe (km)
  - Kalan mesafe
  - Period bilgisi (kalan süre)

- **Liderlik Tablosu:**
  - İlk 10 kullanıcı
  - Kullanıcının sıralaması

**İlerleme Hesaplama (`RaceProgressCalculator.kt`):**
```
Tur Sayısı = (Toplam Adım × Adım Uzunluğu) / Pist Uzunluğu
Kalan Mesafe = Pist Uzunluğu - (Toplam Adım × Adım Uzunluğu % Pist Uzunluğu)
İlerleme Yüzdesi = (Toplam Adım × Adım Uzunluğu) / Pist Uzunluğu × 100
```

**Adım Uzunluğu:** 0.75 metre (varsayılan, Remote Config'den değiştirilebilir)

**Kod Akışı:**
```
TrackDetailViewModel.loadTrackData()
  → StepRepository.getCurrentSteps()
  → TrackRepository.getTrack(trackId)
  → RaceProgressCalculator.calculateProgress(steps, trackLength)
  → LeaderboardRepository.getLeaderboard(trackId, periodId)
```

---

### 7️⃣ LİDERLİK TABLOSU VE REKABET

#### 7.1 Liderlik Tablosu Yapısı (Firestore)

**Koleksiyon Yapısı:**
```
leaderboards/
  {trackId}/
    monthly/
      {periodId}/
        entries/
          {userId}/
            - userId: string
            - displayName: string
            - steps: number
            - lastUpdated: timestamp
            - flagged: boolean (anti-cheat)
```

#### 7.2 Liderlik Tablosu Güncelleme (Firebase Cloud Function)

**Fonksiyon:** `onUserStepSync` (`functions/src/index.ts`)

**Tetikleyici:**
- `users/{userId}` dokümanı güncellendiğinde
- Özellikle `monthlySteps` değiştiğinde

**Süreç:**
1. `users/{userId}` değişikliği algılanır
2. `monthlySteps` değeri alınır
3. `currentMonth` (period ID) alınır
4. `activeTrackId` alınır
5. **Global Leaderboard** güncellenir:
   - `leaderboards/{trackId}/monthly/{periodId}/entries/{userId}`
6. **Lig/Qualifying Leaderboard** güncellenir:
   - Eğer `leagueTier == QUALIFYING` → `qualifying/{trackId}_{periodId}/entries/{userId}`
   - Eğer ligde → `leagues/{leagueId}/members/{userId}`

**Kod Akışı:**
```
Cloud Function: onUserStepSync
  → users/{userId} document write event
  → Extract: monthlySteps, currentMonth, activeTrackId, leagueTier, leagueId
  → Batch update:
      - leaderboards/{trackId}/monthly/{periodId}/entries/{userId}
      - qualifying/{trackId}_{periodId}/entries/{userId} OR
        leagues/{leagueId}/members/{userId}
  → batch.commit()
```

#### 7.3 Liderlik Tablosu Görüntüleme (`LeaderboardScreen.kt`)

**Süreç:**
1. Kullanıcı liderlik tablosuna girer
2. `LeaderboardRepository.getLeaderboard(trackId, periodId)` çağrılır
3. Firestore'dan sıralı veri çekilir (steps DESC)
4. Kullanıcının sıralaması bulunur
5. UI'da gösterilir

**Kod Akışı:**
```
LeaderboardViewModel.loadLeaderboard()
  → LeaderboardRepository.getLeaderboard(trackId, periodId)
  → Firestore query: orderBy("steps", DESC).limit(100)
  → Calculate user rank
  → Update UI state
```

---

### 8️⃣ ROZET SİSTEMİ

#### 8.1 Rozet Türleri (`BadgeType.kt`)

**Temel Rozetler:**
- **FORMATION_LAP:** İlk adım atıldığında
- **CHECKERED_FLAG:** İlk tur tamamlandığında (1 lap)
- **NIGHT_RACE:** Gece 22:00-05:00 arası 1000+ adım
- **ENDURANCE_PILOT:** 42.195 km (maraton) tamamlandığında

**Şampiyonluk Rozetleri:**
- **PERIOD_CHAMPION:** Dönem sonunda 1. sırada
- **PODIUM_FINISH:** Dönem sonunda ilk 3'te

#### 8.2 Rozet Kontrolü (`BadgeManager.kt`)

**Otomatik Kontrol:**
- Adım eklendiğinde (`StepRepository.addSteps()`)
- `BadgeManager.checkBadges()` çağrılır
- Rozet kriterleri kontrol edilir
- Yeni rozet kazanıldıysa → Room DB'ye kaydedilir
- UI event emit edilir (snackbar gösterimi için)

**Kod Akışı:**
```
StepRepository.addSteps(count)
  → BadgeManager.checkBadges(totalSteps, distance, laps, sessionSteps)
  → BadgeManager.checkAndEarn(BadgeType)
  → UserBadgeDao.insertBadge(badge)
  → BadgeManager._badgeEarnedEvents.emit(type)
  → MainActivity → Snackbar gösterilir
```

#### 8.3 Şampiyonluk Rozetleri (`StepSyncManager.awardChampionBadge()`)

**Dönem Sonu Süreci:**
1. Dönem bittiğinde (period end)
2. `StepSyncManager.checkPeriodEnd()` çağrılır
3. Liderlik tablosu kontrol edilir
4. Kullanıcı ilk 3'teyse:
   - Firestore'a rozet kaydedilir: `users/{userId}/championBadges/{badgeId}`
   - Room DB'ye kaydedilir
   - UI event emit edilir

**Kod Akışı:**
```
StepSyncManager.checkPeriodEnd()
  → LeaderboardRepository.getUserRank(userId, trackId, periodId)
  → if (rank <= 3) → BadgeManager.emitChampionBadge(type, trackName, rank)
  → Firestore: users/{userId}/championBadges/{badgeId}
  → Room DB: UserBadgeDao.insertBadge(badge)
```

---

### 9️⃣ LİG YÜKSELME VE DÜŞME SİSTEMİ

#### 9.1 Dönem Sonu İşlemleri (`LeagueManager.checkPromotion()`)

**Süreç:**
1. Dönem bittiğinde (period end)
2. Kullanıcının ligdeki sıralaması kontrol edilir
3. **Yükselme Kontrolü:**
   - İlk N'de (promotion_threshold, varsayılan 5)
   - Mevcut lig LEGEND değilse
   - Bir üst lige yükselir
   - Yeni pist atanır
   - Yeni lige kaydedilir

4. **Düşme Kontrolü:**
   - Son N'de (demotion_threshold, varsayılan 5)
   - Mevcut lig QUALIFYING değilse
   - Bir alt lige düşer
   - Yeni pist atanır
   - Yeni lige kaydedilir

**Kod Akışı:**
```
LeagueManager.checkPromotion(userId, periodId, userRank)
  → getUserLeagueInfo(userId)
  → if (rank <= promotion_threshold && canPromote())
      → nextTier()
      → getTrackForTier(newTier)
      → findOrCreateLeague(newTier, newTrackId)
      → updateUserLeague(userId, newTier, newLeagueId)
      → addUserToLeague(userId, newLeagueId, displayName)
      → StepSyncManager.forceUpdateActiveTrack(newTrackId)
      → PromotionResult(isPromotion = true)
  → else if (rank > demotion_threshold && canDemote())
      → previousTier()
      → (similar process)
      → PromotionResult(isPromotion = false)
```

#### 9.2 Lig Değişikliği Bildirimi

**Süreç:**
1. Lig değişikliği algılandığında
2. `StepSyncManager.leagueChangeEvents` emit edilir
3. `MainActivity`'de dinlenir
4. Snackbar gösterilir: "🎉 TEBRİKLER! BRONZE → SILVER ligine yükseldiniz!"

**Kod Akışı:**
```
LeagueManager.checkPromotion() → PromotionResult
  → StepSyncManager._leagueChangeEvents.emit(event)
  → MainActivity LaunchedEffect → Snackbar
```

---

### 🔟 PERİYODİK YARIŞMA SİSTEMİ

#### 10.1 Period Hesaplama (`StepSyncManager.getCurrentPeriodInfo()`)

**Remote Config Parametreleri:**
- `race_duration_days`: Yarışma süresi (gün, varsayılan 30)
- `race_start_date`: Başlangıç tarihi (yyyy-MM-dd, varsayılan 2026-01-01)
- `race_display_name`: Yarışma adı (örn: "Sevgililer Günü Sprintu")
- `race_description`: Açıklama
- `race_emoji`: Emoji (🏆)

**Period Hesaplama:**
```
daysSinceStart = (Bugün - Başlangıç Tarihi) gün sayısı
periodNumber = (daysSinceStart / race_duration_days) tamsayı bölümü
periodId = "period_" + periodNumber

Örnek:
Başlangıç: 2026-01-01
Süre: 7 gün
Bugün: 2026-01-08
daysSinceStart = 7
periodNumber = 7 / 7 = 1
periodId = "period_1"
```

**Period Bilgisi:**
- `periodId`: "period_0", "period_1", ...
- `startDate`: Period başlangıç tarihi
- `endDate`: Period bitiş tarihi
- `daysRemaining`: Kalan gün sayısı
- `hoursRemaining`: Kalan saat sayısı
- `remainingTimeDisplay`: "5 gün kaldı" formatında string

#### 10.2 Period Sonu İşlemleri (Firebase Cloud Function)

**Fonksiyon:** `processPeriodEnd` (`functions/src/index.ts`)

**Zamanlama:**
- Her gece 00:01'de çalışır (Firebase Pub/Sub Scheduler)
- Timezone: Europe/Istanbul

**Süreç:**
1. Tüm ligleri tarar
2. Her ligin sıralamasını kontrol eder
3. İlk 3'e rozet verir
4. Yükselme/düşme işlemlerini yapar (gelecekte)

**Kod Akışı:**
```
Cloud Function: processPeriodEnd (scheduled)
  → For each league in leagues/
      → processLeague(leagueDoc)
        → Get members ordered by steps DESC
        → Award badges to top 3
        → (Promotion/demotion logic - future)
```

---

### 1️⃣1️⃣ ANTI-CHEAT SİSTEMİ

#### 11.1 Anti-Cheat Kontrolleri (Firebase Cloud Function)

**Fonksiyon:** `onLeaderboardUpdate` (`functions/src/index.ts`)

**Tetikleyici:**
- `leaderboards/{trackId}/monthly/{periodId}/entries/{userId}` güncellendiğinde

**Kontrol Kriterleri:**
1. **İmkansız Hız:** Dakikada 200+ adım (koşarak bile imkansız sürekli)
2. **Spike Tespiti:** 5 dakikada 2000+ adım veya 15 dakikada 5000+ adım
3. **Gece Aktivitesi:** 00:00-05:00 arası 3000+ adım artışı
4. **Günlük Limit:** Günde 60000+ adım

**Süreç:**
1. Leaderboard entry güncellendiğinde
2. Adım artışı (delta) hesaplanır
3. Zaman farkı hesaplanır
4. Şüpheli aktivite kontrol edilir
5. İhlal varsa:
   - `antiCheatLogs` koleksiyonuna log kaydedilir
   - Şüphe skoru >= 50 ise kullanıcı flag'lenir
   - `users/{userId}/antiCheat.flagged = true`
   - `leaderboards/.../entries/{userId}/flagged = true`

**Kod Akışı:**
```
Cloud Function: onLeaderboardUpdate
  → Calculate stepDelta, timeDelta
  → Check violations:
      - IMPOSSIBLE_SPEED
      - SPIKE_5MIN
      - SPIKE_15MIN
      - NIGHT_ACTIVITY
      - DAILY_LIMIT
  → If violations.length > 0:
      → antiCheatLogs.add({ violations, suspicionScore, ... })
      → If suspicionScore >= 50:
          → flagUser(userId, score, violations)
            → users/{userId}/antiCheat.flagged = true
            → leaderboards/.../flagged = true
```

#### 11.2 Manuel Giriş Tespiti (`HealthConnectManager`)

**Süreç:**
1. Health Connect'ten adım okunurken
2. `readRecords()` ile manuel girişler kontrol edilir
3. Manuel girişler çıkarılır
4. Doğrulanmış adım sayısı kullanılır

---

### 1️⃣2️⃣ VERİTABANI YAPISI

#### 12.1 Room Database (Local - SQLite)

**Tablolar:**
- **user_progress:** Kullanıcının pist bazlı ilerlemesi
  - `userId`, `trackId`, `totalSteps`, `completedLoops`, `allTimeSteps`, `allTimeLaps`, `periodStartTime`
- **user_badges:** Kazanılan rozetler
  - `badgeId`, `earnedTimestamp`
- **lap_history:** Tur geçmişi
  - `trackId`, `userId`, `startTime`, `endTime`, `durationSeconds`, `lapNumber`, `periodId`
- **daily_step_log:** Günlük adım logları
  - `date`, `trackId`, `userId`, `steps`, `distance`
- **period_history:** Dönem geçmişi
  - `periodId`, `userId`, `trackId`, `totalSteps`, `completedLaps`, `finalRank`, `archivedAt`
- **tracks:** Pist bilgileri (cache)
  - `id`, `genericName`, `description`, `geoJsonUrl`, `totalDistanceMeters`

**DAO'lar:**
- `UserProgressDao`
- `UserBadgeDao`
- `LapHistoryDao`
- `DailyStepLogDao`
- `PeriodHistoryDao`

#### 12.2 Firestore Database (Cloud)

**Koleksiyonlar:**
- **users/{userId}:** Kullanıcı bilgileri
  - `uid`, `displayName`, `email`, `isSetupCompleted`, `leagueTier`, `leagueId`, `monthlySteps`, `currentMonth`, `activeTrackId`, `antiCheat`
- **leaderboards/{trackId}/monthly/{periodId}/entries/{userId}:** Global liderlik tablosu
- **qualifying/{trackId}_{periodId}/entries/{userId}:** Eleme havuzu
- **leagues/{leagueId}/members/{userId}:** Lig üyeleri
- **leagues/{leagueId}:** Lig bilgileri
  - `tier`, `trackId`, `periodId`, `memberCount`
- **antiCheatLogs/{logId}:** Anti-cheat logları
- **tracks/{trackId}:** Pist bilgileri

---

### 1️⃣3️⃣ UI EKRANLARI VE NAVİGASYON

#### 13.1 Navigation Yapısı

```
login (start if not authenticated)
  ↓
onboarding (if !isSetupCompleted)
  ↓
track_selection (LeagueHomeScreen)
  ↓
  ├─→ track_detail/{trackId} (TrackDetailScreen)
  │     ├─→ leaderboard/{trackId} (LeaderboardScreen)
  │     └─→ global_stats (GlobalStatsScreen)
  │
  └─→ profile (ProfileScreen)
        └─→ login (sign out)
```

#### 13.2 Ana Ekranlar

**LoginScreen:**
- Google Sign-In butonu
- Anonim devam butonu
- Giriş durumu gösterimi

**OnboardingScreen:**
- Uygulama tanıtımı
- Kullanıcı bilgilendirme

**LeagueHomeScreen:**
- Atanmış pist gösterimi
- Lig bilgisi
- Top 10 liderlik tablosu
- Kullanıcı sıralaması

**TrackDetailScreen:**
- Google Maps entegrasyonu (pist rotası)
- İlerleme göstergesi
- İstatistikler (adım, tur, mesafe)
- Period bilgisi
- Liderlik tablosu butonu

**LeaderboardScreen:**
- Tam liderlik tablosu
- Kullanıcı konumu highlight
- Filtreleme (lig bazlı)

**GlobalStatsScreen:**
- Tüm zamanlar istatistikleri
- Period bazlı breakdown
- Rozet koleksiyonu
- Geçmiş dönemler

**ProfileScreen:**
- Kullanıcı bilgileri
- Lig durumu
- Rozetler
- Çıkış yap butonu

---

### 1️⃣4️⃣ REMOTE CONFIG YÖNETİMİ

#### 14.1 Remote Config Parametreleri (`RemoteConfigManager.kt`)

**Reklam Ayarları:**
- `show_interstitial_ads`: Boolean (reklamlar gösterilsin mi)
- `ad_frequency`: Number (kaç adımda bir reklam)

**Uygulama Ayarları:**
- `min_version_code`: Minimum uygulama versiyonu
- `pit_stop_messages`: JSON (Pit Stop uyarı mesajları)
- `background_step_limit`: Arka plan adım limiti

**Yarışma Ayarları:**
- `race_duration_days`: Yarışma süresi (gün)
- `race_start_date`: Başlangıç tarihi (yyyy-MM-dd)
- `race_display_name`: Yarışma adı
- `race_description`: Açıklama
- `race_emoji`: Emoji

**Lig Ayarları:**
- `tier_tracks`: JSON (Lig → Pist mapping)
- `promotion_threshold`: Yükselme eşiği (ilk N)
- `demotion_threshold`: Düşme eşiği (son N)
- `league_size`: Lig başına kullanıcı sayısı

#### 14.2 Remote Config Kullanımı

**Fetch Stratejisi:**
- Debug: 0 saniye (anında fetch)
- Release: 12 saat cache (43200 saniye)

**Kod Akışı:**
```
RemoteConfigManager.init
  → setConfigSettingsAsync(minimumFetchInterval)
  → setDefaultsAsync(defaultValues)
  → fetchAndActivate()
  → updateConfigState()
  → updatePitStopMessages()
  → updateRaceDuration()
  → updateLeagueSettings()
```

---

### 1️⃣5️⃣ MONETİZASYON

#### 15.1 Reklam Sistemi (`AdManager.kt`)

**Reklam Türleri:**
- **Interstitial Ads:** Tam ekran reklamlar (belirli adım aralıklarında)
- **Banner Ads:** (Gelecekte)

**Reklam Gösterimi:**
- Remote Config'den `ad_frequency` alınır (varsayılan 3000 adım)
- Kullanıcı belirli adım sayısını geçtiğinde reklam gösterilir
- AdMob SDK kullanılır

#### 15.2 Abonelik Sistemi (`SubscriptionManager.kt`)

**RevenueCat Entegrasyonu:**
- Premium abonelik yönetimi
- (Gelecekte) Reklamsız deneyim, premium pistler

---

### 1️⃣6️⃣ ÖNEMLİ YÖNETİCİ SINIFLARI

#### 16.1 StepSyncManager

**Sorumluluklar:**
- Period hesaplama
- Period sonu kontrolü
- Şampiyonluk rozeti verme
- Lig değişikliği event'leri
- Aktif pist yönetimi

#### 16.2 LeagueManager

**Sorumluluklar:**
- Lig bilgisi yönetimi
- Pist ataması
- Yeni kullanıcı kaydı
- Yükselme/düşme kontrolü
- Liderlik tablosu yönetimi

#### 16.3 BadgeManager

**Sorumluluklar:**
- Rozet kontrolü
- Rozet kazanma
- Rozet event'leri
- Rozet veritabanı yönetimi

#### 16.4 HealthConnectManager

**Sorumluluklar:**
- Health Connect bağlantısı
- İzin yönetimi
- Adım okuma
- Manuel giriş tespiti

#### 16.5 RemoteConfigManager

**Sorumluluklar:**
- Remote Config fetch
- Config cache yönetimi
- Config state yönetimi
- Default değerler

---

## 📊 VERİ AKIŞI ÖZETİ

### Adım Verisi Akışı:
```
Health Connect (Sensörler)
    ↓
HealthConnectManager.readStepsByTimeRange()
    ↓
StepRepositoryImpl.syncHealthConnectSteps()
    ↓
Room Database (UserProgress) - Local Cache
    ↓
Firebase Firestore (users/{userId}/monthlySteps)
    ↓
Firebase Cloud Function (onUserStepSync)
    ↓
Leaderboard Collections (leaderboards/, qualifying/, leagues/)
    ↓
UI Updates (StateFlow/State)
```

### Kullanıcı Akışı:
```
App Start
    ↓
Authentication Check
    ↓ (if not authenticated)
Login Screen
    ↓ (Google/Anonymous Sign-In)
Onboarding (if first time)
    ↓
Permission Setup
    ↓
League Home Screen
    ↓
Track Detail Screen
    ↓ (periodic sync every 10s)
Step Sync Loop
```

---

## 🔑 ÖNEMLİ ÖZELLİKLER

### 1. Offline-First Yaklaşım
- Room Database ile lokal cache
- Offline çalışma desteği
- Senkronizasyon arka planda

### 2. Gerçek Zamanlı Senkronizasyon
- 10 saniyede bir sync (uygulama açıkken)
- 15 dakikada bir sync (arka planda)
- WorkManager ile güvenilir sync

### 3. Anti-Cheat Sistemi
- Cloud Functions ile server-side kontrol
- Manuel giriş tespiti
- Şüpheli aktivite flag'leme

### 4. Dinamik Konfigürasyon
- Remote Config ile anında güncelleme
- A/B testing desteği
- Feature flag yönetimi

### 5. Modüler Mimari
- Clean Architecture
- Dependency Injection (Hilt)
- Test edilebilir yapı

---

## 🎯 SONUÇ

**Pace Legends**, kapsamlı bir fitness yarış uygulamasıdır. Kullanıcılar adımlarını atarak F1 temalı bir deneyim yaşarlar, liglerde yükselir, rozetler kazanır ve diğer kullanıcılarla rekabet ederler. Uygulama, modern Android geliştirme pratikleri, güvenilir veri senkronizasyonu, anti-cheat sistemi ve dinamik konfigürasyon yönetimi ile donatılmıştır.

---

**Rapor Sonu** ✅

*Bu rapor, projenin mevcut kod tabanı analiz edilerek oluşturulmuştur. Detaylar proje dosyalarından çıkarılmıştır.*
