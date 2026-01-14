package com.pace.legends.domain.repository

import com.pace.legends.domain.model.User

interface UserRepository {
    suspend fun getUser(uid: String): Result<User?>
    suspend fun saveUser(user: User): Result<Unit>
    suspend fun updateSetupCompleted(uid: String, completed: Boolean): Result<Unit>
    suspend fun updateDisplayName(uid: String, displayName: String): Result<Unit>
}
