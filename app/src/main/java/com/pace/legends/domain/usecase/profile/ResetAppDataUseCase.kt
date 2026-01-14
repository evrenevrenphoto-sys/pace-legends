package com.pace.legends.domain.usecase.profile

import com.pace.legends.domain.repository.SystemRepository
import javax.inject.Inject

/**
 * UseCase for resetting all app data.
 * 
 * This is a DEBUG-only feature that clears local and optionally cloud data.
 * Context operations are delegated to SystemRepository (data layer).
 */
class ResetAppDataUseCase @Inject constructor(
    private val systemRepository: SystemRepository
) {
    /**
     * Reset all application data.
     * 
     * @param clearCloudData If true, also clears Firestore user data.
     * @return Result indicating success or failure.
     */
    suspend operator fun invoke(clearCloudData: Boolean = false): Result<Unit> {
        val result = systemRepository.clearAllLocalData(clearCloudData)
        
        if (result.isSuccess) {
            // Trigger app restart after successful clear
            systemRepository.restartApp()
        }
        
        return result
    }
}
