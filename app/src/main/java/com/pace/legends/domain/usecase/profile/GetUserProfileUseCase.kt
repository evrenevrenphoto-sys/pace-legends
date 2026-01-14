package com.pace.legends.domain.usecase.profile

import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.UserRepository
import javax.inject.Inject

/**
 * UseCase for fetching user profile information.
 * 
 * Combines Auth state with Firestore user data to provide
 * a unified profile view.
 */
class GetUserProfileUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    /**
     * Get current user profile.
     * 
     * @return UserProfileResult containing user data or signed-out state.
     */
    suspend operator fun invoke(): UserProfileResult {
        val firebaseUser = authRepository.getCurrentUser()
            ?: return UserProfileResult.SignedOut
        
        val userId = firebaseUser.uid
        val isAnonymous = authRepository.isAnonymousUser()
        
        return UserProfileResult.Success(
            userId = userId,
            displayName = firebaseUser.displayName ?: "Driver",
            email = firebaseUser.email,
            photoUrl = firebaseUser.photoUrl?.toString(),
            isAnonymous = isAnonymous,
            isPro = false // TODO: Inject SubscriptionManager when available
        )
    }
}

/**
 * Result sealed class for user profile fetch.
 */
sealed class UserProfileResult {
    data class Success(
        val userId: String,
        val displayName: String,
        val email: String?,
        val photoUrl: String?,
        val isAnonymous: Boolean,
        val isPro: Boolean
    ) : UserProfileResult()
    
    data object SignedOut : UserProfileResult()
    data object Loading : UserProfileResult()
}

