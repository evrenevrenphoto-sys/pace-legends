package com.pace.legends.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Kullanıcı verileri için yerel cache.
 * P2 FIX: Performansı artırmak ve offline erişimi iyileştirmek için.
 */
@Entity(tableName = "user_cache")
data class UserCacheEntity(
    @PrimaryKey
    val userId: String,
    
    val userDataJson: String, // Full user object as JSON to be flexible
    
    val cachedAt: Long
)
