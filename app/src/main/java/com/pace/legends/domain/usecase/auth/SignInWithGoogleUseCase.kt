package com.pace.legends.domain.usecase.auth

import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.UserRepository
import com.pace.legends.domain.model.User
import com.pace.legends.domain.model.AuthResult
import javax.inject.Inject

/**
 * UseCase for Google Sign-In flow.
 */
class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(idToken: String): AuthResult {
        try {
            // 1. Firebase Auth Sign-In
            val authResult = authRepository.signInWithGoogle(idToken)
            
            if (authResult.isSuccess) {
                val firebaseUser = authRepository.getCurrentUser() 
                    ?: return AuthResult.Error("User not found after sign-in")
                
                // 2. Check/Create Firestore Doc
                // FIX: getUser returns Result<User?>, not User?
                val userResult = userRepository.getUser(firebaseUser.uid)
                val existingUser = userResult.getOrNull()
                
                if (existingUser != null) {
                    return AuthResult.Success(existingUser)
                } else {
                    val newUser = User(
                        uid = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        displayName = firebaseUser.displayName ?: "Runner",
                        photoUrl = firebaseUser.photoUrl?.toString(),
                        createdAt = System.currentTimeMillis()
                    )
                    userRepository.saveUser(newUser)
                    android.util.Log.d("SignInUseCase", "Created new user doc: ${newUser.id}")
                    return AuthResult.Success(newUser)
                }
            } else {
                val exception = authResult.exceptionOrNull()
                return AuthResult.Error(exception?.message ?: "Sign-in failed", exception)
            }
        } catch (e: Exception) {
            return AuthResult.Error(e.message ?: "Unknown error", e)
        }
    }
}
