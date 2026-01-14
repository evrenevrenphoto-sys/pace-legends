package com.pace.legends.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pace.legends.domain.model.UserBadge
import kotlinx.coroutines.flow.Flow

@Dao
interface UserBadgeDao {
    @Query("SELECT * FROM user_badges")
    fun observeEarnedBadges(): Flow<List<UserBadge>>

    @Query("SELECT * FROM user_badges")
    suspend fun getEarnedBadges(): List<UserBadge>

    @Query("SELECT EXISTS(SELECT 1 FROM user_badges WHERE badgeId = :badgeId)")
    suspend fun hasBadge(badgeId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBadge(badge: UserBadge)
}
