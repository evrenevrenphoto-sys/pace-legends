package com.pace.legends.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.pace.legends.domain.model.User
import com.pace.legends.domain.repository.UserRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseUserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userCacheDao: com.pace.legends.data.local.UserCacheDao, // 🆕 P2 FIX
    private val gson: com.google.gson.Gson // 🆕 P2 FIX
) : UserRepository {

    private val usersCollection = firestore.collection("users")
    private val CACHE_TTL = 60 * 60 * 1000L // 1 Hour

    override suspend fun getUser(uid: String): Result<User?> {
        // 1. Check Cache
        try {
            val cached = userCacheDao.getUser(uid)
            val now = System.currentTimeMillis()
            if (cached != null && (now - cached.cachedAt) < CACHE_TTL) {
                val user = gson.fromJson(cached.userDataJson, User::class.java)
                android.util.Log.d("UserRepo", "✅ User loaded from local cache")
                return Result.success(user)
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepo", "❌ Cache read failed: ${e.message}")
        }

        // 2. Fetch from Network
        return try {
            val snapshot = usersCollection.document(uid).get().await()
            if (snapshot.exists()) {
                val user = snapshot.toObject(User::class.java)
                
                // 3. Update Cache
                if (user != null) {
                    try {
                        val json = gson.toJson(user)
                        userCacheDao.insertUser(
                            com.pace.legends.data.local.entity.UserCacheEntity(
                                userId = uid,
                                userDataJson = json,
                                cachedAt = System.currentTimeMillis()
                            )
                        )
                        android.util.Log.d("UserRepo", "💾 User cached successfully")
                    } catch (e: Exception) { 
                        android.util.Log.e("UserRepo", "❌ Cache write failed: ${e.message}")
                    }
                    Result.success(user)
                } else {
                    Result.success(null)
                }
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveUser(user: User): Result<Unit> {
        return try {
            // merge=true kullanarak mevcut alanları koru (örn: adım sayısı vs varsa)
            usersCollection.document(user.uid).set(user, SetOptions.merge()).await()
            
            // 🆕 Cache Update (Invalidate or overwrite)
            // Here we overwrite to keep it fresh
            try {
                val json = gson.toJson(user)
                userCacheDao.insertUser(
                    com.pace.legends.data.local.entity.UserCacheEntity(
                        userId = user.uid,
                        userDataJson = json,
                        cachedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) { }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSetupCompleted(uid: String, completed: Boolean): Result<Unit> {
        return try {
            val updates = mapOf(
                "isSetupCompleted" to completed
            )
            usersCollection.document(uid).set(updates, SetOptions.merge()).await()
            
            // 🆕 Cache Invalidate (Simpler than read-modify-write for partial update)
            try { userCacheDao.deleteUser(uid) } catch (e: Exception) { }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateDisplayName(uid: String, displayName: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "displayName" to displayName
            )
            usersCollection.document(uid).set(updates, SetOptions.merge()).await()
            
            // 🆕 Cache Invalidate
            try { userCacheDao.deleteUser(uid) } catch (e: Exception) { }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
