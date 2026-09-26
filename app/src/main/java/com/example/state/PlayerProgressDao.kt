package com.example.state

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerProgressDao {
    @Query("SELECT * FROM player_progress WHERE id = 1 LIMIT 1")
    fun getPlayerProgress(): Flow<PlayerProgressEntity?>

    @Query("SELECT * FROM player_progress WHERE id = 1 LIMIT 1")
    suspend fun getPlayerProgressOnce(): PlayerProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlayerProgress(progress: PlayerProgressEntity)

    @Query("UPDATE player_progress SET highScore = :newHighScore, lastUpdated = :now WHERE id = 1 AND :newHighScore > highScore")
    suspend fun updateHighScoreIfHigher(newHighScore: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE player_progress SET hasSavedGame = 0, lastUpdated = :now WHERE id = 1")
    suspend fun clearSavedGame(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM player_progress")
    suspend fun deleteAll()
}
