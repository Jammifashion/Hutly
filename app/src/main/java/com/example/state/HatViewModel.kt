package com.example.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.audio.SoundSynthesizer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = HatDatabase.getInstance(application)
    val repository = GameRepository(db.hatDao(), db.playerProgressDao())

    val allHats = repository.allHats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val playerProgress = repository.playerProgress.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerProgressEntity()
    )

    val unlockedTiers = repository.unlockedTiers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = setOf(HatTier.GELB)
    )

    val highestUnlockedTier = repository.highestUnlockedTier.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HatTier.GELB
    )

    val equippedTier = repository.equippedTier.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HatTier.GELB
    )

    // Current multiplier determined by highest unlocked or owned hat
    val highestTier = repository.highestUnlockedTier.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HatTier.GELB
    )

    private val _mergeEvents = MutableSharedFlow<HatTier>()
    val mergeEvents = _mergeEvents.asSharedFlow()

    private val _isMerging = MutableStateFlow(false)
    val isMerging = _isMerging.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initIfEmpty()
        }
    }

    fun addEarnedHat(level: Int = 0) {
        viewModelScope.launch {
            repository.addEarnedHat(level)
        }
    }

    fun equipTier(tier: HatTier) {
        viewModelScope.launch {
            repository.equipTier(tier)
            SoundSynthesizer.playClick()
        }
    }

    /**
     * Checks if 3 or more hats of the same level (below Diamant) exist and merges them.
     */
    fun performAutoMerge(onMerged: ((HatTier) -> Unit)? = null) {
        viewModelScope.launch {
            val currentList = allHats.value
            val grouped = currentList.groupBy { it.level }

            // Find lowest level with at least 3 hats that can be merged
            for (lvl in 0 until HatTier.DIAMANT.level) {
                val hatsOfLevel = grouped[lvl]
                if (hatsOfLevel != null && hatsOfLevel.size >= 3) {
                    _isMerging.value = true
                    val toMerge = hatsOfLevel.take(3)
                    val nextTier = HatTier.fromLevel(lvl + 1)

                    repository.mergeHats(toMerge.map { it.id }, nextTier)

                    SoundSynthesizer.playMergeChime()
                    _mergeEvents.emit(nextTier)
                    onMerged?.invoke(nextTier)
                    _isMerging.value = false
                    break
                }
            }
        }
    }

    /**
     * Seeds initial hats and ensures player profile exists.
     */
    fun seedIfEmpty() {
        viewModelScope.launch {
            repository.initIfEmpty()
        }
    }
}
