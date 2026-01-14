package com.pace.legends.domain.model

/**
 * 🔒 Network ve Lock Durumu Sonucu
 */
sealed class LockStatusResult {
    data class Locked(val trackId: String, val month: String) : LockStatusResult()
    object NotLocked : LockStatusResult()
    data class Error(val exception: Exception) : LockStatusResult()
}
