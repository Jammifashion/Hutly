package com.example.state

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HatEntity::class, PlayerProgressEntity::class],
    version = 2,
    exportSchema = false
)
abstract class HatDatabase : RoomDatabase() {
    abstract fun hatDao(): HatDao
    abstract fun playerProgressDao(): PlayerProgressDao

    companion object {
        @Volatile
        private var INSTANCE: HatDatabase? = null

        fun getInstance(context: Context): HatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HatDatabase::class.java,
                    "hat-database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
