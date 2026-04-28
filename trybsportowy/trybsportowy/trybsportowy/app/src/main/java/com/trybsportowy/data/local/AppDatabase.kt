package com.trybsportowy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DailyReadinessEntity::class, DecaySettingsEntity::class, ChatMessageEntity::class], // Dodano ChatMessageEntity
    version = 2, // Podnosimy wersję bazy!
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract val readinessDao: ReadinessDao
}