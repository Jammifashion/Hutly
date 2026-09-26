package com.example.state

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HatDao {
    @Query("SELECT * FROM hats ORDER BY level DESC, timestamp DESC")
    fun getAllHats(): Flow<List<HatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHat(hat: HatEntity): Long

    @Query("DELETE FROM hats WHERE id = :id")
    suspend fun deleteHatById(id: Int)

    @Query("DELETE FROM hats WHERE id IN (:ids)")
    suspend fun deleteHatsByIds(ids: List<Int>)

    @Query("SELECT MAX(level) FROM hats")
    fun getHighestHatLevel(): Flow<Int?>

    @Query("SELECT * FROM hats")
    suspend fun getAllHatsOnce(): List<HatEntity>
}
