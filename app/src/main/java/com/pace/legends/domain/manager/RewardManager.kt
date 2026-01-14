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
    /**
     * Kullanıcının coin bakiyesini getir
     */
    suspend fun getCoinBalance(): Result<Long> {
        val userId = authRepository.getCurrentUserId() 
            ?: return Result.failure(Exception("User not logged in"))
            
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            val balance = doc.getLong("coins") ?: 0L
            Result.success(balance)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to get coin balance: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Coin ekle ve işlem kaydı oluştur
     */
    suspend fun addCoins(
        type: CoinRewardType,
        description: String = type.description,
        periodId: String? = null,
        trackId: String? = null
    ): Result<Int> {
        val userId = authRepository.getCurrentUserId() 
            ?: return Result.failure(Exception("User not logged in"))
        
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
            
            Result.success(type.amount)
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to add coins: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Lig yükselme ödülü ver
     */
    suspend fun awardLeaguePromotion(
        newTier: LeagueTier,
        periodId: String? = null,
        trackId: String? = null
    ): Result<Unit> {
        // 1. Coin ödülü
        val coinResult = addCoins(
            type = CoinRewardType.LEAGUE_PROMOTION,
            description = "${newTier.displayName}'e yükseldin!",
            periodId = periodId,
            trackId = trackId
        )
        
        // 2. Çerçeve kilidi aç
        val frameResult = unlockAvatarFrame(newTier)
        
        return if (coinResult.isSuccess && frameResult.isSuccess) {
            Result.success(Unit)
        } else {
            // Partial success is still failure for the aggregate op? 
            // Or strictly log failures. Let's return failure if ANY failed.
            val exception = coinResult.exceptionOrNull() ?: frameResult.exceptionOrNull() 
                ?: Exception("Unknown error awarding promotion")
            Result.failure(exception)
        }
    }
    
    /**
     * Dönem sonu sıralama ödülü ver
     */
    suspend fun awardPeriodRank(
        rank: Int,
        periodId: String? = null,
        trackId: String? = null
    ): Result<Unit> {
        val rewardType = CoinRewardType.forRank(rank) ?: return Result.success(Unit) // No reward for this rank
        
        val result = addCoins(
            type = rewardType,
            description = "Dönem sonu ${rank}. sıra ödülü",
            periodId = periodId,
            trackId = trackId
        )
        return result.map { Unit }
    }
    
    /**
     * Avatar çerçevesi kilidi aç
     */
    suspend fun unlockAvatarFrame(tier: LeagueTier): Result<Unit> {
        val userId = authRepository.getCurrentUserId() 
            ?: return Result.failure(Exception("User not logged in"))
        val frame = AvatarFrame.forTier(tier)
        
        return try {
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
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to unlock frame: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Satın alma işlemi: Avatar Çerçevesi
     */
    suspend fun buyAvatarFrame(frame: AvatarFrame): Result<Unit> {
        if (frame.price == null) return Result.failure(IllegalArgumentException("Frame not for sale"))
        val userId = authRepository.getCurrentUserId() 
            ?: return Result.failure(Exception("User not logged in"))
        
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
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Buy frame failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Aktif çerçeveyi değiştir
     */
    suspend fun setActiveFrame(frameId: String): Result<Unit> {
        val userId = authRepository.getCurrentUserId() 
            ?: return Result.failure(Exception("User not logged in"))
        
        return try {
            // Önce açılmış mı kontrol et
            val doc = firestore.collection("users").document(userId).get().await()
            val unlockedFrames = doc.get("unlockedFrames") as? List<*> ?: listOf("default")
            
            if (!unlockedFrames.contains(frameId)) {
                android.util.Log.w(TAG, "Frame not unlocked: $frameId")
                return Result.failure(IllegalStateException("Frame locked"))
            }
            
            firestore.collection("users")
                .document(userId)
                .update("activeFrameId", frameId)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to set active frame: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Kullanıcının açtığı çerçeveleri getir
     */
    suspend fun getUnlockedFrames(): Result<List<AvatarFrame>> {
        val userId = authRepository.getCurrentUserId() 
            ?: return Result.failure(Exception("User not logged in"))
        
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            val unlockedIds = doc.get("unlockedFrames") as? List<*> ?: listOf("default")
            
            val frames = AvatarFrame.ALL_FRAMES.map { frame ->
                frame.copy(isUnlocked = unlockedIds.contains(frame.id))
            }
            Result.success(frames)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to get unlocked frames: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Aktif çerçeveyi getir
     */
    suspend fun getActiveFrame(): Result<AvatarFrame> {
        val userId = authRepository.getCurrentUserId() 
            ?: return Result.failure(Exception("User not logged in"))
        
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            val frameId = doc.getString("activeFrameId") ?: "default"
            Result.success(AvatarFrame.byId(frameId))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to get active frame: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Coin geçmişini getir (son N işlem)
     */
    suspend fun getCoinHistory(limit: Int = 20): Result<List<CoinTransaction>> {
        val userId = authRepository.getCurrentUserId() 
            ?: return Result.failure(Exception("User not logged in"))
        
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("coinHistory")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
            
            val history = snapshot.documents.mapNotNull { doc ->
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
            Result.success(history)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to get coin history: ${e.message}")
            Result.failure(e)
        }
    }
}
