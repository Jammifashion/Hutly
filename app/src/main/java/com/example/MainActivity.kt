package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.components.*
import com.example.game.GameViewModel
import com.example.game.ScreenState
import com.example.state.HatViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                HutApp()
            }
        }
    }
}

@Composable
fun HutApp(
    gameViewModel: GameViewModel = viewModel(),
    hatViewModel: HatViewModel = viewModel()
) {
    val uiState by gameViewModel.uiState.collectAsState()
    val allHats by hatViewModel.allHats.collectAsState()
    val highestTier by hatViewModel.highestTier.collectAsState()
    val equippedTier by hatViewModel.equippedTier.collectAsState()
    val unlockedTiers by hatViewModel.unlockedTiers.collectAsState()
    val playerProgress by hatViewModel.playerProgress.collectAsState()

    // Initialize with starter hats and profile if empty
    LaunchedEffect(Unit) {
        hatViewModel.seedIfEmpty()
    }

    // Keep game multiplier in sync with highest unlocked hat
    LaunchedEffect(highestTier) {
        gameViewModel.setMultiplier(highestTier.multiplier)
    }

    // The hat to display on the player's sombrero: equipped tier if unlocked, or highest unlocked tier
    val activeHatTier = equippedTier

    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState.screenState) {
            ScreenState.START -> {
                StartScreen(
                    highestTier = activeHatTier,
                    collectedHatsCount = allHats.size,
                    highScore = playerProgress.highScore,
                    hasSavedGame = uiState.hasSavedGame,
                    savedRound = uiState.savedRound,
                    savedScore = uiState.savedScore,
                    unlockedTiersCount = unlockedTiers.size,
                    onStartGame = {
                        gameViewModel.startGame(multiplier = highestTier.multiplier)
                    },
                    onResumeGame = {
                        gameViewModel.resumeSavedGame()
                    },
                    onOpenShelf = {
                        gameViewModel.navigateTo(ScreenState.SHELF)
                    }
                )
            }
            ScreenState.PLAYING -> {
                GameScreen(
                    viewModel = gameViewModel,
                    activeTier = activeHatTier,
                    onBackToMenu = {
                        gameViewModel.navigateTo(ScreenState.START)
                    }
                )
            }
            ScreenState.ROUND_SUCCESS -> {
                // Background game scene
                GameScreen(
                    viewModel = gameViewModel,
                    activeTier = activeHatTier,
                    onBackToMenu = {
                        gameViewModel.navigateTo(ScreenState.START)
                    }
                )
                // Celebration Overlay
                RoundSuccessOverlay(
                    stars = uiState.stars,
                    score = uiState.score,
                    earnedTier = uiState.earnedTier,
                    round = uiState.round,
                    onGoToShelf = {
                        gameViewModel.navigateTo(ScreenState.SHELF)
                    },
                    onNextRound = {
                        gameViewModel.nextRound()
                    }
                )
            }
            ScreenState.GAME_OVER -> {
                GameScreen(
                    viewModel = gameViewModel,
                    activeTier = activeHatTier,
                    onBackToMenu = {
                        gameViewModel.navigateTo(ScreenState.START)
                    }
                )
                GameOverDialog(
                    score = uiState.score,
                    onRetry = {
                        gameViewModel.startGame(multiplier = highestTier.multiplier)
                    },
                    onGoToShelf = {
                        gameViewModel.navigateTo(ScreenState.SHELF)
                    }
                )
            }
            ScreenState.SHELF -> {
                ShelfScreen(
                    hatViewModel = hatViewModel,
                    onBack = {
                        gameViewModel.navigateTo(ScreenState.START)
                    }
                )
            }
        }
    }
}
