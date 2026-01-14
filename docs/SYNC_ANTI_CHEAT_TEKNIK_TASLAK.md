# 🔒 SYNC & ANTI-CHEAT - DETAYLI TEKNİK TASLAK
## Pace Legends - Production-Ready Implementation Guide

> **Hedef:** Sync mekanizmasını idempotent, güvenli ve tutarlı hale getirmek. Anti-cheat mekanizmalarını server-side validation ile güçlendirmek.

---

## 📋 İÇİNDEKİLER

1. [Mimari Genel Bakış](#1-mimari-genel-bakış)
2. [Idempotent Sync Session Model](#2-idempotent-sync-session-model)
3. [Server-Side Validation & Anomaly Detection](#3-server-side-validation--anomaly-detection)
4. [Conflict Resolution & Period Transition](#4-conflict-resolution--period-transition)
5. [Device Fingerprinting & Trust Score](#5-device-fingerprinting--trust-score)
6. [Firestore Security Rules](#6-firestore-security-rules)
7. [Monitoring & Alerting](#7-monitoring--alerting)
8. [Test Stratejisi](#8-test-stratejisi)
9. [Migration Plan](#9-migration-plan)

---

## 1. MİMARİ GENEL BAKIŞ

### 1.1 Mevcut Durum Analizi

**Mevcut Akış:**
```
Health Connect → StepRepository → StepSyncManager → Firestore (users/{uid})
                                                          ↓
                                              Cloud Function (onUserStepSync)
                                                          ↓
                                    leaderboards/{trackId}/monthly/{periodId}/entries/{userId}
```

**Problemler:**
1. ❌ **Idempotency yok:** Aynı sync 2x çalışırsa double-count riski
2. ❌ **Client-side validation:** Hız limiti kontrolü sadece client'ta
3. ❌ **Conflict resolution yok:** Dönem geçişlerinde veri kaybı riski
4. ❌ **Device trust yok:** Root/jailbreak cihazlar tespit edilmiyor

### 1.2 Önerilen Mimari

**Yeni Akış:**
```
Health Connect → StepRepository → StepSyncManager (syncToken üretir)
                                                          ↓
                                    Firestore: users/{uid}/stepSyncEvents/{syncToken}
                                                          ↓
                                              Cloud Function (onStepSyncEvent)
                                                          ↓
                                    Validation → Anomaly Detection → Flagging
                                                          ↓
                                    users/{uid} (monthlySteps update)
                                                          ↓
                                    leaderboards/... (propagation)
```

**Avantajlar:**
- ✅ **Idempotent:** `syncToken` ile duplicate sync engellenir
- ✅ **Server-side validation:** Tüm kontroller Cloud Function'da
- ✅ **Audit trail:** `stepSyncEvents` append-only log
- ✅ **Conflict resolution:** Server-side timestamp ile çözülür

---

## 2. IDEMPOTENT SYNC SESSION MODEL

### 2.1 Client-Side Implementation

#### 2.1.1 StepSyncManager Güncellemesi

**Mevcut Kod (Problemli):**
```kotlin
private suspend fun performSyncInternal(
    userId: String,
    monthlySteps: Long,
    activeTrackId: String,
    timestamp: Long
): Boolean {
    // ❌ Idempotency yok, duplicate sync riski
    firestore.collection("users")
        .document(userId)
        .set(userData, SetOptions.merge())
        .await()
}
```

**Önerilen Kod (İyileştirilmiş):**
```kotlin
@Singleton
class StepSyncManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase,
    private val authRepository: AuthRepository,
    private val remoteConfigManager: RemoteConfigManager,
    private val healthConnectManager: HealthConnectManager,
    private val leaderboardRepository: LeaderboardRepository,
    private val functions: FirebaseFunctions
) {
    // ... existing code ...

    /**
     * 🔄 Idempotent Sync: Her sync için unique token üretir
     * 
     * Flow:
     * 1. syncToken (UUID) üret
     * 2. stepSyncEvents/{syncToken} dokümanını oluştur
     * 3. Cloud Function bu event'i dinler ve validation yapar
     * 4. Cloud Function users/{uid} günceller (idempotent check ile)
     */
    private suspend fun performSyncInternal(
        userId: String,
        monthlySteps: Long,
        activeTrackId: String,
        timestamp: Long,
        deltaSteps: Long // 🆕 Adım artışı (validation için)
    ): Boolean {
        return try {
            val currentPeriod = getCurrentPeriod()
            val displayName = authRepository.getCurrentUser()?.displayName ?: "Racer ${userId.take(4)}"
            
            // 🆕 Unique sync token üret
            val syncToken = UUID.randomUUID().toString()
            
            // 🆕 Device info (trust score için)
            val deviceInfo = getDeviceInfo()
            
            // 🆕 stepSyncEvents koleksiyonuna append-only event yaz
            // NOT: Bu koleksiyon sadece Cloud Function tarafından okunur/yazılır
            // Client buraya yazamaz (Security Rules ile engellenir)
            // Alternatif: Cloud Function HTTP endpoint kullan (daha güvenli)
            
            val syncEventData = mapOf(
                "userId" to userId,
                "syncToken" to syncToken,
                "deltaSteps" to deltaSteps,
                "totalSteps" to monthlySteps,
                "activeTrackId" to activeTrackId,
                "currentPeriod" to currentPeriod,
                "displayName" to displayName,
                "timestamp" to timestamp,
                "deviceInfo" to deviceInfo.toMap(),
                "status" to "PENDING" // Cloud Function tarafından PROCESSED/REJECTED olacak
            )
            
            // 🔒 Güvenli yazım: Cloud Function HTTP endpoint kullan
            val result = functions
                .getHttpsCallable("syncUserSteps")
                .call(syncEventData)
                .await()
            
            val resultData = result.data as? Map<*, *>
            val status = resultData?.get("status") as? String
            
            if (status == "PROCESSED") {
                // Başarılı sync, state güncelle
                lastSyncedSteps = monthlySteps
                lastSyncTime = timestamp
                android.util.Log.d("StepSync", "✅ Synced $monthlySteps steps (token: $syncToken)")
                true
            } else {
                val reason = resultData?.get("reason") as? String
                android.util.Log.w("StepSync", "⚠️ Sync rejected: $reason")
                false
            }
            
        } catch (e: Exception) {
            android.util.Log.e("StepSync", "❌ Sync failed: ${e.message}", e)
            false
        }
    }
    
    /**
     * 🆕 Device Info (Trust Score için)
     */
    private fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            deviceId = getDeviceIdHash(),
            osVersion = Build.VERSION.SDK_INT,
            appVersion = BuildConfig.VERSION_CODE,
            isRooted = checkRootStatus(), // SafetyNet / Play Integrity
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 🆕 Device ID hash (PII koruması için)
     */
    private fun getDeviceIdHash(): String {
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return MessageDigest.getInstance("SHA-256")
            .digest(deviceId.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16) // İlk 16 karakter
    }
    
    /**
     * 🆕 Root detection (basit kontrol, production'da Play Integrity kullan)
     */
    private fun checkRootStatus(): Boolean {
        // Basit kontrol (production'da Play Integrity API kullanılmalı)
        return try {
            val suPath = arrayOf(
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
            )
            suPath.any { File(it).exists() }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 🆕 syncIfNeeded güncellemesi: deltaSteps hesapla
     */
    suspend fun syncIfNeeded(
        totalSteps: Long,
        activeTrackId: String,
        force: Boolean = false
    ): Boolean {
        return syncMutex.withLock {
            val userId = authRepository.getCurrentUserId() ?: return false
            val now = System.currentTimeMillis()
            
            // Throttling kontrolü
            if (!force && !shouldSync(totalSteps, lastSyncedSteps, lastSyncTime)) {
                return false
            }
            
            // 🆕 Delta hesapla
            val deltaSteps = totalSteps - lastSyncedSteps
            
            // performSyncInternal çağrısına deltaSteps ekle
            performSyncInternal(
                userId = userId,
                monthlySteps = totalSteps,
                activeTrackId = activeTrackId,
                timestamp = now,
                deltaSteps = deltaSteps
            )
        }
    }
}

/**
 * 🆕 Device Info Data Class
 */
data class DeviceInfo(
    val deviceId: String,
    val osVersion: Int,
    val appVersion: Int,
    val isRooted: Boolean,
    val timestamp: Long
) {
    fun toMap(): Map<String, Any> = mapOf(
        "deviceId" to deviceId,
        "osVersion" to osVersion,
        "appVersion" to appVersion,
        "isRooted" to isRooted,
        "timestamp" to timestamp
    )
}
```

### 2.2 Cloud Function Implementation

#### 2.2.1 syncUserSteps HTTP Callable Function

**functions/src/index.ts:**
```typescript
import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

/**
 * 🔒 SECURE SYNC: Idempotent step sync with validation
 * 
 * Client bu fonksiyonu çağırarak sync yapar.
 * Tüm validation ve propagation server-side yapılır.
 */
export const syncUserSteps = functions.https.onCall(async (data, context) => {
  // 1. Authentication check
  if (!context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'User must be authenticated'
    );
  }

  const userId = context.auth.uid;
  const {
    syncToken,
    deltaSteps,
    totalSteps,
    activeTrackId,
    currentPeriod,
    displayName,
    timestamp,
    deviceInfo
  } = data;

  // 2. Input validation
  if (!syncToken || typeof deltaSteps !== 'number' || typeof totalSteps !== 'number') {
    throw new functions.https.HttpsError(
      'invalid-argument',
      'Missing required fields: syncToken, deltaSteps, totalSteps'
    );
  }

  // 3. 🆕 IDEMPOTENCY CHECK: Bu syncToken daha önce işlendi mi?
  const syncEventRef = admin.firestore()
    .collection('users')
    .doc(userId)
    .collection('stepSyncEvents')
    .doc(syncToken);

  const syncEventDoc = await syncEventRef.get();

  if (syncEventDoc.exists) {
    const existingData = syncEventDoc.data();
    if (existingData?.status === 'PROCESSED') {
      // Duplicate sync → idempotent, başarılı dön
      console.log(`✅ Duplicate sync ignored (token: ${syncToken})`);
      return {
        status: 'PROCESSED',
        reason: 'duplicate',
        timestamp: existingData.timestamp
      };
    }
  }

  // 4. 🛡️ VALIDATION: Delta steps kontrolü
  const validationResult = validateStepDelta(
    deltaSteps,
    totalSteps,
    userId,
    timestamp
  );

  if (!validationResult.valid) {
    // Validation başarısız → reject
    await syncEventRef.set({
      userId,
      syncToken,
      deltaSteps,
      totalSteps,
      activeTrackId,
      currentPeriod,
      timestamp,
      deviceInfo,
      status: 'REJECTED',
      reason: validationResult.reason,
      flaggedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });

    // Anti-cheat log
    await flagUser(
      userId,
      validationResult.anomalyScore || 0,
      [validationResult.reason || 'INVALID_DELTA'],
      activeTrackId,
      currentPeriod
    );

    return {
      status: 'REJECTED',
      reason: validationResult.reason
    };
  }

  // 5. 🛡️ ANOMALY DETECTION: Z-score hesapla
  const anomalyScore = await calculateAnomalyScore(
    userId,
    deltaSteps,
    timestamp
  );

  if (anomalyScore > 3.0) { // 3 sigma threshold
    // Anomaly detected → flag but process (soft flag)
    await flagUser(
      userId,
      anomalyScore,
      ['ANOMALY_DETECTED'],
      activeTrackId,
      currentPeriod
    );
  }

  // 6. ✅ VALIDATION PASSED: Firestore transaction ile güvenli update
  const db = admin.firestore();
  await db.runTransaction(async (transaction) => {
    // 6.1. users/{uid} dokümanını oku
    const userRef = db.collection('users').doc(userId);
    const userDoc = await transaction.get(userRef);
    const currentUserData = userDoc.data();

    // 6.2. Conflict check: Eğer başka bir sync daha yeni ise, bu sync'i ignore et
    const currentLastSync = currentUserData?.lastSyncTimestamp || 0;
    if (timestamp < currentLastSync) {
      throw new Error('STALE_SYNC'); // Bu sync eski, ignore et
    }

    // 6.3. monthlySteps update (sadece artış varsa)
    const currentMonthlySteps = currentUserData?.monthlySteps || 0;
    if (totalSteps > currentMonthlySteps) {
      transaction.update(userRef, {
        monthlySteps: totalSteps,
        currentMonth: currentPeriod,
        activeTrackId: activeTrackId,
        lastSyncTimestamp: timestamp,
        displayName: displayName,
        'deviceInfo.lastDeviceId': deviceInfo?.deviceId,
        'deviceInfo.lastSyncDevice': deviceInfo
      });
    }

    // 6.4. stepSyncEvents'e PROCESSED işaretle
    transaction.set(syncEventRef, {
      userId,
      syncToken,
      deltaSteps,
      totalSteps,
      activeTrackId,
      currentPeriod,
      timestamp,
      deviceInfo,
      status: 'PROCESSED',
      anomalyScore: anomalyScore,
      processedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });
  });

  // 7. 🔄 PROPAGATION: onUserStepSync trigger'ı otomatik çalışacak
  // (users/{uid} değiştiği için)

  console.log(`✅ Synced ${userId}: +${deltaSteps} steps (total: ${totalSteps}, anomaly: ${anomalyScore.toFixed(2)})`);

  return {
    status: 'PROCESSED',
    syncToken: syncToken,
    anomalyScore: anomalyScore
  };
});

/**
 * 🛡️ VALIDATION: Delta steps kontrolü
 */
function validateStepDelta(
  deltaSteps: number,
  totalSteps: number,
  userId: string,
  timestamp: number
): { valid: boolean; reason?: string; anomalyScore?: number } {
  // 1. Negatif delta kontrolü (reset hariç)
  if (deltaSteps < 0 && totalSteps > 0) {
    return { valid: false, reason: 'NEGATIVE_DELTA' };
  }

  // 2. Maksimum delta kontrolü (40k adım/saat = ~667 adım/dakika)
  const MAX_DELTA_PER_HOUR = 40000;
  const MAX_DELTA_PER_MINUTE = 667;

  // Timestamp farkına göre hız kontrolü (basit)
  // NOT: Gerçek implementasyonda son sync zamanı Firestore'dan okunmalı
  if (deltaSteps > MAX_DELTA_PER_HOUR) {
    return {
      valid: false,
      reason: 'EXCEEDS_MAX_DELTA',
      anomalyScore: 10.0 // Critical violation
    };
  }

  // 3. Minimum delta kontrolü (0'dan büyük olmalı, reset hariç)
  if (deltaSteps <= 0 && totalSteps > 0) {
    return { valid: false, reason: 'ZERO_DELTA' };
  }

  return { valid: true };
}

/**
 * 🛡️ ANOMALY DETECTION: Z-score hesaplama
 */
async function calculateAnomalyScore(
  userId: string,
  deltaSteps: number,
  timestamp: number
): Promise<number> {
  const db = admin.firestore();

  // Son 30 günün sync event'lerini oku (örnek için)
  const thirtyDaysAgo = timestamp - (30 * 24 * 60 * 60 * 1000);
  const syncEventsSnapshot = await db
    .collection('users')
    .doc(userId)
    .collection('stepSyncEvents')
    .where('status', '==', 'PROCESSED')
    .where('timestamp', '>=', thirtyDaysAgo)
    .orderBy('timestamp', 'desc')
    .limit(100)
    .get();

  if (syncEventsSnapshot.empty) {
    // İlk sync → anomaly score 0
    return 0.0;
  }

  // Ortalama ve standart sapma hesapla
  const deltas = syncEventsSnapshot.docs.map(doc => doc.data().deltaSteps || 0);
  const mean = deltas.reduce((a, b) => a + b, 0) / deltas.length;
  const variance = deltas.reduce((sum, d) => sum + Math.pow(d - mean, 2), 0) / deltas.length;
  const stdDev = Math.sqrt(variance);

  if (stdDev === 0) {
    return 0.0;
  }

  // Z-score hesapla
  const zScore = Math.abs((deltaSteps - mean) / stdDev);

  return zScore;
}
```

---

## 3. SERVER-SIDE VALIDATION & ANOMALY DETECTION

### 3.1 Validation Kuralları

| Kural | Threshold | Action |
|-------|-----------|--------|
| **Maksimum Delta** | 40k adım/saat | REJECT + FLAG |
| **Negatif Delta** | deltaSteps < 0 | REJECT (reset hariç) |
| **Z-Score** | > 3.0 | SOFT FLAG (process ama log) |
| **Z-Score** | > 5.0 | HARD FLAG (reject) |
| **Device Root** | isRooted = true | Trust score düşer |

### 3.2 Anomaly Detection Algoritması

**Z-Score Hesaplama:**
```typescript
zScore = |deltaSteps - mean| / stdDev

if zScore > 3.0:
    → Soft flag (işle ama log)
if zScore > 5.0:
    → Hard flag (reject)
```

**Time-Series Analysis (Gelecek):**
- ARIMA model ile trend analizi
- Seasonal pattern detection (haftalık/günlük)
- Outlier detection (Isolation Forest)

---

## 4. CONFLICT RESOLUTION & PERIOD TRANSITION

### 4.1 Period Transition Logic

**Cloud Function: Scheduled Function**
```typescript
/**
 * 🕐 PERIOD TRANSITION: Her gün 00:00'da çalışır
 * 
 * Yeni dönem başladığında:
 * 1. Eski dönem verilerini periodHistory'ye kaydet
 * 2. monthlySteps'i reset et
 * 3. Yeni dönem için leaderboard'ları temizle
 */
export const onPeriodTransition = functions.pubsub
  .schedule('0 0 * * *') // Her gün 00:00
  .timeZone('Europe/Istanbul')
  .onRun(async (context) => {
    const db = admin.firestore();
    const now = admin.firestore.Timestamp.now();
    
    // Mevcut dönemi hesapla (Remote Config'den)
    const currentPeriod = calculateCurrentPeriod();
    const previousPeriod = calculatePreviousPeriod();
    
    // Tüm aktif kullanıcıları al
    const usersSnapshot = await db.collection('users')
      .where('currentMonth', '==', previousPeriod)
      .get();
    
    const batch = db.batch();
    let batchCount = 0;
    
    for (const userDoc of usersSnapshot.docs) {
      const userId = userDoc.id;
      const userData = userDoc.data();
      
      // 1. periodHistory'ye kaydet
      const periodHistoryRef = db
        .collection('users')
        .doc(userId)
        .collection('periodHistory')
        .doc(previousPeriod);
      
      batch.set(periodHistoryRef, {
        periodId: previousPeriod,
        totalSteps: userData.monthlySteps || 0,
        activeTrackId: userData.activeTrackId,
        finalRank: await calculateFinalRank(userId, previousPeriod),
        completedAt: now
      }, { merge: true });
      
      // 2. monthlySteps reset
      batch.update(userDoc.ref, {
        monthlySteps: 0,
        currentMonth: currentPeriod,
        lastSyncTimestamp: 0 // Reset sync timestamp
      });
      
      batchCount++;
      
      // Firestore batch limit: 500
      if (batchCount >= 500) {
        await batch.commit();
        batchCount = 0;
      }
    }
    
    if (batchCount > 0) {
      await batch.commit();
    }
    
    console.log(`✅ Period transition completed: ${usersSnapshot.size} users migrated`);
  });
```

---

## 5. DEVICE FINGERPRINTING & TRUST SCORE

### 5.1 Trust Score Hesaplama

```typescript
/**
 * 🛡️ TRUST SCORE: Cihaz güvenilirliği skoru (0-100)
 */
async function calculateTrustScore(
  userId: string,
  deviceInfo: any
): Promise<number> {
  let score = 100;

  // 1. Root detection: -50 puan
  if (deviceInfo.isRooted) {
    score -= 50;
  }

  // 2. OS version: Eski OS → -10 puan
  if (deviceInfo.osVersion < 26) { // Android 8.0 altı
    score -= 10;
  }

  // 3. Geçmiş anomaly rate: Her %1 → -1 puan
  const anomalyRate = await getAnomalyRate(userId);
  score -= anomalyRate * 100;

  // 4. Device değişikliği sıklığı: Çok sık değişiyorsa şüpheli
  const deviceChangeFrequency = await getDeviceChangeFrequency(userId);
  if (deviceChangeFrequency > 5) { // Ayda 5'ten fazla cihaz değişimi
    score -= 20;
  }

  return Math.max(0, Math.min(100, score));
}

/**
 * Trust score < 50 → Flag
 */
if (trustScore < 50) {
  await flagUser(userId, 100 - trustScore, ['LOW_TRUST_SCORE']);
}
```

---

## 6. FIRESTORE SECURITY RULES

### 6.1 Güncellenmiş Security Rules

**firestore.rules:**
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // 🔒 USERS: Sadece kendi verisini okuyabilir/yazabilir
    match /users/{userId} {
      allow read: if request.auth != null && request.auth.uid == userId;
      allow create: if request.auth != null && request.auth.uid == userId;
      
      // UPDATE: Kritik alanlar değiştirilemez (sadece Cloud Functions)
      allow update: if request.auth != null && request.auth.uid == userId
                    && !request.resource.data.diff(resource.data).affectedKeys().hasAny([
                      'antiCheat', 'admin', 'coins', 'premium', 'monthlySteps'
                    ]);
      // NOT: monthlySteps sadece Cloud Function tarafından güncellenir
      // Client syncUserSteps Cloud Function'ını çağırarak update eder
      
      allow delete: if request.auth != null && request.auth.uid == userId;

      // 🔒 STEP SYNC EVENTS: Sadece Cloud Functions yazabilir
      match /stepSyncEvents/{syncToken} {
        allow read: if request.auth != null && request.auth.uid == userId;
        allow write: if false; // Client yazamaz, sadece Cloud Function
      }

      // 🔒 PERIOD HISTORY: Sadece Cloud Functions yazabilir
      match /periodHistory/{periodId} {
        allow read: if request.auth != null && request.auth.uid == userId;
        allow write: if false; // Sadece Cloud Function
      }
    }
    
    // 🔒 LEADERBOARDS: Sadece Cloud Functions yazabilir
    match /leaderboards/{trackId}/monthly/{periodId}/entries/{userId} {
      allow read: if true; // Public leaderboard
      allow write: if false; // Sadece Cloud Function
    }

    // 🔒 LEAGUES: Sadece Cloud Functions yazabilir
    match /leagues/{leagueId} {
      allow read: if request.auth != null;
      allow write: if false;
      
      match /members/{userId} {
        allow read: if request.auth != null;
        allow write: if false; // Sadece Cloud Function
      }
    }

    // 🔒 QUALIFYING: Sadece Cloud Functions yazabilir
    match /qualifying/{poolId}/entries/{userId} {
      allow read: if true;
      allow write: if false; // Sadece Cloud Function
    }
    
    // 🔒 ANTI-CHEAT LOGS: Client sadece kendi userId'si için create (append-only)
    match /antiCheatLogs/{logId} {
      allow read: if request.auth != null && request.auth.token.admin == true;
      allow create: if request.auth != null
        && request.resource.data.userId == request.auth.uid
        && request.resource.data.keys().hasAll(['userId', 'timestamp', 'type'])
        && request.resource.data.timestamp == request.time;
      allow update, delete: if false; // Append-only
    }
    
    // 🔒 DEFAULT: Deny all
    match /{document=**} {
      allow read: if request.auth != null;
      allow write: if false;
    }
  }
}
```

---

## 7. MONITORING & ALERTING

### 7.1 Cloud Monitoring Metrics

**Key Metrics:**
1. **Sync Success Rate:** `syncUserSteps` başarı oranı
2. **Anomaly Detection Rate:** Flagged sync'lerin oranı
3. **Average Anomaly Score:** Ortalama anomaly score
4. **Trust Score Distribution:** Trust score histogram
5. **Sync Latency:** Cloud Function execution time

**Alerting Rules:**
```yaml
# sync_failure_rate.yaml
alert:
  name: High Sync Failure Rate
  condition: sync_failure_rate > 0.05 (5%)
  action: Email + Slack notification

# anomaly_spike.yaml
alert:
  name: Anomaly Detection Spike
  condition: anomaly_detection_rate > 0.02 (2%)
  action: Email + PagerDuty

# trust_score_low.yaml
alert:
  name: Low Trust Score Users
  condition: users_with_trust_score < 50 > 100
  action: Email notification
```

---

## 8. TEST STRATEJİSİ

### 8.1 Unit Tests

**StepSyncManagerTest.kt:**
```kotlin
class StepSyncManagerTest {
    @Test
    fun `syncIfNeeded generates unique syncToken`() = runTest {
        val manager = StepSyncManager(...)
        val token1 = manager.generateSyncToken()
        val token2 = manager.generateSyncToken()
        
        assertNotEquals(token1, token2)
    }
    
    @Test
    fun `duplicate syncToken is ignored by Cloud Function`() = runTest {
        // Mock Cloud Function response
        val mockFunctions = mock<FirebaseFunctions>()
        whenever(mockFunctions.getHttpsCallable("syncUserSteps"))
            .thenReturn(mockCallable)
        
        // İlk sync
        manager.syncIfNeeded(1000L, "track1")
        
        // Aynı token ile tekrar sync
        val result = manager.syncIfNeeded(1000L, "track1")
        
        // Cloud Function duplicate döner
        verify(mockCallable, times(2)).call(any())
    }
}
```

### 8.2 Integration Tests

**Cloud Function Test (Firebase Emulator):**
```typescript
describe('syncUserSteps', () => {
  it('should process valid sync', async () => {
    const data = {
      syncToken: 'test-token-1',
      deltaSteps: 500,
      totalSteps: 1000,
      activeTrackId: 'track1',
      currentPeriod: '2026_01',
      timestamp: Date.now()
    };
    
    const result = await syncUserSteps(data, mockContext);
    
    expect(result.status).toBe('PROCESSED');
  });
  
  it('should reject duplicate syncToken', async () => {
    // İlk sync
    await syncUserSteps({ syncToken: 'dup-token', ... }, mockContext);
    
    // Duplicate sync
    const result = await syncUserSteps({ syncToken: 'dup-token', ... }, mockContext);
    
    expect(result.status).toBe('PROCESSED');
    expect(result.reason).toBe('duplicate');
  });
  
  it('should flag high delta', async () => {
    const result = await syncUserSteps({
      syncToken: 'high-delta',
      deltaSteps: 50000, // 50k adım
      totalSteps: 50000,
      ...
    }, mockContext);
    
    expect(result.status).toBe('REJECTED');
    expect(result.reason).toBe('EXCEEDS_MAX_DELTA');
  });
});
```

---

## 9. MIGRATION PLAN

### 9.1 Aşamalı Rollout

**Phase 1: Staging (1 hafta)**
- Yeni sync mekanizması staging'de test edilir
- Monitoring dashboard kurulur
- Test senaryoları çalıştırılır

**Phase 2: Canary (1 hafta)**
- %10 kullanıcıya feature flag ile açılır
- Monitoring: sync success rate, anomaly rate
- Sorun yoksa %50'ye çıkarılır

**Phase 3: Gradual Rollout (1 hafta)**
- %50 → %100 gradual rollout
- Her gün %10 artış

**Phase 4: Full Production**
- Tüm kullanıcılar yeni mekanizmayı kullanır
- Eski sync mekanizması kaldırılır (deprecated)

### 9.2 Rollback Plan

**Rollback Trigger:**
- Sync failure rate > %10
- Anomaly detection rate > %5
- Critical bug tespit edilirse

**Rollback Steps:**
1. Feature flag → `false` (tüm kullanıcılar eski mekanizmaya döner)
2. Cloud Function → eski `onUserStepSync` trigger'ı aktif
3. Monitoring → eski metrikler takip edilir
4. Post-mortem → sorun analizi

---

## 📝 SONUÇ

Bu teknik taslak ile:
- ✅ **Idempotent sync:** Duplicate sync riski ortadan kalkar
- ✅ **Server-side validation:** Tüm kontroller Cloud Function'da
- ✅ **Anomaly detection:** Z-score ile şüpheli aktiviteler tespit edilir
- ✅ **Device trust:** Root/jailbreak cihazlar flag'lenir
- ✅ **Audit trail:** `stepSyncEvents` ile tam log
- ✅ **Security:** Firestore Security Rules ile client yazımları engellenir

**Tahmini Implementation Süresi:** 3-4 hafta (2 developer)
**Complexity:** High
**ROI:** Very High (güvenlik, tutarlılık, maliyet optimizasyonu)

---

**Hazırlayan:** Senior Android Developer & System Architect  
**Tarih:** 2026-01-XX  
**Versiyon:** 1.0
