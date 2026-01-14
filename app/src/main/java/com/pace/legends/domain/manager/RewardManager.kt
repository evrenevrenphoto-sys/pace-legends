package com.pace.legends.domain.manager

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pace.legends.domain.model.AvatarFrame
import com.pace.legends.domain.model.CoinRewardType
import com.pace.legends.domain.model.CoinTransaction
import com.pace.legends.domain.model.LeagueTier
import com.pace.legends.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ödül Sistemi Yöneticisi
 * 
 * Coin kazanma, kozmetik kilidi açma ve ödül geçmişi yönetimi.
 * 
 * Firestore Yapısı:
 * - users/{userId}.coins: Long (toplam bakiye)
 * - users/{userId}.activeFrameId: String (aktif çerçeve)
 * - users/{userId}.unlockedFrames: List<String> (açılmış çerçeveler)
 * - users/{userId}/coinHistory/{txId}: CoinTransaction
 */
@Singleton
class RewardManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val TAG = "RewardManager"
    }
    
    // 🎉 Ödül kazanıldığında UI'ı bilgilendir
    data class RewardEvent(
        val type: CoinRewardType,
        val amount: Int,
        val newFrame: AvatarFrame? = null,
        val message: String
    )
    
    private val _rewardEvents = MutableSharedFlow<RewardEvent>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val rewardEvents: SharedFlow<RewardEvent> = _rewardEvents.asSharedFlow()
    
    /**
     * Kullanıcının coin bakiyesini getir
     */
    suspend fun getCoinBalance(): Long {
        val userId = authRepository.getCurrentUserId() ?: return 0L
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            doc.getLong("coins") ?: 0L
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to get coin balance: ${e.message}")
            0L
        }
    }
    
    /**
     * Coin ekle ve işlem kaydı oluştur
     * 
     * @param type Ödül türü
     * @param description Açıklama (opsiyonel)
     * @param periodId Dönem ID (opsiyonel)
     * @param trackId Pist ID (opsiyonel)
     * @return Eklenen coin miktarı veya 0 (hata durumunda)
     */
    suspend fun addCoins(
        type: CoinRewardType,
        description: String = type.description,
        periodId: String? = null,
        trackId: String? = null
    ): Int {
        val userId = authRepository.getCurrentUserId() ?: return 0
        
        return try {
            val userRef = firestore.collection("users").document(userId)
            
            // Transaction ile atomik güncelleme
            firestore.runTransaction { transaction ->
                // 1. Mevcut bakiyeyi oku
                val snapshot = transaction.get(userRef)
                val currentCoins = snapshot.getLong("coins") ?: 0L
                
                // 2. Yeni bakiyeyi kaydet
                transaction.update(userRef, "coins", currentCoins + type.amount)
                
                type.amount
            }.await()
            
            // 3. İşlem kaydı oluştur (transaction dışında)
            val tx = CoinTransaction(
                userId = userId,
                type = type.name,
                amount = type.amount,
                description = description,
                periodId = periodId,
                trackId = trackId
            )
            
            firestore.collection("users")
                .document(userId)
                .collection("coinHistory")
                .add(tx.toMap())
                .await()
            
            android.util.Log.d(TAG, "💰 +${type.amount} coins awarded: ${type.name}")
            
            // 4. UI'ı bilgilendir
            _rewardEvents.tryEmit(
                RewardEvent(
                    type = type,
                    amount = type.amount,
                    message = "${type.emoji} +${type.amount} Coin: $description"
                )
            )
            
            type.amount
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to add coins: ${e.message}")
            0
        }
    }
    
    /**
     * Lig yükselme ödülü ver
     * 
     * @param newTier Yeni lig kademesi
     * @param periodId Dönem ID
     * @param trackId Pist ID
     */
    suspend fun awardLeaguePromotion(
        newTier: LeagueTier,
        periodId: String? = null,
        trackId: String? = null
    ) {
        // 1. Coin ödülü
        addCoins(
            type = CoinRewardType.LEAGUE_PROMOTION,
            description = "${newTier.displayName}'e yükseldin!",
            periodId = periodId,
            trackId = trackId
        )
        
        // 2. Çerçeve kilidi aç
        unlockAvatarFrame(newTier)
    }
    
    /**
     * Dönem sonu sıralama ödülü ver
     * 
     * @param rank Kullanıcının sıralaması (1, 2, 3)
     * @param periodId Dönem ID
     * @param trackId Pist ID
     */
    suspend fun awardPeriodRank(
        rank: Int,
        periodId: String? = null,
        trackId: String? = null
    ) {
        val rewardType = CoinRewardType.forRank(rank) ?: return
        
        addCoins(
            type = rewardType,
            description = "Dönem sonu ${rank}. sıra ödülü",
            periodId = periodId,
            trackId = trackId
        )
    }
    
    /**
     * Avatar çerçevesi kilidi aç
     * 
     * @param tier Açılacak çerçevenin lig kademesi
     */
    suspend fun unlockAvatarFrame(tier: LeagueTier) {
        val userId = authRepository.getCurrentUserId() ?: return
        val frame = AvatarFrame.forTier(tier)
        
        try {
            val userRef = firestore.collection("users").document(userId)
            
            // unlockedFrames array'ine ekle (duplicate'i önle)
            userRef.update(
                "unlockedFrames", FieldValue.arrayUnion(frame.id)
            ).await()
            
            android.util.Log.d(TAG, "🖼️ Frame unlocked: ${frame.name}")
            
            // UI'ı bilgilendir
            _rewardEvents.tryEmit(
                RewardEvent(
                    type = CoinRewardType.LEAGUE_PROMOTION,
                    amount = 0,
                    newFrame = frame,
                    message = "${frame.emoji} ${frame.name} açıldı!"
                )
            )
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to unlock frame: ${e.message}")
        }
    }
    
    /**
     * Satın alma işlemi: Avatar Çerçevesi
     */
    suspend fun buyAvatarFrame(frame: AvatarFrame): Boolean {
        if (frame.price == null) return false
        val userId = authRepository.getCurrentUserId() ?: return false
        
        return try {
            firestore.runTransaction { transaction ->
                val userRef = firestore.collection("users").document(userId)
                val snapshot = transaction.get(userRef)
                
                // 1. Zaten sahip mi?
                val unlocked = snapshot.get("unlockedFrames") as? List<String> ?: emptyList()
                if (unlocked.contains(frame.id)) {
                    throw IllegalStateException("ALREADY_OWNED")
                }
                
                // 2. Yeterli bakiye var mı?
                val currentCoins = snapshot.getLong("coins") ?: 0L
                if (currentCoins < frame.price) {
                    throw IllegalStateException("INSUFFICIENT_FUNDS")
                }
                
                // 3. Satın al
                transaction.update(userRef, "coins", currentCoins - frame.price)
                transaction.update(userRef, "unlockedFrames", FieldValue.arrayUnion(frame.id))
            }.await()
            
            // 4. Log
            val tx = CoinTransaction(
                userId = userId,
                type = CoinRewardType.PURCHASE.name,
                amount = -frame.price,
                description = "Satın Alma: ${frame.name}"
            )
            firestore.collection("users").document(userId).collection("coinHistory").add(tx.toMap())
            
            // 5. UI Bilgilendir
            _rewardEvents.tryEmit(
                RewardEvent(
                    type = CoinRewardType.PURCHASE,
                    amount = -frame.price,
                    newFrame = frame,
                    message = "${frame.name} satın alındı!"
                )
            )
            
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Buy frame failed: ${e.message}")
            false
        }
    }
    
    /**
     * Aktif çerçeveyi değiştir
     * 
     * @param frameId Çerçeve ID
     * @return Başarılı mı
     */
    suspend fun setActiveFrame(frameId: String): Boolean {
        val userId = authRepository.getCurrentUserId() ?: return false
        
        return try {
            // Önce açılmış mı kontrol et
            val doc = firestore.collection("users").document(userId).get().await()
            val unlockedFrames = doc.get("unlockedFrames") as? List<*> ?: listOf("default")
            
            if (!unlockedFrames.contains(frameId)) {
                android.util.Log.w(TAG, "Frame not unlocked: $frameId")
                return false
            }
            
            firestore.collection("users")
                .document(userId)
                .update("activeFrameId", frameId)
                .await()
            
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to set active frame: ${e.message}")
            false
        }
    }
    
    /**
     * Kullanıcının açtığı çerçeveleri getir
     */
    suspend fun getUnlockedFrames(): List<AvatarFrame> {
        val userId = authRepository.getCurrentUserId() ?: return listOf(AvatarFrame.DEFAULT)
        
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            val unlockedIds = doc.get("unlockedFrames") as? List<*> ?: listOf("default")
            
            AvatarFrame.ALL_FRAMES.map { frame ->
                frame.copy(isUnlocked = unlockedIds.contains(frame.id))
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to get unlocked frames: ${e.message}")
            listOf(AvatarFrame.DEFAULT)
        }
    }
    
    /**
     * Aktif çerçeveyi getir
     */
    suspend fun getActiveFrame(): AvatarFrame {
        val userId = authRepository.getCurrentUserId() ?: return AvatarFrame.DEFAULT
        
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            val frameId = doc.getString("activeFrameId") ?: "default"
            AvatarFrame.byId(frameId)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to get active frame: ${e.message}")
            AvatarFrame.DEFAULT
        }
    }
    
    /**
     * Coin geçmişini getir (son N işlem)
     */
    suspend fun getCoinHistory(limit: Int = 20): List<CoinTransaction> {
        val userId = authRepository.getCurrentUserId() ?: return emptyList()
        
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("coinHistory")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                try {
                    CoinTransaction(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        type = doc.getString("type") ?: "",
                        amount = doc.getLong("amount")?.toInt() ?: 0,
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        description = doc.getString("description") ?: "",
                        periodId = doc.getString("periodId"),
                        trackId = doc.getString("trackId")
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to get coin history: ${e.message}")
            emptyList()
        }
    }
}
