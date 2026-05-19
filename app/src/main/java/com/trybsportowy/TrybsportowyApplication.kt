package com.trybsportowy

import android.app.Application
import androidx.room.Room
import com.trybsportowy.data.local.AppDatabase
import com.trybsportowy.data.local.migrations.Migration_3_4
import com.trybsportowy.data.repository.ReadinessRepositoryImpl
import com.trybsportowy.domain.repository.ReadinessRepository

class TrybsportowyApplication : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var repository: ReadinessRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "trybsportowy_db"
        )
            // §1.3: NO destructive fallback. Every version bump ships a
            // hand-written Migration committed in the same change.
            .addMigrations(Migration_3_4)
            .build()

        repository = ReadinessRepositoryImpl(database.readinessDao)
    }
}
