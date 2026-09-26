package com.example

import com.example.game.physics.GlassStatus
import com.example.game.physics.HatPhysicsEngine
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class ExampleUnitTest {

    @Test
    fun testGlassesSetup() {
        val physics = HatPhysicsEngine()
        assertEquals(11, physics.glasses.size)

        physics.resetRound(roundNumber = 1, targetFill = 0.70f)
        assertEquals(11, physics.glasses.size)
        for (i in 0 until 11) {
            assertEquals(GlassStatus.EMPTY, physics.glasses[i].status)
            assertEquals(0f, physics.glasses[i].currentFill, 0.001f)
        }
    }

    @Test
    fun testPhysicsCollisionDetection_DirectHit() {
        val physics = HatPhysicsEngine()
        physics.resetRound(roundNumber = 1, targetFill = 0.70f)

        // Glass 0 starts at angle 0.
        // At angle 0: X = 115 * sin(0) = 0, Z = 115 * cos(0) = 115.
        // Pour nozzle is at (X = 0, Z = 115).
        // Glass 0 is perfectly aligned at rotation angle 0!
        physics.currentRotationAngle = 0f
        physics.baseAngularVelocity = 0f // Hold still for exact collision test
        physics.isPouring = true

        var overflowTriggered = false
        // Update physics with a small time step
        physics.update(0.1f) {
            overflowTriggered = true
        }

        val collision = physics.lastCollisionResult
        assertTrue("Expected direct hit when glass 0 is at nozzle", collision.isDirectHit)
        assertEquals(0, collision.hitGlassIndex)
        assertTrue("Glass should be colliding with stream", physics.glasses[0].isCollidingWithStream)
        assertTrue("Liquid fill should have increased", physics.glasses[0].currentFill > 0f)
        assertFalse(overflowTriggered)
    }

    @Test
    fun testPhysicsCollisionDetection_SpillWhenMisaligned() {
        val physics = HatPhysicsEngine()
        physics.resetRound(roundNumber = 1, targetFill = 0.70f)

        // Rotate turntable halfway between glass 0 and glass 1 (half of 2*PI/11 is PI/11)
        physics.currentRotationAngle = (PI / 11.0).toFloat()
        physics.baseAngularVelocity = 0f
        physics.isPouring = true

        physics.update(0.05f) {}

        val collision = physics.lastCollisionResult
        assertTrue("Expected spill when stream falls between glasses", collision.isSpill)
        assertFalse("Should not be direct hit when misaligned", collision.isDirectHit)
    }

    @Test
    fun testFillEvaluation_PerfectAndGood() {
        val physics = HatPhysicsEngine()
        val glass = physics.glasses[0]
        glass.targetFill = 0.70f

        // Perfect test (within ±0.05)
        glass.currentFill = 0.72f
        var status = physics.evaluateGlassFill(glass)
        assertEquals(GlassStatus.PERFECT, status)

        // Good test (within ±0.15)
        glass.currentFill = 0.80f
        status = physics.evaluateGlassFill(glass)
        assertEquals(GlassStatus.GOOD, status)

        // Underfilled test
        glass.currentFill = 0.40f
        status = physics.evaluateGlassFill(glass)
        assertEquals(GlassStatus.UNDERFILLED, status)

        // Overflow test
        glass.currentFill = 1.10f
        status = physics.evaluateGlassFill(glass)
        assertEquals(GlassStatus.OVERFLOW, status)
    }

    @Test
    fun testHapticTargetFillTrigger() {
        val physics = HatPhysicsEngine()
        physics.resetRound(roundNumber = 1, targetFill = 0.70f)
        physics.currentRotationAngle = 0f
        physics.baseAngularVelocity = 0f
        physics.isPouring = true

        var hapticTriggered = false
        // Advance physics until target fill is approached
        for (step in 0 until 50) {
            physics.update(0.04f, onOverflow = {}) { glass ->
                hapticTriggered = true
            }
            if (hapticTriggered) break
        }

        assertTrue("Haptic feedback callback should trigger when target fill is reached", hapticTriggered)
        assertTrue(physics.glasses[0].hasSignaledTargetHaptic)
    }

    @Test
    fun testGlassBottomSplashParticleGeneration() {
        val physics = HatPhysicsEngine()
        physics.resetRound(roundNumber = 1, targetFill = 0.70f)
        physics.currentRotationAngle = 0f
        physics.baseAngularVelocity = 0f
        physics.isPouring = true

        // Simulate a few physics frames while pouring directly into glass 0
        for (step in 0 until 5) {
            physics.update(0.03f, onOverflow = {})
        }

        // Verify splash particles and ripples were created on contact with the glass bottom
        val bottomParticles = physics.splashParticles.filter { it.isGlassBottom }
        assertTrue("Splash particles should be spawned on glass bottom contact", bottomParticles.isNotEmpty())
        assertTrue("Direct hit should register while pouring into aligned glass", physics.lastCollisionResult.isDirectHit)
    }

    @Test
    fun testPlayerProgressEntity_UnlockedLevelsAndDefaults() {
        val progress = com.example.state.PlayerProgressEntity(
            highScore = 1500,
            highestUnlockedLevel = 3,
            unlockedTiersJson = "0,1,2,3",
            equippedTierLevel = 2,
            hasSavedGame = true,
            savedRound = 4,
            savedScore = 1200
        )

        assertEquals(1500, progress.highScore)
        assertEquals(3, progress.highestUnlockedLevel)
        assertEquals(setOf(0, 1, 2, 3), progress.getUnlockedLevels())
        assertTrue(progress.hasSavedGame)
        assertEquals(4, progress.savedRound)
        assertEquals(1200, progress.savedScore)

        val unlockedHatTiers = progress.getUnlockedLevels().map { com.example.state.HatTier.fromLevel(it) }
        assertTrue(unlockedHatTiers.contains(com.example.state.HatTier.GELB))
        assertTrue(unlockedHatTiers.contains(com.example.state.HatTier.GRUEN))
        assertTrue(unlockedHatTiers.contains(com.example.state.HatTier.BLAU))
        assertTrue(unlockedHatTiers.contains(com.example.state.HatTier.ROT))
        assertFalse(unlockedHatTiers.contains(com.example.state.HatTier.GOLD))
    }
}
