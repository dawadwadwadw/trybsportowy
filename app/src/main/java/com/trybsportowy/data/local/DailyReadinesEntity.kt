package com.trybsportowy.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_readiness")
data class DailyReadinessEntity(
    @PrimaryKey val dateTimestamp: Long,
    val sleepCode: String = "S0",
    val hrvCode: String = "H2",
    val stressCode: String = "L",
    val workCode: String = "W0",
    val alcoholCode: String = "A0",
    val physicalLoadCode: String = "P0",
    // ─── Drain fields (v3) ───────────────────────────
    val cnsDrain: Int = 0,           // 0–20, sum of CNS drain-tag points
    val bodyDrain: Int = 0,          // 0–20, sum of Body drain-tag points
    val drainTags: String = "",      // JSON list of selected tag IDs
    val nutritionCode: String = "N1",// N0 / N1 / N2 / N3
    // ─── Sync fields (v4, Phase 2) ───────────────────
    // Defaults declared via @ColumnInfo so the exported schema matches the
    // hand-written Migration_3_4 ALTERs exactly (CLAUDE.md §1.3, §4.2).
    @ColumnInfo(defaultValue = "Europe/Warsaw")
    val tz: String = "Europe/Warsaw",        // IANA tz, set at entry time (§1.8)
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0L,                // UTC epoch ms; bumped on every edit
    @ColumnInfo(defaultValue = "PENDING")
    val syncState: String = "PENDING",       // SyncState.name; only repo transitions it
    val syncError: String? = null            // last sync error message, if any
)

/**
 * Repairs nulls that Gson injects in place of Kotlin defaults.
 * Required after deserializing old backups (pre-v3, and pre-v4 sync fields).
 */
fun DailyReadinessEntity.sanitize(): DailyReadinessEntity = DailyReadinessEntity(
    dateTimestamp     = dateTimestamp,
    sleepCode         = (sleepCode        as? String) ?: "S0",
    hrvCode           = (hrvCode          as? String) ?: "H2",
    stressCode        = (stressCode       as? String) ?: "L",
    workCode          = (workCode         as? String) ?: "W0",
    alcoholCode       = (alcoholCode      as? String) ?: "A0",
    physicalLoadCode  = (physicalLoadCode as? String) ?: "P0",
    cnsDrain          = cnsDrain,
    bodyDrain         = bodyDrain,
    drainTags         = (drainTags        as? String) ?: "",
    nutritionCode     = (nutritionCode    as? String) ?: "N1",
    tz                = (tz               as? String) ?: "Europe/Warsaw",
    updatedAt         = updatedAt,
    syncState         = (syncState        as? String) ?: "PENDING",
    syncError         = syncError
)
