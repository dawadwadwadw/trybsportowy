package com.trybsportowy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_readiness")
data class DailyReadinessEntity(
    @PrimaryKey val dateTimestamp: Long, // Początek dnia w milisekundach
    val sleepCode: String = "S0",
    val hrvCode: String = "H2", // H2 to 0 punktów, bezpieczny default
    val stressCode: String = "L",
    val workCode: String = "W0",
    val alcoholCode: String = "A0",
    val physicalLoadCode: String = "P0"
)