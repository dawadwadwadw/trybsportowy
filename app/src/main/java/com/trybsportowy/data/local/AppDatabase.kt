package com.trybsportowy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DailyReadinessEntity::class,
        DecaySettingsEntity::class,
        ChatMessageEntity::class,
        SyncAttemptEntity::class,
        ComputedScoreCacheEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract val readinessDao: ReadinessDao
    abstract val syncAttemptDao: SyncAttemptDao
    abstract val computedScoreCacheDao: ComputedScoreCacheDao
}
