package com.example.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class GameRepository(
    private val hatDao: HatDao,
    private val progressDao: PlayerProgressDao
) {
    val allHats: Flow<List<HatEntity>> = hatDao.getAllHats()

    val playerProgress: Flow<PlayerProgressEntity> = progressDao.getPlayerProgress().map {
        it ?: PlayerProgressEntity()
    }.distinctUntilChanged()

    val unlockedTiers: Flow<Set<HatTier>> = playerProgress.map { progress ->
        val levels = progress.getUnlockedLevels()
        levels.map { HatTier.fromLevel(it) }.toSet()
    }.distinctUntilChanged()

    val highestUnlockedTier: Flow<HatTier> = playerProgress.map { progress ->
        HatTier.fromLevel(progress.highestUnlockedLevel)
    }.distinctUntilChanged()

    val equippedTier: Flow<HatTier> = playerProgress.map { progress ->
        HatTier.fromLevel(progress.equippedTierLevel)
    }.distinctUntilChanged()

    val totalHatsCount: Flow<Int> = allHats.map { it.size }.distinctUntilChanged()

    suspend fun getProgressOnce(): PlayerProgressEntity {
        return progressDao.getPlayerProgressOnce() ?: PlayerProgressEntity().also {
            progressDao.savePlayerProgress(it)
        }
    }

    suspend fun initIfEmpty() {
        val currentProgress = progressDao.getPlayerProgressOnce()
        if (currentProgress == null) {
            progressDao.savePlayerProgress(
                PlayerProgressEntity(
                    id = 1,
                    unlockedTiersJson = "0",
                    highestUnlockedLevel = 0,
                    equippedTierLevel = 0,
                    totalHatsEverWon = 2
                )
            )
        }

        // Check if hats are empty; if so seed 2 starter hats
        val existingHats = hatDao.getAllHatsOnce()
        if (existingHats.isEmpty()) {
            hatDao.insertHat(HatEntity(type = 0, level = 0))
            hatDao.insertHat(HatEntity(type = 0, level = 0))
        }
    }

    suspend fun addEarnedHat(level: Int): HatTier {
        val tier = HatTier.fromLevel(level)
        hatDao.insertHat(HatEntity(type = tier.level, level = tier.level))

        val current = getProgressOnce()
        val unlocked = current.getUnlockedLevels().toMutableSet()
        unlocked.add(tier.level)
        val newHighest = maxOf(current.highestUnlockedLevel, tier.level)

        val updated = current.copy(
            totalHatsEverWon = current.totalHatsEverWon + 1,
            highestUnlockedLevel = newHighest,
            unlockedTiersJson = unlocked.sorted().joinToString(","),
            lastUpdated = System.currentTimeMillis()
        )
        progressDao.savePlayerProgress(updated)
        return tier
    }

    suspend fun equipTier(tier: HatTier) {
        val current = getProgressOnce()
        // Only allow equipping if unlocked
        if (current.getUnlockedLevels().contains(tier.level) || tier.level <= current.highestUnlockedLevel) {
            val updated = current.copy(
                equippedTierLevel = tier.level,
                lastUpdated = System.currentTimeMillis()
            )
            progressDao.savePlayerProgress(updated)
        }
    }

    suspend fun recordRoundSuccess(
        round: Int,
        score: Int,
        earnedTier: HatTier,
        glassesCount: Int,
        perfectCount: Int
    ) {
        val current = getProgressOnce()
        val unlocked = current.getUnlockedLevels().toMutableSet()
        unlocked.add(earnedTier.level)
        val newHighest = maxOf(current.highestUnlockedLevel, earnedTier.level)
        val newHighScore = maxOf(current.highScore, score)
        val newHighestRound = maxOf(current.highestRoundReached, round)

        // Also add the hat to the shelf
        hatDao.insertHat(HatEntity(type = earnedTier.level, level = earnedTier.level))

        val updated = current.copy(
            highScore = newHighScore,
            totalRoundsCleared = current.totalRoundsCleared + 1,
            highestRoundReached = newHighestRound,
            totalGlassesPoured = current.totalGlassesPoured + glassesCount,
            perfectPours = current.perfectPours + perfectCount,
            totalHatsEverWon = current.totalHatsEverWon + 1,
            highestUnlockedLevel = newHighest,
            unlockedTiersJson = unlocked.sorted().joinToString(","),
            // Clear in-progress saved game since round ended
            hasSavedGame = false,
            lastUpdated = System.currentTimeMillis()
        )
        progressDao.savePlayerProgress(updated)
    }

    suspend fun saveActiveGameProgress(
        round: Int,
        score: Int,
        lives: Int,
        combo: Int,
        multiplier: Float
    ) {
        val current = getProgressOnce()
        val newHighScore = maxOf(current.highScore, score)
        val newHighestRound = maxOf(current.highestRoundReached, round)

        val updated = current.copy(
            highScore = newHighScore,
            highestRoundReached = newHighestRound,
            hasSavedGame = true,
            savedRound = round,
            savedScore = score,
            savedLives = lives,
            savedCombo = combo,
            savedMultiplier = multiplier,
            lastUpdated = System.currentTimeMillis()
        )
        progressDao.savePlayerProgress(updated)
    }

    suspend fun clearActiveGame(finalScore: Int? = null) {
        val current = getProgressOnce()
        val newHighScore = if (finalScore != null) maxOf(current.highScore, finalScore) else current.highScore
        val updated = current.copy(
            highScore = newHighScore,
            hasSavedGame = false,
            lastUpdated = System.currentTimeMillis()
        )
        progressDao.savePlayerProgress(updated)
    }

    suspend fun updatePreferences(isHaptics: Boolean? = null, isMuted: Boolean? = null) {
        val current = getProgressOnce()
        val updated = current.copy(
            isHapticsEnabled = isHaptics ?: current.isHapticsEnabled,
            isSoundMuted = isMuted ?: current.isSoundMuted,
            lastUpdated = System.currentTimeMillis()
        )
        progressDao.savePlayerProgress(updated)
    }

    suspend fun mergeHats(toDeleteIds: List<Int>, newTier: HatTier) {
        hatDao.deleteHatsByIds(toDeleteIds)
        hatDao.insertHat(HatEntity(type = newTier.level, level = newTier.level))

        val current = getProgressOnce()
        val unlocked = current.getUnlockedLevels().toMutableSet()
        unlocked.add(newTier.level)
        val newHighest = maxOf(current.highestUnlockedLevel, newTier.level)

        val updated = current.copy(
            highestUnlockedLevel = newHighest,
            unlockedTiersJson = unlocked.sorted().joinToString(","),
            lastUpdated = System.currentTimeMillis()
        )
        progressDao.savePlayerProgress(updated)
    }
}
