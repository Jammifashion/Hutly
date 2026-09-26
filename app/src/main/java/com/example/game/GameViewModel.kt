package com.example.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.audio.SoundSynthesizer
import com.example.game.haptics.HapticFeedbackManager
import com.example.game.physics.Glass3D
import com.example.game.physics.GlassStatus
import com.example.game.physics.HatPhysicsEngine
import com.example.state.GameRepository
import com.example.state.HatDatabase
import com.example.state.HatTier
import com.example.state.PlayerProgressEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class ScreenState {
    START,
    PLAYING,
    ROUND_SUCCESS,
    GAME_OVER,
    SHELF
}

data class GameUiState(
    val screenState: ScreenState = ScreenState.START,
    val round: Int = 1,
    val score: Int = 0,
    val combo: Int = 0,
    val lives: Int = 3,
    val finishedGlassesCount: Int = 0,
    val totalGlasses: Int = HatPhysicsEngine.NUM_GLASSES,
    val feedbackMessage: String? = null,
    val feedbackColor: Long = 0xFFFFD700,
    val earnedTier: HatTier = HatTier.GELB,
    val stars: Int = 1,
    val perfectCount: Int = 0,
    val goodCount: Int = 0,
    val currentMultiplier: Float = 1.0f,
    val isHapticsEnabled: Boolean = true,
    // Persisted player progress & saved game state
    val highScore: Int = 0,
    val hasSavedGame: Boolean = false,
    val savedRound: Int = 1,
    val savedScore: Int = 0,
    val totalHatsWon: Int = 0,
    val totalRoundsCleared: Int = 0,
    val highestUnlockedTier: HatTier = HatTier.GELB,
    val equippedTier: HatTier = HatTier.GELB
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val db = HatDatabase.getInstance(application)
    val repository = GameRepository(db.hatDao(), db.playerProgressDao())

    val physics = HatPhysicsEngine()
    val haptics = HapticFeedbackManager(application)

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState = _uiState.asStateFlow()

    private var gameLoopJob: Job? = null
    private var lastFrameTime = System.nanoTime()
    private var bubblingSoundTimer = 0f
    private var activeFillingGlass: Glass3D? = null

    init {
        viewModelScope.launch {
            repository.initIfEmpty()
            repository.playerProgress.collect { progress ->
                haptics.isEnabled = progress.isHapticsEnabled
                _uiState.value = _uiState.value.copy(
                    highScore = progress.highScore,
                    hasSavedGame = progress.hasSavedGame,
                    savedRound = progress.savedRound,
                    savedScore = progress.savedScore,
                    totalHatsWon = progress.totalHatsEverWon,
                    totalRoundsCleared = progress.totalRoundsCleared,
                    highestUnlockedTier = HatTier.fromLevel(progress.highestUnlockedLevel),
                    equippedTier = HatTier.fromLevel(progress.equippedTierLevel),
                    isHapticsEnabled = progress.isHapticsEnabled
                )
            }
        }
    }

    fun setMultiplier(multiplier: Float) {
        _uiState.value = _uiState.value.copy(currentMultiplier = multiplier)
    }

    fun toggleHaptics() {
        val newState = !_uiState.value.isHapticsEnabled
        haptics.isEnabled = newState
        _uiState.value = _uiState.value.copy(isHapticsEnabled = newState)
        if (newState) {
            haptics.vibrateSuccess()
        }
        viewModelScope.launch {
            repository.updatePreferences(isHaptics = newState)
        }
    }

    fun startGame(multiplier: Float = 1.0f) {
        val nextTargetFill = 0.65f + Random.nextFloat() * 0.15f
        physics.resetRound(roundNumber = 1, targetFill = nextTargetFill)

        val currentHaptics = _uiState.value.isHapticsEnabled
        _uiState.value = _uiState.value.copy(
            screenState = ScreenState.PLAYING,
            round = 1,
            score = 0,
            combo = 0,
            lives = 3,
            finishedGlassesCount = 0,
            perfectCount = 0,
            goodCount = 0,
            currentMultiplier = multiplier,
            isHapticsEnabled = currentHaptics,
            feedbackMessage = "Runde 1: Triff die Linie!"
        )
        viewModelScope.launch {
            repository.saveActiveGameProgress(
                round = 1,
                score = 0,
                lives = 3,
                combo = 0,
                multiplier = multiplier
            )
        }
        startGameLoop()
    }

    fun resumeSavedGame() {
        val state = _uiState.value
        if (!state.hasSavedGame) {
            startGame(state.currentMultiplier)
            return
        }

        val resumeRound = maxOf(1, state.savedRound)
        val resumeScore = maxOf(0, state.savedScore)
        val resumeLives = 3
        val nextTargetFill = 0.60f + Random.nextFloat() * 0.20f

        physics.resetRound(roundNumber = resumeRound, targetFill = nextTargetFill)

        _uiState.value = _uiState.value.copy(
            screenState = ScreenState.PLAYING,
            round = resumeRound,
            score = resumeScore,
            combo = 0,
            lives = resumeLives,
            finishedGlassesCount = 0,
            perfectCount = 0,
            goodCount = 0,
            feedbackMessage = "Spielstand geladen! Runde $resumeRound"
        )
        startGameLoop()
    }

    fun nextRound() {
        val nextRoundNum = _uiState.value.round + 1
        val nextTargetFill = 0.60f + Random.nextFloat() * 0.22f
        physics.resetRound(roundNumber = nextRoundNum, targetFill = nextTargetFill)

        _uiState.value = _uiState.value.copy(
            screenState = ScreenState.PLAYING,
            round = nextRoundNum,
            lives = 3,
            finishedGlassesCount = 0,
            perfectCount = 0,
            goodCount = 0,
            feedbackMessage = "Runde $nextRoundNum: Schneller!"
        )
        viewModelScope.launch {
            repository.saveActiveGameProgress(
                round = nextRoundNum,
                score = _uiState.value.score,
                lives = 3,
                combo = _uiState.value.combo,
                multiplier = _uiState.value.currentMultiplier
            )
        }
        startGameLoop()
    }

    fun navigateTo(state: ScreenState) {
        if (_uiState.value.screenState == ScreenState.PLAYING && state != ScreenState.PLAYING) {
            // Save in-progress game safely when leaving the playing field
            viewModelScope.launch {
                repository.saveActiveGameProgress(
                    round = _uiState.value.round,
                    score = _uiState.value.score,
                    lives = _uiState.value.lives,
                    combo = _uiState.value.combo,
                    multiplier = _uiState.value.currentMultiplier
                )
            }
        }
        _uiState.value = _uiState.value.copy(screenState = state)
        if (state != ScreenState.PLAYING) {
            stopGameLoop()
        }
    }

    fun startPouring() {
        if (_uiState.value.screenState != ScreenState.PLAYING) return
        physics.isPouring = true
        haptics.vibratePourStart()
        SoundSynthesizer.playGluckern()
    }

    fun stopPouring() {
        if (!physics.isPouring) return
        physics.isPouring = false

        // Evaluate the glass that was being poured into
        val collision = physics.lastCollisionResult
        val targetGlass = collision.hitGlass ?: activeFillingGlass

        if (targetGlass != null && (targetGlass.status == GlassStatus.FILLING || targetGlass.currentFill > 0.10f)) {
            evaluateGlass(targetGlass)
        }
        activeFillingGlass = null
    }

    private fun evaluateGlass(glass: Glass3D) {
        val result = physics.evaluateGlassFill(glass)
        val mult = _uiState.value.currentMultiplier
        val combo = _uiState.value.combo

        when (result) {
            GlassStatus.PERFECT -> {
                val pts = (100 * mult * (1f + combo * 0.25f)).toInt()
                val newCombo = combo + 1
                SoundSynthesizer.playKlirren()
                haptics.vibratePerfect()
                _uiState.value = _uiState.value.copy(
                    score = _uiState.value.score + pts,
                    combo = newCombo,
                    perfectCount = _uiState.value.perfectCount + 1,
                    feedbackMessage = "Perfekt! +$pts (Combo x$newCombo)",
                    feedbackColor = 0xFFFFD700
                )
            }
            GlassStatus.GOOD -> {
                val pts = (50 * mult).toInt()
                SoundSynthesizer.playKlirren()
                haptics.vibrateSuccess()
                _uiState.value = _uiState.value.copy(
                    score = _uiState.value.score + pts,
                    goodCount = _uiState.value.goodCount + 1,
                    feedbackMessage = "Gut! +$pts",
                    feedbackColor = 0xFF00E676
                )
            }
            GlassStatus.UNDERFILLED -> {
                val pts = 10
                haptics.vibratePourStart()
                _uiState.value = _uiState.value.copy(
                    score = _uiState.value.score + pts,
                    combo = 0,
                    feedbackMessage = "Zu wenig! +10",
                    feedbackColor = 0xFFFF9100
                )
            }
            GlassStatus.OVERFLOW -> {
                // Handled in onOverflow callback
            }
            GlassStatus.EMPTY -> {
                // Not enough poured to count
            }
            GlassStatus.FILLING -> {}
        }

        // Persist intermediate score and progress securely
        viewModelScope.launch {
            repository.saveActiveGameProgress(
                round = _uiState.value.round,
                score = _uiState.value.score,
                lives = _uiState.value.lives,
                combo = _uiState.value.combo,
                multiplier = _uiState.value.currentMultiplier
            )
        }

        checkRoundProgress()
    }

    private fun handleOverflow(glass: Glass3D) {
        SoundSynthesizer.playVerschuettet()
        haptics.vibrateSpill()
        val newLives = _uiState.value.lives - 1
        _uiState.value = _uiState.value.copy(
            lives = newLives,
            combo = 0,
            feedbackMessage = "Verschüttet! 1 Leben verloren!",
            feedbackColor = 0xFFFF1744
        )

        if (newLives <= 0) {
            stopGameLoop()
            val finalScore = _uiState.value.score
            viewModelScope.launch {
                repository.clearActiveGame(finalScore)
            }
            _uiState.value = _uiState.value.copy(
                screenState = ScreenState.GAME_OVER,
                feedbackMessage = "Keine Leben mehr!"
            )
        } else {
            viewModelScope.launch {
                repository.saveActiveGameProgress(
                    round = _uiState.value.round,
                    score = _uiState.value.score,
                    lives = newLives,
                    combo = 0,
                    multiplier = _uiState.value.currentMultiplier
                )
            }
            checkRoundProgress()
        }
    }

    private fun checkRoundProgress() {
        val finished = physics.glasses.count { it.isFinished }
        _uiState.value = _uiState.value.copy(finishedGlassesCount = finished)

        if (finished >= HatPhysicsEngine.NUM_GLASSES && _uiState.value.lives > 0) {
            // Round finished successfully!
            stopGameLoop()
            SoundSynthesizer.playJubel()
            haptics.vibrateRoundVictory()

            val perfect = _uiState.value.perfectCount
            val stars = when {
                perfect >= 7 -> 3
                perfect >= 4 -> 2
                else -> 1
            }

            // Determine earned hat tier
            val tier = when {
                stars == 3 && _uiState.value.round >= 3 -> HatTier.BLAU
                stars == 3 || _uiState.value.round >= 2 -> HatTier.GRUEN
                else -> HatTier.GELB
            }

            val finalRoundScore = _uiState.value.score
            val currentRound = _uiState.value.round

            // Persist the completed round, unlocked tier, score, and new hat to Room database
            viewModelScope.launch {
                repository.recordRoundSuccess(
                    round = currentRound,
                    score = finalRoundScore,
                    earnedTier = tier,
                    glassesCount = HatPhysicsEngine.NUM_GLASSES,
                    perfectCount = perfect
                )
            }

            _uiState.value = _uiState.value.copy(
                screenState = ScreenState.ROUND_SUCCESS,
                stars = stars,
                earnedTier = tier,
                feedbackMessage = "Hut geschafft!"
            )
        }
    }

    private fun startGameLoop() {
        stopGameLoop()
        lastFrameTime = System.nanoTime()

        gameLoopJob = viewModelScope.launch {
            while (isActive) {
                val now = System.nanoTime()
                val dt = ((now - lastFrameTime) / 1_000_000_000.0f).coerceIn(0.005f, 0.05f)
                lastFrameTime = now

                // Update physics
                physics.update(
                    deltaTime = dt,
                    onOverflow = { overflowGlass ->
                        handleOverflow(overflowGlass)
                    },
                    onTargetFillReached = { _ ->
                        haptics.vibrateTargetLineReached()
                    }
                )

                // If pouring, track active glass and play bubbling sound
                if (physics.isPouring) {
                    val collision = physics.lastCollisionResult
                    if (collision.isDirectHit || collision.isRimHit) {
                        activeFillingGlass = collision.hitGlass
                        bubblingSoundTimer += dt
                        if (bubblingSoundTimer >= 0.16f) {
                            bubblingSoundTimer = 0f
                            SoundSynthesizer.playGluckern()
                        }
                    }
                } else {
                    bubblingSoundTimer = 0f
                }

                delay(16) // ~60 FPS
            }
        }
    }

    private fun stopGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = null
        physics.isPouring = false
    }

    override fun onCleared() {
        super.onCleared()
        stopGameLoop()
    }
}
