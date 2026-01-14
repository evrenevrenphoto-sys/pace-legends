package com.pace.legends.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pace.legends.domain.model.BadgeType
import com.pace.legends.domain.repository.ChampionBadge
import com.pace.legends.domain.usecase.profile.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Profil ekranı için ViewModel
 * 
 * Clean Architecture: Tüm iş mantığı UseCase'lere devredildi.
 * ViewModel sadece UI state yönetimi ve UseCase koordinasyonu yapar.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    // UseCases - Single Responsibility
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val updateUserProfileUseCase: UpdateUserProfileUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val getBadgesUseCase: GetBadgesUseCase,
    private val getChampionBadgesUseCase: GetChampionBadgesUseCase,
    private val claimPastChampionshipsUseCase: ClaimPastChampionshipsUseCase,
    private val resetAppDataUseCase: ResetAppDataUseCase,
    // Managers for remaining features (Sync, Rewards, Frames)
    private val stepSyncManager: com.pace.legends.domain.manager.StepSyncManager,
    private val leagueManager: com.pace.legends.domain.manager.LeagueManager,
    private val healthConnectManager: com.pace.legends.domain.manager.HealthConnectManager,
    private val rewardManager: com.pace.legends.domain.manager.RewardManager,
    private val stepRepository: com.pace.legends.domain.repository.StepRepository,
    private val authRepository: com.pace.legends.domain.repository.AuthRepository,
    private val subscriptionManager: com.pace.legends.domain.manager.SubscriptionManager // 🆕 Injected
) : ViewModel() {

    // 🛡️ Anti-Cheat Stats State (Debug Panel)
    data class AntiCheatStatsUI(
        val totalSteps: Long = 0,
        val manualSteps: Long = 0,
        val verifiedSteps: Long = 0,
        val manualPercentage: Float = 0f,
        val isLoading: Boolean = false,
        val lastCheck: String = "-"
    )
    
    private val _antiCheatStats = MutableStateFlow(AntiCheatStatsUI())
    val antiCheatStats: StateFlow<AntiCheatStatsUI> = _antiCheatStats.asStateFlow()

    // 🆕 Champion Badges State (from UseCase)
    data class ChampionBadgeUI(
        val badgeId: String,
        val periodDisplayName: String,
        val trackDisplayName: String,
        val rank: Int,
        val earnedAt: Long
    )
    
    private val _championBadges = MutableStateFlow<List<ChampionBadgeUI>>(emptyList())
    val championBadges: StateFlow<List<ChampionBadgeUI>> = _championBadges.asStateFlow()
    
    // 🆕 All Badges State (from UseCase)
    data class BadgeUI(
        val type: BadgeType,
        val isEarned: Boolean,
        val earnedAt: Long?
    )
    
    private val _allBadges = MutableStateFlow<List<BadgeUI>>(emptyList())
    val allBadges: StateFlow<List<BadgeUI>> = _allBadges.asStateFlow()

    private val _userUiState = MutableStateFlow<UserUiState>(UserUiState.Loading)
    val userUiState: StateFlow<UserUiState> = _userUiState.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 💰 Coin bakiyesi
    private val _coinBalance = MutableStateFlow(0L)
    val coinBalance: StateFlow<Long> = _coinBalance.asStateFlow()
    
    // Navigation event for Logout -> Login
    private val _navigateToLogin = MutableSharedFlow<Unit>()
    val navigateToLogin: SharedFlow<Unit> = _navigateToLogin.asSharedFlow()
    
    // Snackbar events
    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()
    
    // 🎁 Reward Dialog State
    private val _currentReward = MutableStateFlow<com.pace.legends.domain.manager.RewardManager.RewardEvent?>(null)
    val currentReward: StateFlow<com.pace.legends.domain.manager.RewardManager.RewardEvent?> = _currentReward.asStateFlow()
    
    // 🖼️ Avatar Frames
    private val _unlockedFrames = MutableStateFlow<List<com.pace.legends.domain.model.AvatarFrame>>(emptyList())
    val unlockedFrames: StateFlow<List<com.pace.legends.domain.model.AvatarFrame>> = _unlockedFrames.asStateFlow()
    
    private val _activeFrame = MutableStateFlow<com.pace.legends.domain.model.AvatarFrame>(com.pace.legends.domain.model.AvatarFrame.DEFAULT)
    val activeFrame: StateFlow<com.pace.legends.domain.model.AvatarFrame> = _activeFrame.asStateFlow()
    
    init {
        loadUserInfo()
        loadCoinBalance()
        collectRewards()
        loadUnlockedFrames()
        loadActiveFrame()
    }
    
    private fun loadActiveFrame() {
        viewModelScope.launch {
            _activeFrame.value = rewardManager.getActiveFrame().getOrDefault(com.pace.legends.domain.model.AvatarFrame.DEFAULT)
        }
    }
    
    fun loadUnlockedFrames() {
        viewModelScope.launch {
            _unlockedFrames.value = rewardManager.getUnlockedFrames().getOrDefault(emptyList())
        }
    }
    
    fun setActiveFrame(frameId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = rewardManager.setActiveFrame(frameId)
            if (result.isSuccess) {
                loadUserInfo()
                loadActiveFrame()
                _snackbarEvent.emit("🖼️ Tema değiştirildi!")
            } else {
                _snackbarEvent.emit("❌ Tema değiştirilemedi: ${result.exceptionOrNull()?.message ?: "Kilitli"}")
            }
            _isLoading.value = false
        }
    }
    
    private fun collectRewards() {
        viewModelScope.launch {
            rewardManager.rewardEvents.collect { event ->
                _currentReward.value = event
                loadCoinBalance()
            }
        }
    }
    
    fun dismissReward() {
        _currentReward.value = null
    }

    /**
     * Load user profile using UseCase
     */
    fun loadUserInfo() {
        viewModelScope.launch {
            _userUiState.value = UserUiState.Loading
            
            when (val result = getUserProfileUseCase()) {
                is UserProfileResult.SignedOut -> {
                    _userUiState.value = UserUiState.SignedOut
                }
                is UserProfileResult.Success -> {
                    _userUiState.value = UserUiState.Success(
                        uid = result.userId,
                        displayName = result.displayName,
                        email = result.email,
                        photoUrl = result.photoUrl,
                        isAnonymous = result.isAnonymous,
                        isPro = result.isPro
                    )
                    loadChampionBadges()
                    loadAllBadges()
                }
                is UserProfileResult.Loading -> {
                    _userUiState.value = UserUiState.Loading
                }
            }
        }
    }
    
    private fun loadCoinBalance() {
        viewModelScope.launch {
            val balance = rewardManager.getCoinBalance().getOrDefault(0L)
            _coinBalance.value = balance
        }
    }
    
    /**
     * Load all badges using UseCase
     */
    private fun loadAllBadges() {
        viewModelScope.launch {
            try {
                val badges = getBadgesUseCase()
                _allBadges.value = badges.map { info ->
                    BadgeUI(
                        type = info.type,
                        isEarned = info.isEarned,
                        earnedAt = info.earnedAt
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileVM", "Failed to load badges: ${e.message}")
            }
        }
    }
    
    /**
     * 🛡️ ANTI-CHEAT: Manuel vs Otomatik adım istatistiklerini yükle
     */
    fun loadAntiCheatStats() {
        viewModelScope.launch {
            _antiCheatStats.value = _antiCheatStats.value.copy(isLoading = true)
            try {
                val now = java.time.Instant.now()
                val startOfPeriod = now.minus(java.time.Duration.ofDays(30))
                
                val stats = healthConnectManager.getStepRecordStats(startOfPeriod, now)
                
                val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val lastCheckTime = timeFormat.format(java.util.Date())
                
                _antiCheatStats.value = AntiCheatStatsUI(
                    totalSteps = stats.totalSteps,
                    manualSteps = stats.manualSteps,
                    verifiedSteps = stats.verifiedSteps,
                    manualPercentage = stats.filteredPercentage,
                    isLoading = false,
                    lastCheck = lastCheckTime
                )
                
                if (stats.manualSteps > 0) {
                    _snackbarEvent.emit("⚠️ ${stats.manualSteps} manuel adım tespit edildi ve filtrelendi!")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("ProfileVM", "Anti-cheat stats error: ${e.message}")
                _antiCheatStats.value = _antiCheatStats.value.copy(isLoading = false)
            }
        }
    }
    
    /**
     * Load championship badges using UseCase
     */
    private fun loadChampionBadges() {
        viewModelScope.launch {
            try {
                val badges = getChampionBadgesUseCase()
                _championBadges.value = badges.map { badge ->
                    ChampionBadgeUI(
                        badgeId = badge.badgeId,
                        periodDisplayName = badge.periodDisplayName,
                        trackDisplayName = badge.trackDisplayName,
                        rank = badge.rank,
                        earnedAt = badge.earnedAt
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileVM", "Failed to load champion badges: ${e.message}")
            }
        }
    }
    
    /**
     * Sign out using UseCase
     */
    fun signOut() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = signOutUseCase()
                if (result.isSuccess) {
                    _snackbarEvent.emit("✅ Çıkış yapıldı!")
                    kotlinx.coroutines.delay(500)
                    _navigateToLogin.emit(Unit)
                } else {
                    _snackbarEvent.emit("❌ Çıkış yapılamadı: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _snackbarEvent.emit("❌ Çıkış yapılamadı: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Update display name using UseCase
     */
    fun updateDisplayName(newName: String) {
        if (newName.isBlank()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = updateUserProfileUseCase(newName)
                if (result.isSuccess) {
                    loadUserInfo()
                    _snackbarEvent.emit("✅ İsim güncellendi!")
                } else {
                    _snackbarEvent.emit("❌ İsim güncellenemedi: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _snackbarEvent.emit("❌ İsim güncellenemedi: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun refreshUser() {
        loadUserInfo()
    }
    
    /**
     * Claim past championships using UseCase
     */
    fun claimPastChampionships() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (val result = claimPastChampionshipsUseCase()) {
                    is ClaimResult.Success -> {
                        _snackbarEvent.emit("🏆 ${result.claimedCount} geçmiş şampiyonluk rozeti eklendi!")
                        loadChampionBadges()
                    }
                    is ClaimResult.NoBadgesToClaim -> {
                        _snackbarEvent.emit("ℹ️ Talep edilecek geçmiş şampiyonluk bulunamadı.")
                    }
                    is ClaimResult.NotSignedIn -> {
                        _snackbarEvent.emit("❌ Hata: Kullanıcı girişi yok!")
                    }
                }
            } catch (e: Exception) {
                _snackbarEvent.emit("❌ Hata: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Force sync (debug feature)
     */
    fun forceSyncNow() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentSteps = stepRepository.monthlySteps.value
                var trackId = stepRepository.currentTrackId.value
                
                if (trackId == null) {
                    _snackbarEvent.emit("⚠️ Pist verisi eksik! Buluttan kurtarılıyor...")
                    stepRepository.refreshUserData()
                    
                    kotlinx.coroutines.delay(1000)
                    val restoredTrackId = stepRepository.currentTrackId.value
                    if (restoredTrackId == null) {
                        _snackbarEvent.emit("❌ Hata: Pist kurtarılamadı. Lütfen tekrar giriş yapın.")
                        return@launch
                    }
                    trackId = restoredTrackId
                }
                
                stepSyncManager.syncIfNeeded(currentSteps, trackId, force = true)
                stepRepository.syncHealthConnectSteps(force = true)
                
                _snackbarEvent.emit("✅ Force Sync Tamamlandı: $currentSteps adım")
                
            } catch (e: Exception) {
                _snackbarEvent.emit("❌ Sync Hatası: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Reset app data using UseCase (removes Context from ViewModel)
     */
    fun resetAppData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _snackbarEvent.emit("🛑 Tüm veriler siliniyor...")
                
                val result = resetAppDataUseCase(clearCloudData = true)
                if (result.isFailure) {
                    _snackbarEvent.emit("❌ Sıfırlama hatası: ${result.exceptionOrNull()?.message}")
                    _isLoading.value = false
                }
                // If success, app will restart automatically
            } catch (e: Exception) {
                _snackbarEvent.emit("❌ Sıfırlama hatası: ${e.message}")
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 🧪 TEST: Manuel Coin Ekleme (Gerçekten Firebase'e yazar)
     */
    fun debugAddCoins() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val amountResult = rewardManager.addCoins(
                    type = com.pace.legends.domain.model.CoinRewardType.DEBUG_BONUS,
                    description = "DEBUG: Manuel Coin Ekleme"
                )
                val amount = amountResult.getOrNull()
                if (amount != null && amount > 0) {
                    loadCoinBalance()
                    _snackbarEvent.emit("💰 DEBUG: +$amount Coin Eklendi!")
                } else {
                     _snackbarEvent.emit("❌ Coin eklenemedi: ${amountResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _snackbarEvent.emit("❌ Hata: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 🧪 TEST: Sadece UI Dialog Göster (Coin eklemez)
     */
    fun testRewardDialog() {
        val testEvent = com.pace.legends.domain.manager.RewardManager.RewardEvent(
            type = com.pace.legends.domain.model.CoinRewardType.LEAGUE_PROMOTION,
            amount = 500,
            message = "Debug Test Ödülü: Harika İş! (Sadece UI test, coin eklenmedi)"
        )
        val method = ProfileViewModel::class.java.getDeclaredField("_currentReward")
        method.isAccessible = true
        (method.get(this) as MutableStateFlow<com.pace.legends.domain.manager.RewardManager.RewardEvent?>).value = testEvent
    }

    /**
     * 🧪 TEST: DÖNEM SONU SİMÜLASYONU
     */
    fun testProcessEndOfPeriod() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    _snackbarEvent.emit("❌ Hata: Kullanıcı girişi yok!")
                    return@launch
                }

                val currentTrack = stepRepository.currentTrackId.value 
                if (currentTrack == null) {
                    _snackbarEvent.emit("❌ Hata: Aktif pist verisi yok!")
                    return@launch
                }
                
                val currentPeriod = stepSyncManager.getCurrentPeriod() 
                if (currentPeriod.isEmpty()) {
                    _snackbarEvent.emit("❌ Hata: Dönem bilgisi alınamadı!")
                    return@launch
                }

                val rank = 1 
                
                _snackbarEvent.emit("⏳ Dönem sonu işlemi başlatılıyor... (Test Rank: $rank)")
                
                val result = leagueManager.processEndOfPeriod(userId, currentPeriod, rank)
                
                if (result != null) {
                    if (result.isPromotion) {
                        _snackbarEvent.emit("🎉 TEBRİKLER! ${result.previousTier.displayName} -> ${result.newTier.displayName}")
                    } else if (result.isDemotion) {
                        _snackbarEvent.emit("📉 Düşüş: ${result.previousTier.displayName} -> ${result.newTier.displayName}")
                    } else {
                        _snackbarEvent.emit("ℹ️ Lig değişmedi.")
                    }
                } else {
                    _snackbarEvent.emit("⚠️ İşlem yapıldı ama lig değişmedi.")
                }
                
                kotlinx.coroutines.delay(2000)
                stepRepository.refreshUserData()
                loadUserInfo()
                
            } catch (e: Exception) {
                _snackbarEvent.emit("❌ Dönem sonu hatası: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun toggleProStatus() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Mevcut durumu kontrol et
                val currentPro = subscriptionManager.isPro.value
                val newStatus = !currentPro
                
                // Debug override'" ayarla
                subscriptionManager.setDebugPro(newStatus)
                
                // UI'" güncelle
                loadUserInfo()
                loadCoinBalance()
                
                _snackbarEvent.emit(if (newStatus) "⭐️ DEBUG: PRO Modu Aktif!" else "ℹ️ DEBUG: PRO Modu Kapatıldı")
            } catch (e: Exception) {
                _snackbarEvent.emit("❌ Hata: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
}