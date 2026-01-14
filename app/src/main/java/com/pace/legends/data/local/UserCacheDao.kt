package com.pace.legends.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pace.legends.data.local.entity.UserCacheEntity

@Dao
interface UserCacheDao {
    
    @Query("SELECT * FROM user_cache WHERE userId = :userId")
    suspend fun getUser(userId: String): UserCacheEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(userCache: UserCacheEntity)
    
    @Query("DELETE FROM user_cache WHERE userId = :userId")
    suspend fun deleteUser(userId: String)
    
    @Query("DELETE FROM user_cache")
    suspend fun clearAll()
}
