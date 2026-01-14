package com.pace.legends.domain.repository

import com.google.firebase.auth.FirebaseUser

interface AuthRepository {
    suspend fun signInAnonymously(): Result<String>
    suspend fun signInWithGoogle(idToken: String): Result<String>
    suspend fun linkAnonymousToGoogle(idToken: String): Result<String>
    fun getCurrentUserId(): String?
    fun getCurrentUser(): FirebaseUser?
    fun isUserSignedIn(): Boolean
    fun isAnonymousUser(): Boolean
    suspend fun signOut()
    
    // 🆕 Profil Güncelleme
    suspend fun updateProfile(displayName: String): Result<Unit>
    
    // 🆕 Auth State Flow for UI Reactivity
    fun getAuthStateFlow(): kotlinx.coroutines.flow.Flow<Boolean>
}
