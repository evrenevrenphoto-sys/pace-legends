package com.pace.legends.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.pace.legends.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth
) : AuthRepository {

    override suspend fun signInAnonymously(): Result<String> {
        return try {
            if (isUserSignedIn()) {
                val user = auth.currentUser ?: throw Exception("User was signed in but currentUser is null")
                return Result.success(user.uid)
            }
            val result = auth.signInAnonymously().await()
            val user = result.user ?: throw Exception("User is null after sign in")
            Result.success(user.uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<String> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: throw Exception("User is null after Google sign in")
            Result.success(user.uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Anonim kullanıcıyı Google hesabına bağla.
     * Bu sayede mevcut veriler korunur ve hesap kalıcı olur.
     */
    override suspend fun linkAnonymousToGoogle(idToken: String): Result<String> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("No current user to link"))
            
            if (!currentUser.isAnonymous) {
                return Result.failure(Exception("User is not anonymous"))
            }
            
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = currentUser.linkWithCredential(credential).await()
            val user = result.user ?: throw Exception("User is null after linking")
            Result.success(user.uid)
        } catch (e: Exception) {
            // Linking failed - maybe account already exists
            // Fall back to sign in with Google and migrate data manually
            Result.failure(e)
        }
    }

    override fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
    
    override fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }
    
    override fun isUserSignedIn(): Boolean {
        return auth.currentUser != null
    }
    
    override fun isAnonymousUser(): Boolean {
        return auth.currentUser?.isAnonymous == true
    }
    
    override suspend fun signOut() {
        auth.signOut()
    }
    
    override suspend fun updateProfile(displayName: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user signed in"))
            
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            
            user.updateProfile(profileUpdates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getAuthStateFlow(): kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.callbackFlow {
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser != null)
        }
        auth.addAuthStateListener(listener)
        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }
}
