package com.pace.legends.ui.profile

sealed interface UserUiState {
    data object Loading : UserUiState
    data object SignedOut : UserUiState
    data class Success(
        val uid: String,
        val displayName: String,
        val email: String?,
        val photoUrl: String?,
        val isAnonymous: Boolean,
        val isPro: Boolean = false // Future proof
    ) : UserUiState
    data class Error(val message: String) : UserUiState
}
