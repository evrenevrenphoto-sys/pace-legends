package com.pace.legends.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pace.legends.domain.manager.HealthConnectManager
import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.usecase.sync.SyncInitializer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val authRepository: AuthRepository,
    private val syncInitializer: SyncInitializer
) : ViewModel() {

    // Permission State
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    // Dialog State
    private val _showPitStopSuccessDialog = MutableStateFlow(false)
    val showPitStopSuccessDialog: StateFlow<Boolean> = _showPitStopSuccessDialog.asStateFlow()
    
    // 🆕 Demo Mode State (Health Connect skip edildi mi?)
    private val _isInDemoMode = MutableStateFlow(false)
    val isInDemoMode: StateFlow<Boolean> = _isInDemoMode.asStateFlow()
    
    // 🆕 Sync Initialization State
    private val _syncInitialized = MutableStateFlow(false)
    val syncInitialized: StateFlow<Boolean> = _syncInitialized.asStateFlow()

    // Auth State (Reactive Navigation)
    val isUserLoggedIn = authRepository.getAuthStateFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = authRepository.isUserSignedIn()
        )

    init {
        // 🆕 Initialize sync state when ViewModel is created (user is logged in)
        initializeSyncStateIfNeeded()
    }
    
    /**
     * 🆕 Initialize sync state from cloud on app startup.
     * 
     * This fixes the "fresh install" problem where local state is 0
     * but cloud has existing steps.
     * 
     * Called automatically in init{} block.
     */
    private fun initializeSyncStateIfNeeded() {
        viewModelScope.launch {
            if (authRepository.isUserSignedIn()) {
                val success = syncInitializer()
                _syncInitialized.value = success
                
                if (success) {
                    android.util.Log.i("MainViewModel", "✅ Sync state initialized successfully")
                } else {
                    android.util.Log.w("MainViewModel", "⚠️ Sync state initialization failed (will retry on next launch)")
                }
            }
        }
    }
    
    /**
     * 🆕 Re-initialize sync state after login.
     * Call this after successful login/signup.
     */
    fun onUserLoggedIn() {
        initializeSyncStateIfNeeded()
    }

    fun updatePermissionStatus(granted: Boolean, demoMode: Boolean = false) {
        _permissionsGranted.value = granted
        _isInDemoMode.value = demoMode
    }

    fun setPitStopDialogVisible(visible: Boolean) {
        _showPitStopSuccessDialog.value = visible
    }

    fun checkHealthConnectPermissions() {
        if (!healthConnectManager.isHealthConnectAvailable()) return

        viewModelScope.launch {
            _permissionsGranted.value = healthConnectManager.hasAllPermissions()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            // Reset sync state on logout
            _syncInitialized.value = false
        }
    }
}
