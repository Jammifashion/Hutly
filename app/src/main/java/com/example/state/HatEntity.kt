package com.example.state

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hats")
data class HatEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: Int, // 0: Gelb, 1: Grün, ...
    val level: Int,
    val timestamp: Long = System.currentTimeMillis()
)
