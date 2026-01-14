package com.pace.legends.domain.model

import com.google.firebase.firestore.PropertyName

data class User(
    @get:PropertyName("uid")
    val uid: String = "",
    
    @get:PropertyName("email")
    val email: String? = null,
    
    @get:PropertyName("displayName")
    val displayName: String? = null,

    @get:PropertyName("photoUrl")
    val photoUrl: String? = null,
    
    @get:PropertyName("isAnonymous")
    val isAnonymous: Boolean = false,
    
    @get:PropertyName("isSetupCompleted")
    val isSetupCompleted: Boolean = false,
    
    @get:PropertyName("createdAt")
    val createdAt: Long = System.currentTimeMillis()
) {
    // Backward compatibility alias
    val id: String
        get() = uid

    // No-arg constructor for Firestore
    constructor() : this("", null, null, null, false, false, 0)
}
