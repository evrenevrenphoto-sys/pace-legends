package com.pace.legends.domain.mapper

import com.google.firebase.auth.FirebaseUser
import com.pace.legends.domain.model.User

/**
 * Mapper for User domain model.
 * 
 * Maps FirebaseUser to Domain User.
 * NOTE: UI state mapping should be done in UI layer, not here.
 */
object UserMapper {

    /**
     * Maps Firebase SDK User to Domain User
     */
    fun FirebaseUser.toDomain(): User {
        return User(
            uid = this.uid,
            email = this.email,
            displayName = this.displayName,
            photoUrl = this.photoUrl?.toString(),
            isAnonymous = this.isAnonymous,
            isSetupCompleted = true, // Has to be fetched from FS usually, but default true if logged in
            createdAt = this.metadata?.creationTimestamp ?: System.currentTimeMillis()
        )
    }
}
