package com.pace.legends.domain.usecase.profile

import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.UserRepository
import javax.inject.Inject

/**
 * UseCase for updating user profile display name.
 * 
 * Updates both Firebase Auth profile and Firestore user document.
 */
class UpdateUserProfileUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    /**
     * Update the user's display name.
     * 
     * @param newDisplayName The new display name to set.
     * @return Result indicating success or failure.
     */
    suspend operator fun invoke(newDisplayName: String): Result<Unit> {
        if (newDisplayName.isBlank()) {
            return Result.failure(IllegalArgumentException("Display name cannot be blank"))
        }
        
        // 1. Update Firebase Auth profile
        val authResult = authRepository.updateProfile(newDisplayName)
        if (authResult.isFailure) {
            return authResult
        }
        
        // 2. Update Firestore user document
        val userId = authRepository.getCurrentUserId()
            ?: return Result.failure(IllegalStateException("User not signed in"))
        
        return userRepository.updateDisplayName(userId, newDisplayName)
    }
}
