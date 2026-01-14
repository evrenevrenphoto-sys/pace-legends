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
            _coinBalance.value = rewardManager.getCoinBalance().getOrDefault(0L)
            val unlocked = rewardManager.getUnlockedFrames().getOrDefault(emptyList())
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
            
            val result = rewardManager.buyAvatarFrame(frame)
            if (result.isSuccess) {
                _snackbarEvent.emit("🎉 ${frame.name} satın alındı!")
                loadData() // Bakiye ve envanteri güncelle
            } else {
                val error = result.exceptionOrNull()
                val msg = if (error?.message == "ALREADY_OWNED") "Zaten sahipsiniz" 
                         else if (error?.message == "INSUFFICIENT_FUNDS") "Yetersiz bakiye"
                         else "Satın alma başarısız"
                _snackbarEvent.emit("❌ $msg")
            }
            
            _isLoading.value = false
        }
    }
}
