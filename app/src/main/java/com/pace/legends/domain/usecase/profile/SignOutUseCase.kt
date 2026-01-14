package com.pace.legends.domain.usecase.profile

import com.pace.legends.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * UseCase for user sign out.
 * 
 * Handles the auth sign out process cleanly.
 */
class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    /**
     * Sign out the current user.
     * 
     * @return Result indicating success or failure.
     */
    suspend operator fun invoke(): Result<Unit> {
        return try {
            authRepository.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
