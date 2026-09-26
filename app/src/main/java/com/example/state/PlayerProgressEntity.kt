package com.example.state

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the persistent player game state, statistics, and unlocked tiers locally in Room.
 */
@Entity(tableName = "player_progress")
data class PlayerProgressEntity(
    @PrimaryKey val id: Int = 1, // Single profile record
    val highScore: Int = 0,
    val highestUnlockedLevel: Int = 0, // 0 = Gelb, up to 6 = Diamant
    val unlockedTiersJson: String = "0", // Comma-separated list of unlocked levels, e.g. "0,1"
    val equippedTierLevel: Int = 0,
    val totalHatsEverWon: Int = 2, // Starts with initial 2 starter hats
    val totalRoundsCleared: Int = 0,
    val highestRoundReached: Int = 1,
    val totalGlassesPoured: Int = 0,
    val perfectPours: Int = 0,
    // Active saved in-progress game
    val hasSavedGame: Boolean = false,
    val savedRound: Int = 1,
    val savedScore: Int = 0,
    val savedLives: Int = 3,
    val savedCombo: Int = 0,
    val savedMultiplier: Float = 1.0f,
    // Player preferences
    val isHapticsEnabled: Boolean = true,
    val isSoundMuted: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun getUnlockedLevels(): Set<Int> {
        return unlockedTiersJson.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
            .ifEmpty { setOf(0) }
    }
}
