# 🗓️ PACE LEGENDS - 3 AYLIK SPRINT PLANI
## Q1 2026 (Ocak - Mart) - Kritik İyileştirmeler & Güvenlik Hardening

> **Hedef:** Sync mekanizmasını güvenli ve tutarlı hale getirmek, performans optimizasyonları yapmak ve teknik borcu azaltmak.

---

## 📊 SPRINT YAPISI

- **Sprint Süresi:** 2 hafta
- **Toplam Sprint Sayısı:** 6 sprint (12 hafta)
- **Takım Büyüklüğü:** 3-4 developer (1 Senior Android, 1 Backend/Firebase, 1 QA/Automation, 1 Junior Android)
- **Sprint Kapasitesi:** ~40 story point / sprint (Fibonacci: 1, 2, 3, 5, 8, 13, 21)

---

## 🎯 EPIC'LER VE ÖNCELİK SIRASI

| Epic | Öncelik | Tahmini SP | Sprint Aralığı | Bağımlılıklar |
|------|---------|------------|----------------|---------------|
| **E1: Sync & Anti-Cheat Güvenlik** | 🔴 P0 | 34 SP | Sprint 1-2 | - |
| **E2: Firestore Security Rules & Cost Optimization** | 🔴 P0 | 21 SP | Sprint 1-2 | E1 (partial) |
| **E3: Compose Performance & UX** | 🟡 P1 | 18 SP | Sprint 2-3 | - |
| **E4: UseCase-First Domain Refactoring** | 🟡 P1 | 26 SP | Sprint 3-4 | E1 |
| **E5: Test Suite & CI/CD** | 🟡 P1 | 29 SP | Sprint 4-5 | E4 |
| **E6: Data Retention & Room Optimization** | 🟢 P2 | 13 SP | Sprint 5-6 | E2 |

**Toplam:** ~141 Story Point (3 sprint buffer ile ~180 SP kapasite)

---

## 📅 SPRINT BREAKDOWN

---

### 🚀 SPRINT 1 (Hafta 1-2): Sync & Security Foundation

**Sprint Goal:** Sync mekanizmasını idempotent hale getirmek ve Firestore Security Rules'ı sertleştirmek.

#### Epic E1: Sync & Anti-Cheat Güvenlik (Part 1)

**Story S1.1: Idempotent Sync Session Model** (8 SP)
- **Açıklama:** `users/{uid}/stepSyncEvents/{syncId}` append-only modeli implementasyonu
- **Acceptance Criteria:**
  - Client her sync'te unique `syncToken` (UUID) üretir
  - Cloud Function `syncToken` ile idempotency check yapar
  - Aynı token ile tekrar sync → ignored (duplicate)
  - `stepSyncEvents` koleksiyonu append-only (client yazamaz, sadece CF)
- **Tasks:**
  - [ ] `StepSyncManager.kt` → `syncToken` generation ekle (5 SP)
  - [ ] Cloud Function `onUserStepSync` → idempotency check ekle (3 SP)
- **Definition of Done:**
  - Unit test: duplicate sync token → ignored
  - Integration test: 2x aynı token → sadece 1 write
  - Code review + security review

**Story S1.2: Server-Side Validation & Anomaly Detection** (13 SP)
- **Açıklama:** Cloud Function'da adım artışı için hız limiti ve anomaly score hesaplama
- **Acceptance Criteria:**
  - `deltaSteps > MAX_DELTA` (örn. 40k/saat) → flag
  - Z-score hesaplama (kullanıcının geçmiş ortalamasına göre)
  - Anomaly score > threshold → `antiCheatLogs` collection'a yaz
  - Flagged kullanıcılar için `users/{uid}.antiCheat.flagged = true`
- **Tasks:**
  - [ ] CF: `validateStepDelta()` fonksiyonu (5 SP)
  - [ ] CF: `calculateAnomalyScore()` fonksiyonu (5 SP)
  - [ ] CF: `flagUser()` entegrasyonu (3 SP)
- **Definition of Done:**
  - Test: 50k adım tek seferde → flag
  - Test: normal kullanıcı → flag yok
  - Monitoring dashboard'da anomaly rate görünür

#### Epic E2: Firestore Security Rules (Part 1)

**Story S1.3: Security Rules Hardening** (8 SP)
- **Açıklama:** `firestore.rules` dosyasını güvenlik açıklarını kapatacak şekilde güncelle
- **Acceptance Criteria:**
  - `leaderboards/*` → sadece Cloud Functions yazabilir (client read-only)
  - `users/{uid}` → sadece kendi userId'si ile yazabilir
  - `stepSyncEvents` → sadece Cloud Functions yazabilir
  - `antiCheatLogs` → client sadece kendi userId'si için create (append-only)
- **Tasks:**
  - [ ] `firestore.rules` revizyonu (5 SP)
  - [ ] Security rules test suite (Firebase Emulator) (3 SP)
- **Definition of Done:**
  - `firebase emulators:exec --only firestore "npm test"` → tüm testler geçer
  - Penetration test: client'tan `leaderboards` yazma denemesi → fail
  - Code review + security audit

**Story S1.4: Cost Optimization - Throttling Improvements** (5 SP)
- **Açıklama:** `StepSyncManager` throttling mantığını optimize et (500 adım → 1000 adım, 15dk → 20dk)
- **Acceptance Criteria:**
  - Remote Config'den `sync_step_threshold` ve `sync_interval_minutes` okunur
  - Default: 1000 adım / 20 dakika
  - Foreground'da daha agresif, background'da daha konservatif
- **Tasks:**
  - [ ] `StepSyncManager` → Remote Config entegrasyonu (3 SP)
  - [ ] A/B test için feature flag ekle (2 SP)
- **Definition of Done:**
  - Remote Config'de değerler görünür
  - Test: threshold değişince sync davranışı değişir

**Sprint 1 Toplam:** 34 SP

---

### 🚀 SPRINT 2 (Hafta 3-4): Sync Completion & Performance Quick Wins

**Sprint Goal:** Sync mekanizmasını tamamlamak ve Compose performans optimizasyonlarına başlamak.

#### Epic E1: Sync & Anti-Cheat Güvenlik (Part 2)

**Story S2.1: Conflict Resolution & Period Transition** (8 SP)
- **Açıklama:** Dönem geçişlerinde (period transition) adım verilerinin doğru şekilde reset edilmesi
- **Acceptance Criteria:**
  - Yeni dönem başladığında `monthlySteps` reset edilir
  - Eski dönem verisi `users/{uid}/periodHistory/{periodId}` altına kaydedilir
  - `lastSyncTimestamp` yeni dönem başlangıcına set edilir
  - Cloud Function dönem geçişini algılar ve `periodHistory` oluşturur
- **Tasks:**
  - [ ] `StepSyncManager.checkForPeriodTransition()` iyileştirme (4 SP)
  - [ ] CF: `onPeriodTransition` scheduled function (4 SP)
- **Definition of Done:**
  - Test: dönem geçişi simülasyonu → `periodHistory` oluşur
  - Test: yeni dönemde adımlar sıfırdan başlar

**Story S2.2: Device Fingerprinting & Trust Score** (5 SP)
- **Açıklama:** Kullanıcı cihazı için trust score hesaplama (root/jailbreak tespiti, OS versiyonu)
- **Acceptance Criteria:**
  - Client-side: `DeviceInfo` modeli (deviceId hash, OS version, isRooted)
  - Sync sırasında `deviceInfo` Firestore'a yazılır
  - Cloud Function: `isRooted = true` → trust score düşer
  - Trust score < threshold → flag
- **Tasks:**
  - [ ] `DeviceInfo` modeli ve collection (2 SP)
  - [ ] Root detection (Android SafetyNet / Play Integrity) (3 SP)
- **Definition of Done:**
  - Test: root cihaz → trust score düşük
  - Monitoring: trust score distribution görünür

#### Epic E2: Firestore Security Rules (Part 2)

**Story S2.3: Security Rules Testing & Deployment** (5 SP)
- **Açıklama:** Security rules'ı production'a deploy et ve monitoring ekle
- **Acceptance Criteria:**
  - Staging environment'da test edilir
  - Production'a gradual rollout (feature flag ile)
  - Firestore audit logs monitoring
- **Tasks:**
  - [ ] Staging deployment (2 SP)
  - [ ] Monitoring dashboard (3 SP)
- **Definition of Done:**
  - Staging'de 24 saat test → hiç security violation yok
  - Production'da gradual rollout başarılı

#### Epic E3: Compose Performance & UX (Part 1)

**Story S2.4: Compose Recomposition Optimization** (8 SP)
- **Açıklama:** `TrackDetailScreen` ve `LeaderboardScreen` için recomposition optimizasyonu
- **Acceptance Criteria:**
  - `derivedStateOf` kullanımı → gereksiz recomposition azalır
  - State splitting → `TrackMap` sadece path değişince recompose
  - JankStats ile ölçüm: jank rate < %1
- **Tasks:**
  - [ ] `TrackDetailScreen` → state splitting (3 SP)
  - [ ] `LeaderboardScreen` → lazy list optimization (3 SP)
  - [ ] JankStats integration & baseline measurement (2 SP)
- **Definition of Done:**
  - JankStats: jank rate %1'in altında
  - Layout Inspector: recomposition count azaldı
  - Code review

**Story S2.5: WorkManager Battery Optimization** (5 SP)
- **Açıklama:** `DataSyncWorker` için battery-friendly constraints ve adaptive polling
- **Acceptance Criteria:**
  - Charging + unmetered network → agresif sync
  - Battery saver mode → minimal sync (30dk)
  - Gece saatleri (00:00-06:00) → sync interval artar (1 saat)
- **Tasks:**
  - [ ] `DataSyncWorker` → constraints ekle (3 SP)
  - [ ] Adaptive polling logic (2 SP)
- **Definition of Done:**
  - Test: battery saver mode → sync azalır
  - Battery consumption test → %10 azalma

**Sprint 2 Toplam:** 31 SP

---

### 🚀 SPRINT 3 (Hafta 5-6): UX Improvements & Domain Refactoring Start

**Sprint Goal:** Kullanıcı deneyimini iyileştirmek ve UseCase-first domain yapısına geçişe başlamak.

#### Epic E3: Compose Performance & UX (Part 2)

**Story S3.1: Offline Mode UX Enhancements** (5 SP)
- **Açıklama:** Offline durumunda kullanıcıya net feedback verme
- **Acceptance Criteria:**
  - "Son güncelleme: X dakika önce" mesajı gösterilir
  - Offline iken leaderboard güncellenemez → snackbar göster
  - Pull-to-refresh → retry mekanizması
- **Tasks:**
  - [ ] `LeaderboardScreen` → offline state UI (3 SP)
  - [ ] `TrackDetailScreen` → last sync timestamp gösterimi (2 SP)
- **Definition of Done:**
  - Test: airplane mode → offline mesajı görünür
  - UX review

**Story S3.2: Loading States & Skeleton Screens** (5 SP)
- **Açıklama:** Tüm ekranlarda shimmer/skeleton loading ekle
- **Acceptance Criteria:**
  - `LeaderboardScreen` → skeleton list
  - `TrackDetailScreen` → shimmer map placeholder
  - Loading süresi > 500ms ise skeleton göster
- **Tasks:**
  - [ ] Skeleton component library (2 SP)
  - [ ] Tüm ekranlara entegrasyon (3 SP)
- **Definition of Done:**
  - Tüm ekranlarda loading state var
  - UX review

**Story S3.3: Error Handling & User Feedback** (5 SP)
- **Açıklama:** Network hataları, Health Connect izin reddi için kullanıcı dostu mesajlar
- **Acceptance Criteria:**
  - Health Connect izin reddi → "Ayarlar'a git" butonu
  - Network error → retry butonu
  - Firestore permission denied → "Yöneticiye bildir" mesajı
- **Tasks:**
  - [ ] Error message mapping (2 SP)
  - [ ] Error dialog/snackbar components (3 SP)
- **Definition of Done:**
  - Tüm error senaryoları test edildi
  - UX review

#### Epic E4: UseCase-First Domain Refactoring (Part 1)

**Story S3.4: SyncStepsUseCase Implementation** (8 SP)
- **Açıklama:** `StepSyncManager.syncIfNeeded()` mantığını `SyncStepsUseCase`'e taşı
- **Acceptance Criteria:**
  - `SyncStepsUseCase` → `StepRepository` ve `StepSyncManager` kullanır
  - Throttling mantığı UseCase içinde
  - `StepSyncManager` sadece Firestore write yapar
- **Tasks:**
  - [ ] `SyncStepsUseCase` oluştur (5 SP)
  - [ ] `StepSyncManager` refactor (3 SP)
- **Definition of Done:**
  - Unit test: `SyncStepsUseCase` test coverage > %80
  - Integration test: sync flow çalışıyor
  - Code review

**Story S3.5: ObserveUserProgressUseCase** (5 SP)
- **Açıklama:** `TrackDetailViewModel` için `UserProgress` Flow'unu UseCase'e taşı
- **Acceptance Criteria:**
  - `ObserveUserProgressUseCase` → `StepRepository` ve `TrackRepository` kullanır
  - Flow: `UserProgress` → `TrackUiState` mapping
- **Tasks:**
  - [ ] UseCase oluştur (3 SP)
  - [ ] `TrackDetailViewModel` refactor (2 SP)
- **Definition of Done:**
  - Test: UseCase Flow test
  - UI test: `TrackDetailScreen` çalışıyor

**Sprint 3 Toplam:** 28 SP

---

### 🚀 SPRINT 4 (Hafta 7-8): Domain Refactoring & Test Foundation

**Sprint Goal:** UseCase pattern'ini tüm kritik akışlara yaymak ve test altyapısını kurmak.

#### Epic E4: UseCase-First Domain Refactoring (Part 2)

**Story S4.1: RegisterToLeagueUseCase** (5 SP)
- **Açıklama:** `LeagueRegistrationWorker` mantığını UseCase'e taşı
- **Acceptance Criteria:**
  - `RegisterToLeagueUseCase` → `LeagueManager` ve `LeagueRepository` kullanır
  - Retry logic UseCase içinde
- **Tasks:**
  - [ ] UseCase oluştur (3 SP)
  - [ ] `LeagueRegistrationWorker` refactor (2 SP)
- **Definition of Done:**
  - Test: UseCase test coverage
  - Worker test: retry çalışıyor

**Story S4.2: GetLeaderboardFlowUseCase** (5 SP)
- **Açıklama:** `LeaderboardViewModel` için leaderboard Flow'unu UseCase'e taşı
- **Acceptance Criteria:**
  - `GetLeaderboardFlowUseCase` → `LeaderboardRepository` kullanır
  - Cache-first strategy (Room → Firestore fallback)
- **Tasks:**
  - [ ] UseCase oluştur (3 SP)
  - [ ] `LeaderboardViewModel` refactor (2 SP)
- **Definition of Done:**
  - Test: UseCase Flow test
  - UI test: `LeaderboardScreen` çalışıyor

**Story S4.3: UpdateActiveTrackUseCase** (3 SP)
- **Açıklama:** Pist değişikliği mantığını UseCase'e taşı
- **Acceptance Criteria:**
  - `UpdateActiveTrackUseCase` → `TrackRepository` ve `StepSyncManager` kullanır
  - Pist değişince sync tetiklenir
- **Tasks:**
  - [ ] UseCase oluştur (2 SP)
  - [ ] ViewModel refactor (1 SP)
- **Definition of Done:**
  - Test: UseCase test
  - Integration test: pist değişikliği çalışıyor

#### Epic E5: Test Suite & CI/CD (Part 1)

**Story S4.4: Unit Test Infrastructure** (8 SP)
- **Açıklama:** Tüm UseCase'ler için unit test suite
- **Acceptance Criteria:**
  - Mock repository'ler (MockK veya manual mocks)
  - Test coverage > %70 (UseCase'ler için)
  - Test naming convention: `Given_When_Then`
- **Tasks:**
  - [ ] Test utilities & mocks (3 SP)
  - [ ] UseCase test suite (5 SP)
- **Definition of Done:**
  - `./gradlew test` → tüm testler geçer
  - Coverage report: > %70

**Story S4.5: Compose UI Test Setup** (5 SP)
- **Açıklama:** Compose UI test altyapısı ve ilk testler
- **Acceptance Criteria:**
  - `androidx.compose.ui.test` setup
  - `TrackDetailScreen` ve `LeaderboardScreen` için basic UI testler
- **Tasks:**
  - [ ] Test setup (2 SP)
  - [ ] İlk UI testler (3 SP)
- **Definition of Done:**
  - `./gradlew connectedCheck` → UI testler geçer
  - Test report görünür

**Sprint 4 Toplam:** 26 SP

---

### 🚀 SPRINT 5 (Hafta 9-10): CI/CD & Data Optimization

**Sprint Goal:** CI/CD pipeline'ı kurmak ve Room/Firestore optimizasyonlarını yapmak.

#### Epic E5: Test Suite & CI/CD (Part 2)

**Story S5.1: GitHub Actions CI Pipeline** (8 SP)
- **Açıklama:** GitHub Actions ile otomatik test ve lint
- **Acceptance Criteria:**
  - PR açıldığında → `./gradlew lint ktlintCheck detekt test`
  - Test başarısız → PR merge edilemez
  - Coverage report → PR comment'inde gösterilir
- **Tasks:**
  - [ ] `.github/workflows/ci.yml` oluştur (5 SP)
  - [ ] Coverage reporting (3 SP)
- **Definition of Done:**
  - Test PR → CI çalışır
  - Coverage report görünür

**Story S5.2: Firebase Test Lab Integration** (5 SP)
- **Açıklama:** Kritik cihazlarda UI testleri çalıştırma
- **Acceptance Criteria:**
  - CI'da Firebase Test Lab'e deploy
  - Test matrix: Pixel 5 (API 30), Pixel 7 (API 33)
  - Test sonuçları → CI'da görünür
- **Tasks:**
  - [ ] Firebase Test Lab setup (3 SP)
  - [ ] CI entegrasyonu (2 SP)
- **Definition of Done:**
  - CI'da Test Lab çalışır
  - Test sonuçları görünür

**Story S5.3: E2E Test Suite (Firebase Emulators)** (8 SP)
- **Açıklama:** Firebase Emulators ile end-to-end testler
- **Acceptance Criteria:**
  - Auth, Firestore, Functions emulators
  - Fake Health Connect provider
  - E2E test: login → sync → leaderboard görüntüleme
- **Tasks:**
  - [ ] Emulator setup (4 SP)
  - [ ] E2E test suite (4 SP)
- **Definition of Done:**
  - `firebase emulators:exec "npm test"` → E2E testler geçer
  - Test report görünür

#### Epic E6: Data Retention & Room Optimization

**Story S5.4: Room Schema Optimization** (5 SP)
- **Açıklama:** Room index'leri ve TTL mekanizması
- **Acceptance Criteria:**
  - `LeaderboardCacheEntity` → composite index ekle
  - TTL: 5 dakika (leaderboard cache)
  - `UserProgressEntity` → `lastUpdated` field ekle
- **Tasks:**
  - [ ] Room migration (3 SP)
  - [ ] TTL logic (2 SP)
- **Definition of Done:**
  - Migration test: eski DB → yeni DB
  - Performance test: query süresi azaldı

**Story S5.5: Firestore Data Retention Policy** (3 SP)
- **Açıklama:** Eski leaderboard verilerini otomatik silme
- **Acceptance Criteria:**
  - Cloud Function: `cleanupOldLeaderboards` (scheduled, günlük)
  - 6 aydan eski `leaderboards/*/monthly/*/entries` silinir
  - `periodHistory` 24 ay saklanır
- **Tasks:**
  - [ ] CF: cleanup function (2 SP)
  - [ ] Cloud Scheduler setup (1 SP)
- **Definition of Done:**
  - Test: eski veriler silinir
  - Monitoring: storage cost azalır

**Sprint 5 Toplam:** 29 SP

---

### 🚀 SPRINT 6 (Hafta 11-12): Finalization & Documentation

**Sprint Goal:** Kalan işleri tamamlamak, dokümantasyon ve monitoring kurmak.

#### Epic E6: Data Retention & Room Optimization (Part 2)

**Story S6.1: Monitoring Dashboard** (5 SP)
- **Açıklama:** Firebase Performance Monitoring ve Cloud Monitoring dashboard
- **Acceptance Criteria:**
  - Sync success rate metric
  - Anomaly detection rate
  - Firestore cost tracking
  - Alert: sync failure rate > %5
- **Tasks:**
  - [ ] Cloud Monitoring dashboard (3 SP)
  - [ ] Alerting rules (2 SP)
- **Definition of Done:**
  - Dashboard görünür
  - Alert test: başarılı

**Story S6.2: Technical Documentation** (5 SP)
- **Açıklama:** Sync mekanizması, UseCase pattern, test stratejisi dokümantasyonu
- **Acceptance Criteria:**
  - `docs/SYNC_ARCHITECTURE.md` → detaylı sync flow
  - `docs/USECASE_PATTERN.md` → UseCase kullanım rehberi
  - `docs/TESTING_GUIDE.md` → test yazma rehberi
- **Tasks:**
  - [ ] Dokümantasyon yazımı (5 SP)
- **Definition of Done:**
  - Dokümantasyon review
  - Yeni developer onboarding → dokümantasyon kullanılabilir

**Story S6.3: Performance Benchmarking** (5 SP)
- **Açıklama:** Baseline performance metrikleri ve karşılaştırma
- **Acceptance Criteria:**
  - JankStats: jank rate baseline
  - Battery consumption: baseline
  - Firestore cost: baseline
  - App startup time: baseline
- **Tasks:**
  - [ ] Benchmark suite (3 SP)
  - [ ] Baseline report (2 SP)
- **Definition of Done:**
  - Baseline report görünür
  - Gelecek sprint'lerde karşılaştırma yapılabilir

**Story S6.4: Bug Fixes & Polish** (8 SP)
- **Açıklama:** Sprint 1-5'te tespit edilen bug'ları düzeltme
- **Acceptance Criteria:**
  - Critical bug'lar düzeltildi
  - P1 bug'lar düzeltildi (veya backlog'a alındı)
- **Tasks:**
  - [ ] Bug triage (2 SP)
  - [ ] Bug fixes (6 SP)
- **Definition of Done:**
  - Critical bug yok
  - Test suite geçer

**Sprint 6 Toplam:** 23 SP

---

## 📈 METRİKLER VE BAŞARI KRİTERLERİ

### Sprint Başına Takip Edilecek Metrikler

| Metrik | Baseline | Sprint 1-2 Hedef | Sprint 3-4 Hedef | Sprint 5-6 Hedef |
|--------|----------|-------------------|------------------|------------------|
| **Sync Success Rate** | %85 | %90 | %95 | %98 |
| **Anomaly Detection Rate** | %0 | %0.1 | %0.5 | %1 |
| **Jank Rate** | %5 | %3 | %1 | %0.5 |
| **Battery Consumption** | Baseline | -10% | -20% | -30% |
| **Firestore Cost/MAU** | $X | -15% | -30% | -40% |
| **Test Coverage** | %20 | %40 | %60 | %70+ |
| **App Startup Time** | 3.5s | 3.0s | 2.5s | 2.0s |

### Definition of Done (Genel)

- ✅ Code review tamamlandı
- ✅ Unit test coverage > %70 (yeni kod için)
- ✅ Integration test geçti
- ✅ Lint/ktlint/detekt hata vermiyor
- ✅ Security review (güvenlik kritik story'ler için)
- ✅ Dokümantasyon güncellendi (gerekliyse)
- ✅ Monitoring/alerting kuruldu (gerekliyse)

---

## 🚨 RİSK YÖNETİMİ

| Risk | Olasılık | Etki | Mitigasyon |
|------|----------|------|------------|
| **Sync mekanizması production'da sorun çıkarır** | Orta | Yüksek | Staging'de 1 hafta test, gradual rollout, feature flag |
| **Firestore cost beklenenden yüksek** | Düşük | Orta | Cost monitoring dashboard, alerting, throttling artırma |
| **UseCase refactoring regression yaratır** | Orta | Yüksek | Feature flag ile yeni kod, A/B test, rollback planı |
| **CI/CD pipeline kurulumu uzar** | Düşük | Orta | External consultant desteği, basit pipeline ile başla |
| **Test coverage hedefi tutmaz** | Orta | Düşük | Test yazımı için ek zaman, pair programming |

---

## 📝 NOTLAR VE ÖNERİLER

1. **Sprint Planning:** Her sprint başında 2 saat planning, 1 saat retro
2. **Daily Standup:** Her gün 15 dakika (async veya sync)
3. **Code Review:** PR'lar 24 saat içinde review edilmeli
4. **Bug Triage:** Haftalık bug triage meeting (1 saat)
5. **Stakeholder Update:** Her sprint sonunda demo (30 dakika)

---

## 🎯 SONRAKİ ADIMLAR (Q2 2026)

- Multi-module architecture geçişi
- Advanced anti-cheat (ML-based anomaly detection)
- iOS platform expansion hazırlığı
- AI coaching features (premium)
- Social features (friends, private competitions)

---

**Hazırlayan:** Senior Android Developer & System Architect  
**Tarih:** 2026-01-XX  
**Versiyon:** 1.0
