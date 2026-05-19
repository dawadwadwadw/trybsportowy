package com.trybsportowy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Server-computed readiness, cached after every successful sync read-back
 * (GET /api/android/readiness). The phone is the source of truth for whether
 * a day exists; this table only mirrors the server's computed numbers so the
 * UI can show the canonical score (CLAUDE.md §2.2, §2.3).
 */
@Entity(tableName = "readiness_computed_cache")
data class ComputedScoreCacheEntity(
    @PrimaryKey val dateTimestamp: Long,
    val algorithmVersion: String,         // "v1" (CLAUDE.md §1.2)
    val readinessScore: Double,
    val dCode: Double,
    val totalCns: Double,
    val totalBody: Double,
    val overloadTriggered: Boolean,
    val fetchedAt: Long                   // UTC epoch ms when pulled from server
)
