package com.trybsportowy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Decay weights for the readiness algorithm (CLAUDE.md §4.1.1).
 *
 * Uses [Double] to ensure bit-identical results with the server's Python implementation
 * (which uses 64-bit floats).
 */
@Entity(tableName = "decay_settings")
data class DecaySettingsEntity(
    @PrimaryKey val id: Int = 1,
    val weightToday: Double = 1.0,
    val weightYesterday: Double = 0.75,
    val weightYesterdaySleep: Double = 0.8,
    val weightTwoDaysAgo: Double = 0.5,
    val weightThreeDaysAgo: Double = 0.2
)
