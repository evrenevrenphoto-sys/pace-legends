package com.pace.legends.domain.model

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val message: String, val throwable: Throwable? = null) : AuthResult()
}
