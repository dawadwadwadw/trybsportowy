package com.trybsportowy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "daily_readiness")
data class DailyReadinessEntity(
    @PrimaryKey val dateTimestamp: Long,
    val sleepCode: String = "S0",
    val hrvCode: String = "H2",
    val stressCode: String = "L",
    val workCode: String = "W0",
    val alcoholCode: String = "A0",
    val physicalLoadCode: String = "P0",
    // ─── Nowe pola v3 ────────────────────────────────
    val cnsDrain: Int = 0,           // 0–20, suma punktów z tagów CNS
    val bodyDrain: Int = 0,          // 0–20, suma punktów z tagów Body
    val drainTags: String = "",      // JSON z listą zaznaczonych tagów
    val nutritionCode: String = "N1" // N0 / N1 / N2 / N3
)

/**
 * Naprawia null-y które Gson wstrzykuje zamiast Kotlinowych defaults.
 * Wymagane po deserializacji starych backupów (przed v3).
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
    nutritionCode     = (nutritionCode    as? String) ?: "N1"
)