package com.trybsportowy

import android.app.Application
import androidx.room.Room
import com.trybsportowy.data.local.AppDatabase
import com.trybsportowy.data.local.migrations.Migration_3_4
import com.trybsportowy.data.repository.ReadinessRepositoryImpl
import com.trybsportowy.domain.repository.ReadinessRepository
import com.trybsportowy.settings.SecretsStore
import com.trybsportowy.sync.SyncReadinessRepository
import com.trybsportowy.sync.SyncRepository
import com.trybsportowy.sync.SyncScheduler
import com.trybsportowy.sync.api.NetworkModule

class TrybsportowyApplication : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var repository: ReadinessRepository
        private set

    /** Sole accessor for the Bearer secret + server URL (§4.6). */
    val secretsStore: SecretsStore by lazy { SecretsStore(applicationContext) }

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

        // §10.5 — best-effort periodic sync; KEEP so it survives restarts.
        SyncScheduler.schedulePeriodic(this)
    }

    /**
     * Manual-DI factory for the sync graph (no Hilt in this project).
     * Rebuilt per call so a changed server URL/secret is picked up.
     */
    fun syncRepository(): SyncRepository = SyncRepository(
        readinessRepo = SyncReadinessRepository(database.readinessDao),
        syncAttemptDao = database.syncAttemptDao,
        cacheDao = database.computedScoreCacheDao,
        api = NetworkModule.createApi(secretsStore),
        secrets = secretsStore
    )
}
