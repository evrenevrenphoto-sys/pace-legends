package com.pace.legends.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pace.legends.domain.model.UserProgress
import com.pace.legends.domain.repository.LeaderboardRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Leaderboard Repository
 * 
 * Sıralama Mantığı:
 * 1. Birincil: Toplam Tur Sayısı (completedLoops) - Büyükten küçüğe
 * 2. İkincil: Ortalama Tur Süresi (bestLapTimeSeconds) - Küçükten büyüğe
 * 
 * Bu mantık "yürüyüş teşvik" uygulaması için idealdir:
 * - Daha fazla yürüyen (tur atan) ödüllendirilir
 * - Eşit turda daha hızlı olan öne geçer
 */
@Singleton
class FirebaseLeaderboardRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val db: com.pace.legends.data.local.AppDatabase // 🆕 Cache Access
) : LeaderboardRepository {

    // 🆕 P2 FIX: Error state UI için (Error Swallowing önleme)
    private var _lastError: String? = null
    val lastError: String? get() = _lastError

    override suspend fun getLeaderboard(trackId: String): List<UserProgress> {
        _lastError = null
        return try {
            val result = firestore.collection("leaderboards")
                .document(trackId)
                .collection("scores")
                .orderBy("totalSteps", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()
            
            val rawList = result.toObjects(UserProgress::class.java)
            
            rawList.sortedWith(
                compareByDescending<UserProgress> { it.totalSteps }
                    .thenByDescending { it.completedLoops }
            )
        } catch (e: Exception) {
            // 🆕 P2 FIX: Detaylı error logging ve state
            _lastError = when (e) {
                is java.net.UnknownHostException -> "İnternet bağlantısı yok"
                is com.google.firebase.firestore.FirebaseFirestoreException -> "Sunucu hatası: ${e.code}"
                else -> "Bilinmeyen hata: ${e.message}"
            }
            android.util.Log.e("LeaderboardRepo", "❌ getLeaderboard failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Kullanıcı skorunu gönder.
     * 
     * MALİYET OPTİMİZASYONU:
     * - Her adımda değil, sadece TUR TAMAMLANDIĞINDA çağrılır
     * - Aynı kullanıcının dokümanı üzerine yazılır (document count artmaz)
     * - Bu sayede Firebase ücretsiz kotası aşılmaz
     */
    override suspend fun submitScore(userProgress: UserProgress) {
        try {
            if (userProgress.userId.isBlank()) return // Auth yoksa gönderme
            
            // Mevcut skoru kontrol et - sadece daha iyi ise güncelle
            val existingDoc = firestore.collection("leaderboards")
                .document(userProgress.trackId)
                .collection("scores")
                .document(userProgress.userId)
                .get()
                .await()
            
            val existingProgress = existingDoc.toObject(UserProgress::class.java)
            
            // Güncelleme gerekli mi kontrol et
            val shouldUpdate = when {
                existingProgress == null -> true // İlk kayıt
                userProgress.completedLoops > existingProgress.completedLoops -> true // Daha fazla tur
                userProgress.completedLoops == existingProgress.completedLoops &&
                userProgress.bestLapTimeSeconds > 0 &&
                userProgress.bestLapTimeSeconds < existingProgress.bestLapTimeSeconds -> true // Aynı tur, daha iyi süre
                else -> false
            }
            
            if (shouldUpdate) {
                firestore.collection("leaderboards")
                    .document(userProgress.trackId)
                    .collection("scores")
                    .document(userProgress.userId)
                    .set(userProgress)
                    .await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Pist bazında genel istatistikleri getir
     */
    suspend fun getTrackStats(trackId: String): TrackStats {
        return try {
            val scores = firestore.collection("leaderboards")
                .document(trackId)
                .collection("scores")
                .get()
                .await()
                .toObjects(UserProgress::class.java)
            
            TrackStats(
                totalParticipants = scores.size,
                totalLoops = scores.sumOf { it.completedLoops },
                averageLapTime = if (scores.isNotEmpty()) 
                    scores.filter { it.bestLapTimeSeconds > 0 }
                        .map { it.bestLapTimeSeconds }
                        .average()
                        .toLong() 
                    else 0L
            )
        } catch (e: Exception) {
            TrackStats()
        }
    }

    /**
     * 🆕 Aylık Maraton Leaderboard (Cached)
     * COST-SAVING: 6 Saatlik Cache kullanır.
     */
    override suspend fun getMonthlyLeaderboard(
        trackId: String,
        month: String
    ): List<com.pace.legends.domain.repository.LeaderboardEntry> {
        val cacheDao = db.leaderboardCacheDao()
        val now = System.currentTimeMillis()
        val ttl = 6 * 60 * 60 * 1000L // 6 Saat Cache TTL

        // 1. Cache Kontrolü
        val cacheTimestamp = cacheDao.getCacheTimestamp(trackId, month) ?: 0L
        val isCacheValid = (now - cacheTimestamp) < ttl

        if (isCacheValid) {
            val cachedList = cacheDao.getLeaderboard(trackId, month)
            if (cachedList.isNotEmpty()) {
                android.util.Log.d("LeaderboardRepo", "✅ Using Cached Leaderboard (${cachedList.size} entries)")
                return cachedList.map { 
                    com.pace.legends.domain.repository.LeaderboardEntry(it.userId, it.displayName, it.steps, it.rank)
                }
            }
        }

        // 2. Firestore'dan Çek (Cache eski veya yok)
        return try {
            val result = firestore.collection("leaderboards")
                .document(trackId)
                .collection("monthly")
                .document(month)
                .collection("entries")
                .orderBy("steps", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .await()
            
            val entries = mutableListOf<com.pace.legends.domain.repository.LeaderboardEntry>()
            var rank = 0
            
            val cacheEntities = mutableListOf<com.pace.legends.data.local.entity.LeaderboardCacheEntity>()
            
            result.documents.forEach { doc ->
                if (doc.getBoolean("flagged") != true) {
                    if (entries.size < 50) {
                        rank++
                        val entry = com.pace.legends.domain.repository.LeaderboardEntry(
                            userId = doc.getString("userId") ?: "",
                            displayName = doc.getString("displayName") ?: "Racer",
                            steps = doc.getLong("steps") ?: 0L,
                            rank = rank,
                            isPro = doc.getBoolean("isPro") ?: false
                        )
                        entries.add(entry)
                        
                        // Cache Entity hazırla
                        cacheEntities.add(
                            com.pace.legends.data.local.entity.LeaderboardCacheEntity(
                                trackId = trackId,
                                periodId = month,
                                userId = entry.userId,
                                displayName = entry.displayName,
                                steps = entry.steps,
                                rank = entry.rank,
                                timestamp = now
                            )
                        )
                    }
                }
            }
            
            // 3. Cache Güncelle (Async yapabiliriz ama basitlik için await)
            if (cacheEntities.isNotEmpty()) {
                cacheDao.clearLeaderboard(trackId, month)
                cacheDao.insertAll(cacheEntities)
                android.util.Log.d("LeaderboardRepo", "💾 Leaderboard Cached (${cacheEntities.size} entries)")
            }
            
            entries
        } catch (e: Exception) {
            android.util.Log.e("LeaderboardRepo", "Leaderboard fetch failed: ${e.message}")
            // Fallback to cache if network fails, even if expired
            val fallbackCache = cacheDao.getLeaderboard(trackId, month)
            fallbackCache.map { 
                com.pace.legends.domain.repository.LeaderboardEntry(it.userId, it.displayName, it.steps, it.rank)
            }
        }
    }

    /**
     * 🆕 Kullanıcının Gerçek Sıralamasını Hesapla (Ölçeklenebilir)
     * 
     * Top 100'de olmayan kullanıcılar için "count" sorgusu ile sıralama bulur.
     * 
     * @param trackId Pist ID
     * @param periodId Dönem ID (örn: "period_0")
     * @param userSteps Kullanıcının adım sayısı
     * @return Sıralama (1-based) veya null
     */
    override suspend fun getUserRankBySteps(trackId: String, periodId: String, userSteps: Long): Int? {
        return try {
            // Kullanıcının adımından DAHA FAZLA adımı olan kullanıcı sayısını say
            val higherScores = firestore.collection("leaderboards")
                .document(trackId)
                .collection("monthly")
                .document(periodId)
                .collection("entries")
                .whereGreaterThan("steps", userSteps)
                .get()
                .await()
            
            // Sıralama = daha yüksek skorlu kişi sayısı + 1
            higherScores.size() + 1
        } catch (e: Exception) {
            android.util.Log.e("LeaderboardRepo", "getUserRankBySteps failed: ${e.message}")
            null
        }
    }
    
    /**
     * 🆕 Real Ghost Racing: Top Racers
     * En iyi tur zamanına (bestLapTimeSeconds) göre sıralar.
     */
    override suspend fun getTopRacers(trackId: String, limit: Int): List<UserProgress> {
        return try {
            val result = firestore.collection("leaderboards")
                .document(trackId)
                .collection("scores")
                .whereGreaterThan("bestLapTimeSeconds", 0) // Geçerli turu olanlar
                .orderBy("bestLapTimeSeconds", Query.Direction.ASCENDING) // En hızlı (küçük saniye)
                .limit(limit.toLong())
                .get()
                .await()
            result.toObjects(UserProgress::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 🆕 Toplam Katılımcı Sayısı
     */
    override suspend fun getTotalParticipants(trackId: String, periodId: String): Int {
        return try {
            val snapshot = firestore.collection("leaderboards")
                .document(trackId)
                .collection("monthly")
                .document(periodId)
                .collection("entries")
                .get()
                .await()
            snapshot.size()
        } catch (e: Exception) {
            0
        }
    }
}

/**
 * Pist istatistikleri
 */
data class TrackStats(
    val totalParticipants: Int = 0,
    val totalLoops: Int = 0,
    val averageLapTime: Long = 0
)

/**
 * Firestore'dan dönen raw data class
 */
private data class MonthlyEntryFirestore(
    val userId: String = "",
    val displayName: String = "",
    val steps: Long = 0
)
