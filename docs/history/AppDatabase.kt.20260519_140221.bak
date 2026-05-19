package com.trybsportowy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DailyReadinessEntity::class, DecaySettingsEntity::class, ChatMessageEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract val readinessDao: ReadinessDao
}