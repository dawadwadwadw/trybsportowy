package com.trybsportowy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decay_settings")
data class DecaySettingsEntity(
    @PrimaryKey val id: Int = 1,
    val weightToday: Float = 1.0f,
    val weightYesterday: Float = 0.75f,
    val weightYesterdaySleep: Float = 0.8f,
    val weightTwoDaysAgo: Float = 0.5f,
    val weightThreeDaysAgo: Float = 0.2f
)