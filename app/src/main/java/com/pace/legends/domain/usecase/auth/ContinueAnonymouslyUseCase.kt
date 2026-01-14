package com.pace.legends.domain.usecase.auth

import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.UserRepository
import com.pace.legends.domain.model.User
import com.pace.legends.domain.model.AuthResult
import javax.inject.Inject

class ContinueAnonymouslyUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(): AuthResult {
        try {
            val result = authRepository.signInAnonymously()
            if (result.isSuccess) {
                 val firebaseUser = authRepository.getCurrentUser() 
                    ?: return AuthResult.Error("User not found after anonymous sign-in")
                 
                 // FIX: getUser returns Result<User?>
                 val userResult = userRepository.getUser(firebaseUser.uid)
                 val existingUser = userResult.getOrNull()

                 if (existingUser != null) {
                     return AuthResult.Success(existingUser)
                 } else {
                     val newUser = User(
                        uid = firebaseUser.uid,
                        isAnonymous = true,
                        displayName = "Guest Runner",
                        createdAt = System.currentTimeMillis()
                     )
                     userRepository.saveUser(newUser)
                     return AuthResult.Success(newUser)
                 }
            } else {
                val exception = result.exceptionOrNull()
                return AuthResult.Error(exception?.message ?: "Anonymous sign-in failed", exception)
            }
        } catch (e: Exception) {
            return AuthResult.Error(e.message ?: "Unknown error", e)
        }
    }
}
