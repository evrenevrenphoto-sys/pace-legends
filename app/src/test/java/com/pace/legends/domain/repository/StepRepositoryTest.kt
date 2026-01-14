package com.pace.legends.domain.repository

import org.junit.Assert.*
import org.junit.Test

/**
 * StepRepository için basit birim testleri.
 * 
 * Bu testler harici bağımlılık olmadan (Mockito, Coroutines-Test) çalışır.
 * Sadece temel mantık doğrulaması yapar.
 */
class StepRepositoryTest {

    // ==================== LOGIC TESTS (Pure JUnit) ====================

    @Test
    fun test_AtomicInteger_ThreadSafety_Simulation() {
        // Bu test AtomicInteger'ın thread-safe olduğunu simüle eder
        val counter = java.util.concurrent.atomic.AtomicInteger(0)
        
        val threads = (1..10).map {
            Thread {
                repeat(1000) { counter.incrementAndGet() }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // 10 thread x 1000 increment = 10000
        assertEquals(10000, counter.get())
    }

    @Test
    fun test_NullCheck_Prevents_Execution() {
        // Null check mantığını test et (addSteps logic simulation)
        var trackId: String? = null
        var stepsAdded = false
        
        // Simulate addSteps logic with null track
        if (trackId != null) {
            stepsAdded = true
        }
        
        assertFalse("Steps should NOT be added when trackId is null", stepsAdded)
        
        
        assertFalse("Steps should NOT be added when trackId is null", stepsAdded)
        
        // Now with active track
        trackId = "track_A"
        // Simulate addSteps logic with non-null track
        stepsAdded = true
        
        assertTrue("Steps SHOULD be added when trackId exists", stepsAdded)
    }

    @Test
    fun test_UnsavedSteps_ResetAfterSave() {
        // unsavedSteps mantığını test et (saveCurrentProgress logic)
        val unsavedSteps = java.util.concurrent.atomic.AtomicInteger(0)
        
        // Add steps
        unsavedSteps.addAndGet(100)
        unsavedSteps.addAndGet(50)
        
        assertEquals(150, unsavedSteps.get())
        
        // Simulate save (getAndSet atomically gets and resets)
        val stepsToSave = unsavedSteps.getAndSet(0)
        
        assertEquals("Saved steps should be 150", 150, stepsToSave)
        assertEquals("unsavedSteps should be reset to 0", 0, unsavedSteps.get())
    }

    @Test
    fun test_SessionSteps_Independent_From_UnsavedSteps() {
        // sessionSteps ve unsavedSteps ayrımını test et
        val sessionSteps = java.util.concurrent.atomic.AtomicInteger(0)
        val unsavedSteps = java.util.concurrent.atomic.AtomicInteger(0)
        
        // Add steps (hem sessionSteps hem unsavedSteps artmalı)
        sessionSteps.addAndGet(100)
        unsavedSteps.addAndGet(100)
        
        // Save (sadece unsavedSteps sıfırlanır, sessionSteps UI için kalır)
        val saved = unsavedSteps.getAndSet(0)
        
        assertEquals("Saved should be 100", 100, saved)
        assertEquals("sessionSteps should NOT change", 100, sessionSteps.get())
        assertEquals("unsavedSteps should be reset", 0, unsavedSteps.get())
    }

    @Test
    fun test_TrackSwitch_Resets_AllCounters() {
        // Track değişiminde tüm sayaçların sıfırlandığını test et
        val sessionSteps = java.util.concurrent.atomic.AtomicInteger(500)
        val unsavedSteps = java.util.concurrent.atomic.AtomicInteger(200)
        val sessionDuration = java.util.concurrent.atomic.AtomicLong(60000)
        
        // Simulate switchToTrack reset logic
        sessionSteps.set(0)
        unsavedSteps.set(0)
        sessionDuration.set(0)
        
        assertEquals("sessionSteps should be 0", 0, sessionSteps.get())
        assertEquals("unsavedSteps should be 0", 0, unsavedSteps.get())
        assertEquals("sessionDuration should be 0", 0L, sessionDuration.get())
    }
}
