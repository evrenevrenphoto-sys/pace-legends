package com.pace.legends.domain.usecase.sync

import com.pace.legends.domain.manager.StepSyncManager
import com.pace.legends.domain.repository.StepRepository
import javax.inject.Inject

/**
 * UseCase for checking and handling period transitions.
 * 
 * Detects when a new period starts and triggers:
 * - Period archival
 * - League promotion/demotion processing
 * 
 * Called by DataSyncWorker periodically.
 */
class CheckPeriodTransitionUseCase @Inject constructor(
    private val stepSyncManager: StepSyncManager,
    private val stepRepository: StepRepository
) {
    /**
     * Check if period has changed and process transition.
     * 
     * @return TransitionResult indicating what happened
     */
    suspend operator fun invoke(): TransitionResult {
        return try {
            val periodChanged = stepSyncManager.checkForPeriodTransition()
            
            if (periodChanged) {
                TransitionResult.PeriodChanged
            } else {
                TransitionResult.NoPeriodChange
            }
        } catch (e: Exception) {
            TransitionResult.Error(e.message ?: "Unknown error")
        }
    }
}

/**
 * Result of period transition check.
 */
sealed class TransitionResult {
    /** Period changed, archival and league processing triggered */
    data object PeriodChanged : TransitionResult()
    
    /** No period change detected */
    data object NoPeriodChange : TransitionResult()
    
    /** Error during check */
    data class Error(val message: String) : TransitionResult()
}
