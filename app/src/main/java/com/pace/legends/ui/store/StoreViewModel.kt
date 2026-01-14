package com.pace.legends.ui.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pace.legends.domain.manager.RewardManager
import com.pace.legends.domain.model.AvatarFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StoreViewModel @Inject constructor(
    private val rewardManager: RewardManager
) : ViewModel() {
    
    private val _coinBalance = MutableStateFlow(0L)
    val coinBalance: StateFlow<Long> = _coinBalance.asStateFlow()
    
    // Satın alınabilir ürünler
    private val _storeFrames = MutableStateFlow<List<AvatarFrame>>(emptyList())
    val storeFrames: StateFlow<List<AvatarFrame>> = _storeFrames.asStateFlow()
    
    // Kullanıcının sahip olduğu frame ID'leri
    private val _ownedFrameIds = MutableStateFlow<List<String>>(emptyList())
    val ownedFrameIds: StateFlow<List<String>> = _ownedFrameIds.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()
    
    init {
        loadData()
        loadStoreItems()
    }
    
    fun loadData() {
        viewModelScope.launch {
            _coinBalance.value = rewardManager.getCoinBalance()
            val unlocked = rewardManager.getUnlockedFrames()
            _ownedFrameIds.value = unlocked.map { it.id }
        }
    }
    
    private fun loadStoreItems() {
        // Sadece fiyatı olanları filtrele
        val purchasable = AvatarFrame.ALL_FRAMES.filter { it.price != null }
        _storeFrames.value = purchasable
    }
    
    fun buyFrame(frame: AvatarFrame) {
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            _isLoading.value = true
            
            val success = rewardManager.buyAvatarFrame(frame)
            if (success) {
                _snackbarEvent.emit("🎉 ${frame.name} satın alındı!")
                loadData() // Bakiye ve envanteri güncelle
            } else {
                _snackbarEvent.emit("❌ Satın alma başarısız (Yetersiz bakiye veya zaten sahipsiniz)")
            }
            
            _isLoading.value = false
        }
    }
}
