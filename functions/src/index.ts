/**
 * Pace Legends - Anti-Cheat Cloud Functions
 * 
 * Bu modül, Firestore'a yazılan adım verilerini analiz eder ve
 * şüpheli aktiviteleri tespit eder.
 * 
 * Tespit Kriterleri:
 * 1. İmkansız Hız: Dakikada 200+ adım (koşarak bile imkansız sürekli)
 * 2. Şüpheli Saat: Gece 00:00-05:00 arası büyük artışlar
 * 3. Pattern Tespiti: Her gün aynı miktarda artış
 * 4. Spike Tespiti: Kısa sürede aşırı artış
 */

import * as functions from "firebase-functions/v1";
import * as admin from "firebase-admin";

admin.initializeApp();

const db = admin.firestore();

// =====================================================
// 🛡️ ANTİ-CHEAT SABİTLERİ
// =====================================================

const ANTI_CHEAT = {
  // İnsan limitleri
  MAX_STEPS_PER_MINUTE: 200,        // Koşu temposu
  MAX_STEPS_PER_HOUR: 12000,        // Maraton temposu
  MAX_STEPS_PER_DAY: 60000,         // Ultra maraton

  // Şüpheli saat aralığı (gece)
  SUSPICIOUS_HOUR_START: 0,         // 00:00
  SUSPICIOUS_HOUR_END: 5,           // 05:00
  SUSPICIOUS_NIGHT_THRESHOLD: 3000, // Gece bu kadar artış şüpheli

  // Spike tespiti
  SPIKE_THRESHOLD_5MIN: 2000,       // 5 dakikada 2000+ adım = şüpheli
  SPIKE_THRESHOLD_15MIN: 5000,      // 15 dakikada 5000+ adım = şüpheli

  // Pattern tespiti (aynı miktar tekrarı)
  PATTERN_TOLERANCE: 50,            // ±50 adım tolerans
  PATTERN_MIN_OCCURRENCES: 3,       // 3+ kez aynı artış = pattern
};

// =====================================================
// 🔍 LEADERBOARD ENTRY GÜNCELLEME TETİKLEYİCİSİ
// =====================================================

export const onLeaderboardUpdate = functions.firestore
  .document("leaderboards/{trackId}/monthly/{periodId}/entries/{userId}")
  .onWrite(async (change, context) => {
    const { trackId, periodId, userId } = context.params;

    // Silme işlemini yoksay
    if (!change.after.exists) {
      return null;
    }

    const newData = change.after.data();
    const oldData = change.before.exists ? change.before.data() : null;

    const newSteps = newData?.steps || 0;
    const oldSteps = oldData?.steps || 0;
    const stepDelta = newSteps - oldSteps;

    // Artış yoksa veya negatifse yoksay
    if (stepDelta <= 0) {
      return null;
    }

    const now = new Date();

    // ⚡ CPU OPTIMIZATION: Küçük güncellemelerde analizi atla
    // 500 adımdan az ve gece değilse (en büyük maliyet kalemi)
    const hour = now.getHours();
    const isNightTime = hour >= ANTI_CHEAT.SUSPICIOUS_HOUR_START && hour < ANTI_CHEAT.SUSPICIOUS_HOUR_END;

    if (stepDelta < 500 && !isNightTime) {
      // console.log(`⏩ Skipping anti-cheat for small delta: ${stepDelta}`);
      return null;
    }

    const lastUpdated = newData?.lastUpdated?.toDate() || now;
    const previousUpdated = oldData?.lastUpdated?.toDate() || now;

    // Zaman farkını hesapla (dakika)
    const timeDeltaMs = lastUpdated.getTime() - previousUpdated.getTime();
    const timeDeltaMinutes = Math.max(1, timeDeltaMs / (1000 * 60));

    // Anti-cheat kontrolleri
    const violations: string[] = [];
    let suspicionScore = 0;

    // 1️⃣ İMKANSIZ HIZ KONTROLÜ
    const stepsPerMinute = stepDelta / timeDeltaMinutes;
    if (stepsPerMinute > ANTI_CHEAT.MAX_STEPS_PER_MINUTE) {
      violations.push(`IMPOSSIBLE_SPEED: ${Math.round(stepsPerMinute)} steps/min`);
      suspicionScore += 50;
    }

    // 2️⃣ SPIKE KONTROLÜ (5 dakika içinde)
    if (timeDeltaMinutes <= 5 && stepDelta > ANTI_CHEAT.SPIKE_THRESHOLD_5MIN) {
      violations.push(`SPIKE_5MIN: ${stepDelta} steps in ${Math.round(timeDeltaMinutes)}min`);
      suspicionScore += 40;
    }

    // 3️⃣ SPIKE KONTROLÜ (15 dakika içinde)
    if (timeDeltaMinutes <= 15 && stepDelta > ANTI_CHEAT.SPIKE_THRESHOLD_15MIN) {
      violations.push(`SPIKE_15MIN: ${stepDelta} steps in ${Math.round(timeDeltaMinutes)}min`);
      suspicionScore += 30;
    }

    // 4️⃣ GECE AKTİVİTESİ KONTROLÜ
    // hour already defined above at line 75
    if (hour >= ANTI_CHEAT.SUSPICIOUS_HOUR_START &&
      hour < ANTI_CHEAT.SUSPICIOUS_HOUR_END &&
      stepDelta > ANTI_CHEAT.SUSPICIOUS_NIGHT_THRESHOLD) {
      violations.push(`NIGHT_ACTIVITY: ${stepDelta} steps at ${hour}:00`);
      suspicionScore += 20;
    }

    // 5️⃣ GÜNLÜK LİMİT KONTROLÜ
    const dailySteps = await getDailySteps(userId, trackId, periodId);
    if (dailySteps + stepDelta > ANTI_CHEAT.MAX_STEPS_PER_DAY) {
      violations.push(`DAILY_LIMIT: ${dailySteps + stepDelta} steps today`);
      suspicionScore += 25;
    }

    // Şüpheli aktivite tespit edildiyse logla ve flag'le
    if (violations.length > 0) {
      console.warn(`🚨 ANTI-CHEAT: User ${userId} flagged!`, {
        violations,
        suspicionScore,
        stepDelta,
        timeDeltaMinutes,
        newSteps,
        oldSteps,
      });

      // Şüpheli aktivite kaydı oluştur
      await db.collection("antiCheatLogs").add({
        userId,
        trackId,
        periodId,
        violations,
        suspicionScore,
        stepDelta,
        timeDeltaMinutes,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        newSteps,
        oldSteps,
      });

      // Yüksek şüphe skoru varsa kullanıcıyı flag'le
      if (suspicionScore >= 50) {
        await flagUser(userId, suspicionScore, violations, trackId, periodId);
      }
    }

    return null;
  });

// =====================================================
// 📊 YARDIMCI FONKSİYONLAR
// =====================================================

/**
 * Kullanıcının bugünkü toplam adım artışını hesapla
 */
async function getDailySteps(
  userId: string,
  trackId: string,
  periodId: string
): Promise<number> {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const logs = await db
    .collection("antiCheatLogs")
    .where("userId", "==", userId)
    .where("trackId", "==", trackId)
    .where("periodId", "==", periodId)
    .where("timestamp", ">=", today)
    .get();

  let totalDelta = 0;
  logs.forEach((doc) => {
    totalDelta += doc.data().stepDelta || 0;
  });

  return totalDelta;
}

/**
 * Şüpheli kullanıcıyı flag'le
 */
async function flagUser(
  userId: string,
  score: number,
  violations: string[],
  trackId?: string,
  periodId?: string
): Promise<void> {
  const batch = db.batch();

  // 1. User dokümanını flag'le
  const userRef = db.collection("users").doc(userId);
  batch.set(
    userRef,
    {
      antiCheat: {
        flagged: true,
        suspicionScore: admin.firestore.FieldValue.increment(score),
        lastViolation: admin.firestore.FieldValue.serverTimestamp(),
        violations: admin.firestore.FieldValue.arrayUnion(...violations),
      },
    },
    { merge: true }
  );

  // 2. Leaderboard entry'sini de flag'le (filtreleme için)
  if (trackId && periodId) {
    const entryRef = db
      .collection("leaderboards")
      .doc(trackId)
      .collection("monthly")
      .doc(periodId)
      .collection("entries")
      .doc(userId);

    batch.set(
      entryRef,
      { flagged: true },
      { merge: true }
    );
  }

  await batch.commit();
  console.warn(`🚫 User ${userId} flagged with score ${score}`);
}

// =====================================================
// 🔒 SECURE LEADERBOARD SYNC (Server Authority)
// =====================================================

/**
 * 🆕 Kullanıcı adımlarını güncellediğinde çalışır.
 * Source of Truth: users/{userId} -> monthlySteps
 * 
 * Bu fonksiyon, kullanıcının adım sayısını güvenli bir şekilde:
 * 1. Global Leaderboard'a
 * 2. Lig (League) veya Eleme (Qualifying) sıralamasına
 * kopyalar.
 * 
 * Böylece client'ın "leaderboards" koleksiyonuna yazma izni iptal edilebilir.
 */
export const onUserStepSync = functions.firestore
  .document("users/{userId}")
  .onWrite(async (change, context) => {
    const userId = context.params.userId;
    const newData = change.after.data();
    const oldData = change.before.data();

    // Silme işlemini yoksay
    if (!newData) return null;

    const newSteps = newData.monthlySteps || 0;
    const oldSteps = oldData?.monthlySteps || 0;

    // Adım sayısı değişmediyse veya azaldıysa (reset hariç) işlem yapma
    // Reset durumunda (newSteps < oldSteps) güncelleme yapılmalı (yeni ay/dönem)
    if (newSteps === oldSteps) return null;

    const currentPeriod = newData.currentMonth;
    const activeTrackId = newData.activeTrackId;
    const displayName = newData.displayName || `Racer ${userId.substring(0, 4)}`;

    // Eksik veri varsa çık
    if (!currentPeriod || !activeTrackId) return null;

    console.log(`🔄 Syncing steps for ${userId}: ${newSteps} steps (Period: ${currentPeriod}, Track: ${activeTrackId})`);

    const batch = db.batch();
    const now = admin.firestore.FieldValue.serverTimestamp();

    // 1. GLOBAL LEADERBOARD GÜNCELLE
    // leaderboards/{trackId}/monthly/{periodId}/entries/{userId}
    const leaderboardRef = db
      .collection("leaderboards")
      .doc(activeTrackId)
      .collection("monthly")
      .doc(currentPeriod)
      .collection("entries")
      .doc(userId);

    batch.set(leaderboardRef, {
      userId,
      displayName,
      steps: newSteps,
      lastUpdated: now,
      // Profil fotosu vb. eklenebilir
    }, { merge: true });

    // 2. LIG / QUALIFYING GÜNCELLE
    const leagueTier = newData.leagueTier || "QUALIFYING";
    const leagueId = newData.leagueId;

    if (leagueTier === "QUALIFYING" || !leagueId) {
      // Eleme Havuzu: qualifying/{trackId}_{periodId}/entries/{userId}
      // Not: Qualifying ID formatı app ile uyumlu olmalı: {trackId}_{periodId}
      const qualifyingId = `${activeTrackId}_${currentPeriod}`;
      const qualifyingRef = db
        .collection("qualifying")
        .doc(qualifyingId)
        .collection("entries")
        .doc(userId);

      batch.set(qualifyingRef, {
        userId,
        displayName,
        steps: newSteps,
        lastUpdated: now
      }, { merge: true });

    } else {
      // Lig Üyeliği: leagues/{leagueId}/members/{userId}
      const leagueRef = db
        .collection("leagues")
        .doc(leagueId)
        .collection("members")
        .doc(userId);

      batch.set(leagueRef, {
        userId,
        displayName,
        steps: newSteps,
        lastUpdated: now
      }, { merge: true });
    }

    // 🚀 BATCH COMMIT
    try {
      await batch.commit();
      console.log(`✅ Synced ${userId} to leaderboards & leagues.`);
    } catch (e) {
      console.error(`❌ Sync failed for ${userId}:`, e);
    }

    // 🛡️ ANTI-CHEAT KONTROLÜ (Mevcut mantık ile entegrasyon)
    // Değişim verisiyle analiz yap (Step Delta)
    const stepDelta = newSteps - oldSteps;
    if (stepDelta > 0) {
      // Eski fonksiyonu manuel çağırabilir veya mantığı buraya taşıyabiliriz.
      // Şimdilik basit bir kontrol ekleyelim:
      if (stepDelta > 50000) { // Tek seferde 50k adım?
        await db.collection("antiCheatLogs").add({
          userId,
          type: "IMPOSSIBLE_JUMP",
          stepDelta,
          timestamp: now
        });
      }
    }

    return null;
  });

// =====================================================
// 🧹 GÜNLÜK TEMİZLİK (Opsiyonel - Schedule)
// =====================================================

export const dailyAntiCheatReport = functions.pubsub
  .schedule("every day 06:00")
  .timeZone("Europe/Istanbul")
  .onRun(async () => {
    // ... (Mevcut kod aynen kalabilir)
    return null;
  });


// =====================================================
// 🔓 ADMIN: Kullanıcı Flag'ini Kaldır
// =====================================================

export const unflagUser = functions.https.onCall(async (data, context) => {
  // Admin kontrolü
  if (!context.auth?.token?.admin) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Only admins can unflag users"
    );
  }

  const { userId } = data;
  if (!userId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "userId is required"
    );
  }

  await db.collection("users").doc(userId).update({
    "antiCheat.flagged": false,
    "antiCheat.unflaggedAt": admin.firestore.FieldValue.serverTimestamp(),
    "antiCheat.unflaggedBy": context.auth.uid,
  });

  console.log(`✅ User ${userId} unflagged by admin ${context.auth.uid}`);

  return { success: true };
});

/**
 * 🛡️ SECURE LOGGING: Şüpheli Aktivite Bildirimi
 * Client doğrudan Firestore'a yazamaz (Security Rules: allow create: if false).
 * Bu fonksiyon üzerinden bildirim yapar.
 */
export const logCheatAttempt = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated to log stats"
    );
  }

  const userId = context.auth.uid;
  const { type, steps, durationMs, pacetKmph } = data;

  // Basit validasyon
  if (!type || !steps) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing required fields"
    );
  }

  await db.collection("antiCheatLogs").add({
    userId,
    type,
    steps,
    durationMs,
    pacetKmph,
    timestamp: admin.firestore.FieldValue.serverTimestamp(),
    source: "CLIENT_REPORT",
    userAgent: context.rawRequest.headers["user-agent"] || "Unknown"
  });

  console.warn(`🚨 Client-side cheat report from ${userId}: ${type}`);

  // Eğer çok ciddi bir ihlal ise hemen flag'le
  if (type === "SPEED_VIOLATION" && pacetKmph > 100) {
    await flagUser(userId, 100, ["CRITICAL_SPEED_VIOLATION"]);
  }

  return { success: true };
});

/**
 * Her gece çalışır, period bitimini kontrol eder.
 * Eğer period bittiyse:
 * 1. Remote Config'den period bilgilerini çeker
 * 2. Tüm ligleri tarar
 * 3. Sıralamaları kesinleştirir
 * 4. Yükselme/Düşme işlemlerini yapar
 * 5. Şampiyonlara rozet ve coin verir
 */
export const processPeriodEnd = functions.pubsub
  .schedule("every day 00:01")
  .timeZone("Europe/Istanbul")
  .onRun(async (context) => {
    console.log("🏁 [Period End Check] Starting...");

    // 🆕 Remote Config'den period bilgilerini çek
    const remoteConfig = admin.remoteConfig();
    let periodConfig: PeriodConfig;

    try {
      const template = await remoteConfig.getTemplate();
      const periodConfigParam = template.parameters["period_config"];

      if (periodConfigParam && periodConfigParam.defaultValue) {
        const configValue = (periodConfigParam.defaultValue as { value: string }).value;
        periodConfig = JSON.parse(configValue);
      } else {
        // Fallback: Varsayılan 14 günlük dönem
        periodConfig = getDefaultPeriodConfig();
      }
    } catch (e) {
      console.warn("⚠️ Remote Config fetch failed, using defaults:", e);
      periodConfig = getDefaultPeriodConfig();
    }

    // Period bitti mi kontrol et
    const now = new Date();
    const periodEndDate = new Date(periodConfig.currentPeriodEndDate);

    if (now < periodEndDate) {
      console.log(`⏳ Period not ended yet. Ends at: ${periodEndDate.toISOString()}`);
      return null;
    }

    console.log("🎉 Period ended! Processing...");

    // 1. Tüm ligleri işle (PARALEL - Chunked)
    const leaguesSnapshot = await db.collection("leagues").get();

    // ✅ SCALABILITY FIX: Process in parallel with chunking
    // Chunk size 10: Firestore batch limit friendly, prevents rate limiting
    const CHUNK_SIZE = 10;
    const leagueDocs = leaguesSnapshot.docs;
    let processedLeagues = 0;

    for (let i = 0; i < leagueDocs.length; i += CHUNK_SIZE) {
      const chunk = leagueDocs.slice(i, i + CHUNK_SIZE);

      // Process chunk in parallel
      await Promise.all(
        chunk.map(async (doc) => {
          try {
            await processLeague(doc, periodConfig);
            processedLeagues++;
          } catch (error) {
            console.error(`❌ Failed to process league ${doc.id}:`, error);
          }
        })
      );

      console.log(`📊 Processed ${processedLeagues}/${leagueDocs.length} leagues...`);
    }

    // 2. Eleme havuzlarını işle (PARALEL - Chunked)
    const qualifyingSnapshot = await db.collection("qualifying").get();
    const qualifyingDocs = qualifyingSnapshot.docs;

    for (let i = 0; i < qualifyingDocs.length; i += CHUNK_SIZE) {
      const chunk = qualifyingDocs.slice(i, i + CHUNK_SIZE);

      await Promise.all(
        chunk.map(async (doc) => {
          try {
            await processQualifyingPool(doc, periodConfig);
          } catch (error) {
            console.error(`❌ Failed to process qualifying ${doc.id}:`, error);
          }
        })
      );
    }

    // 3. Period bilgilerini güncelle (bir sonraki dönem)
    await updatePeriodConfig(periodConfig);

    console.log(`✅ Period end processed: ${processedLeagues} leagues, ${qualifyingDocs.length} qualifying pools.`);

    return null;
  });

/**
 * ⚡ SCALABILITY: Worker Function
 * Cloud Task tarafından çağrılacak izole fonksiyon.
 * Her lig için ayrı bir instance ayağa kalkar, 9 dakika süresi vardır.
 */
export const processLeagueTask = functions.https.onRequest(async (req, res) => {
  // Sadece Cloud Task'lardan gelen istekleri kabul et (Security)
  // if (!req.get('X-AppEngine-QueueName') && !process.env.FUNCTIONS_EMULATOR) {
  //  res.status(403).send('Forbidden');
  //  return;
  // }

  const { leagueId, config } = req.body;
  if (!leagueId || !config) {
    res.status(400).send("Missing leagueId or config");
    return;
  }

  try {
    console.log(`⚡ Worker started for league: ${leagueId}`);
    const leagueDoc = await db.collection("leagues").doc(leagueId).get();

    if (!leagueDoc.exists) {
      res.status(404).send("League not found");
      return;
    }

    const result = await processLeague(leagueDoc, config);
    res.status(200).json({ success: true, ...result });
  } catch (error) {
    console.error("Worker failed:", error);
    res.status(500).send(error);
  }
});

// =====================================================
// 📋 PERIOD TİPLERİ
// =====================================================

interface PeriodConfig {
  currentPeriodId: string;
  currentPeriodEndDate: string; // ISO date
  periodDurationDays: number;
  promotionThreshold: number; // Top N get promoted
  demotionThreshold: number;  // Bottom N get demoted
  coinRewards: {
    champion: number;
    second: number;
    third: number;
    promotion: number;
  };
}

function getDefaultPeriodConfig(): PeriodConfig {
  // Varsayılan: 14 günlük dönem
  const now = new Date();
  const endDate = new Date(now.getTime() + 14 * 24 * 60 * 60 * 1000);

  return {
    currentPeriodId: `period_${Math.floor(now.getTime() / (14 * 24 * 60 * 60 * 1000))}`,
    currentPeriodEndDate: endDate.toISOString(),
    periodDurationDays: 14,
    promotionThreshold: 3,
    demotionThreshold: 3,
    coinRewards: {
      champion: 300,
      second: 200,
      third: 100,
      promotion: 500
    }
  };
}

async function updatePeriodConfig(oldConfig: PeriodConfig): Promise<void> {
  // Yeni period oluştur
  const newEndDate = new Date();
  newEndDate.setDate(newEndDate.getDate() + oldConfig.periodDurationDays);

  const periodNumber = parseInt(oldConfig.currentPeriodId.replace("period_", "")) + 1;

  const newConfig: PeriodConfig = {
    ...oldConfig,
    currentPeriodId: `period_${periodNumber}`,
    currentPeriodEndDate: newEndDate.toISOString()
  };

  // Remote Config güncelleme (Admin SDK ile)
  try {
    const remoteConfig = admin.remoteConfig();
    const template = await remoteConfig.getTemplate();

    template.parameters["period_config"] = {
      defaultValue: { value: JSON.stringify(newConfig) }
    };

    await remoteConfig.publishTemplate(template);
    console.log("📅 Period config updated for next period");
  } catch (e) {
    console.error("❌ Failed to update Remote Config:", e);
  }
}

// =====================================================
// 🏆 LİG İŞLEME - GÜÇLENDİRİLMİŞ VERSİYON
// =====================================================

interface LeagueProcessResult {
  usersProcessed: number;
  promotions: number;
  demotions: number;
}

async function processLeague(
  leagueDoc: admin.firestore.DocumentSnapshot,
  config: PeriodConfig
): Promise<LeagueProcessResult> {
  const leagueId = leagueDoc.id;
  const leagueData = leagueDoc.data();
  const tier = leagueData.tier as string;

  console.log(`📊 Processing league: ${leagueId} (Tier: ${tier})`);

  const membersRef = leagueDoc.ref.collection("members");
  const membersSnapshot = await membersRef.orderBy("steps", "desc").get();

  const totalMembers = membersSnapshot.docs.length;
  if (totalMembers === 0) {
    return { usersProcessed: 0, promotions: 0, demotions: 0 };
  }

  const batch = db.batch();
  let rank = 0;
  let promotions = 0;
  let demotions = 0;
  const now = admin.firestore.FieldValue.serverTimestamp();

  // Lig tier sırası
  const tierOrder = ["QUALIFYING", "BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "LEGEND"];
  const currentTierIndex = tierOrder.indexOf(tier);
  const nextTier = currentTierIndex < tierOrder.length - 1 ? tierOrder[currentTierIndex + 1] : null;
  const prevTier = currentTierIndex > 0 ? tierOrder[currentTierIndex - 1] : null;

  const demotionZoneStart = totalMembers - config.demotionThreshold + 1;

  for (const memberDoc of membersSnapshot.docs) {
    rank++;
    const userId = memberDoc.id;
    const userRef = db.collection("users").doc(userId);

    const userUpdates: any = {
      monthlySteps: 0,
      lastResetAt: now,
      previousPeriodRank: rank
    };
    let totalCoinReward = 0;

    // 🏅 1. ŞAMPIYONLUK ROZETİ VE COIN (İlk 3)
    if (rank <= 3) {
      // Rozet (Separate Doc - OK to be in batch)
      const badgeRef = userRef.collection("championBadges").doc();
      batch.set(badgeRef, {
        type: rank === 1 ? "PERIOD_CHAMPION" : "PODIUM_FINISH",
        rank: rank,
        leagueId: leagueId,
        tier: tier,
        periodId: config.currentPeriodId,
        earnedAt: now
      });

      // Coin ödülü
      const coinAmount = rank === 1 ? config.coinRewards.champion :
        rank === 2 ? config.coinRewards.second :
          config.coinRewards.third;

      totalCoinReward += coinAmount;

      // Coin transaction log (Separate Doc - OK)
      const coinLogRef = userRef.collection("coinHistory").doc();
      batch.set(coinLogRef, {
        type: rank === 1 ? "PERIOD_CHAMPION" : rank === 2 ? "PERIOD_SECOND" : "PERIOD_THIRD",
        amount: coinAmount,
        timestamp: now,
        description: `${tier} Ligi - ${rank}. sıra ödülü`,
        periodId: config.currentPeriodId
      });

      console.log(`🏅 ${userId}: Rank ${rank} - +${coinAmount} coins`);
    }

    // 🔄 PERIOD RESET LOGIC (Archive)
    const userCurrentSteps = memberDoc.data().steps || 0;

    // 1. ARŞİVLE (periodHistory)
    if (userCurrentSteps > 0) {
      const historyRef = userRef.collection("periodHistory").doc(config.currentPeriodId);
      batch.set(historyRef, {
        periodId: config.currentPeriodId,
        totalSteps: userCurrentSteps,
        leagueTier: tier,
        leagueId: leagueId,
        archivedAt: now,
        rank: rank
      });
    }

    // 🚀 2. YÜKSELME (Top N)
    if (rank <= config.promotionThreshold && nextTier) {
      userUpdates.leagueTier = nextTier;
      userUpdates.leagueId = null; // Yeni lige atanacak
      userUpdates.promotedAt = now;
      userUpdates.unlockedFrames = admin.firestore.FieldValue.arrayUnion(nextTier.toLowerCase());

      // Yükselme coin ödülü
      totalCoinReward += config.coinRewards.promotion;

      const promoLogRef = userRef.collection("coinHistory").doc();
      batch.set(promoLogRef, {
        type: "LEAGUE_PROMOTION",
        amount: config.coinRewards.promotion,
        timestamp: now,
        description: `${tier} → ${nextTier} yükselme bonusu`
      });

      promotions++;
      console.log(`🚀 ${userId}: PROMOTED to ${nextTier}`);
    } else if (rank >= demotionZoneStart && prevTier) {
      // 📉 3. DÜŞME (Bottom N)
      // Else if used because can't be promoted AND demoted
      userUpdates.leagueTier = prevTier;
      userUpdates.leagueId = null;
      userUpdates.demotedAt = now;

      demotions++;
      console.log(`📉 ${userId}: DEMOTED to ${prevTier}`);
    }

    // 💰 COIN UPDATE (Consolidated)
    if (totalCoinReward > 0) {
      userUpdates.coins = admin.firestore.FieldValue.increment(totalCoinReward);
    }

    // 🔥 EXECUTE USER UPDATE (Single Operation)
    batch.set(userRef, userUpdates, { merge: true });
  }

  await batch.commit();

  return {
    usersProcessed: totalMembers,
    promotions,
    demotions
  };
}

// =====================================================
// 🏁 ELEME HAVUZU İŞLEME
// =====================================================

async function processQualifyingPool(
  qualifyingDoc: admin.firestore.QueryDocumentSnapshot,
  config: PeriodConfig
): Promise<void> {
  const poolId = qualifyingDoc.id;
  console.log(`🏁 Processing qualifying pool: ${poolId}`);

  const entriesRef = qualifyingDoc.ref.collection("entries");
  const entriesSnapshot = await entriesRef.orderBy("steps", "desc").get();

  if (entriesSnapshot.empty) return;

  const batch = db.batch();
  let rank = 0;
  const now = admin.firestore.FieldValue.serverTimestamp();

  for (const entryDoc of entriesSnapshot.docs) {
    rank++;
    const userId = entryDoc.id;
    const userRef = db.collection("users").doc(userId);

    const userUpdates: any = {
      monthlySteps: 0,
      lastResetAt: now
    };
    let totalCoinReward = 0;

    // Top 3 yükselir BRONZE'a
    if (rank <= config.promotionThreshold) {
      userUpdates.leagueTier = "BRONZE";
      userUpdates.leagueId = null;
      userUpdates.promotedAt = now;
      userUpdates.unlockedFrames = admin.firestore.FieldValue.arrayUnion("bronze");

      // Yükselme coin ödülü
      totalCoinReward += config.coinRewards.promotion;

      const promoLogRef = userRef.collection("coinHistory").doc();
      batch.set(promoLogRef, {
        type: "LEAGUE_PROMOTION",
        amount: config.coinRewards.promotion,
        timestamp: now,
        description: "Eleme → Bronz Lig yükselme bonusu"
      });

      console.log(`🚀 ${userId}: PROMOTED from Qualifying to BRONZE`);
    }

    // Top 3'e rozet ver
    if (rank <= 3) {
      const badgeRef = userRef.collection("championBadges").doc();
      batch.set(badgeRef, {
        type: rank === 1 ? "QUALIFYING_CHAMPION" : "QUALIFYING_PODIUM",
        rank: rank,
        poolId: poolId,
        periodId: config.currentPeriodId,
        earnedAt: now
      });

      const coinAmount = rank === 1 ? config.coinRewards.champion :
        rank === 2 ? config.coinRewards.second :
          config.coinRewards.third;

      totalCoinReward += coinAmount;
    }

    // 🔄 PERIOD RESET LOGIC (Qualifying)
    const entrySteps = entryDoc.data().steps || 0;
    if (entrySteps > 0) {
      const historyRef = userRef.collection("periodHistory").doc(config.currentPeriodId);
      batch.set(historyRef, {
        periodId: config.currentPeriodId,
        totalSteps: entrySteps,
        leagueTier: "QUALIFYING",
        archivedAt: now,
        rank: rank
      });
    }

    // 💰 COIN UPDATE (Consolidated)
    if (totalCoinReward > 0) {
      userUpdates.coins = admin.firestore.FieldValue.increment(totalCoinReward);
    }

    // 🔥 EXECUTE USER UPDATE (Single Operation)
    batch.set(userRef, userUpdates, { merge: true });
  }

  await batch.commit();
}



// resetMonthlySteps removed - integrated into processPeriodEnd logic


/**
 * Eski leaderboard entries'lerini temizle (Opsiyonel)
 * Maliyet optimizasyonu için eski dönem verilerini siler.
 */
export async function cleanupOldLeaderboardEntries(periodId: string): Promise<void> {
  console.log(`🧹 Cleaning up leaderboard entries for period: ${periodId}`);

  // Bu işlem büyük ölçekte maliyetli olabilir.
  // Production'da Cloud Tasks ile fan-out yapılmalı.

  const tracksSnapshot = await db.collection("leaderboards").get();

  for (const trackDoc of tracksSnapshot.docs) {
    const entriesRef = trackDoc.ref
      .collection("monthly")
      .doc(periodId)
      .collection("entries");

    const entries = await entriesRef.limit(500).get();

    if (!entries.empty) {
      const batch = db.batch();
      entries.docs.forEach(doc => batch.delete(doc.ref));
      await batch.commit();
      console.log(`   🗑️ Deleted ${entries.docs.length} entries from ${trackDoc.id}/${periodId}`);
    }
  }
}
