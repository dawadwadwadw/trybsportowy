package com.trybsportowy

import android.app.Application
import androidx.room.Room
import com.trybsportowy.data.local.AppDatabase
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
            .fallbackToDestructiveMigration()
            .build()

        repository = ReadinessRepositoryImpl(database.readinessDao)
    }
}