package com.trybsportowy.sync

import com.trybsportowy.data.local.ComputedScoreCacheEntity
import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.sync.api.dto.DailyReadinessDto
import com.trybsportowy.sync.api.dto.ReadinessRecordDto

/**
 * Entity <-> DTO mapping, and the ONE place the epoch-unit boundary is crossed.
 *
 * Local `DailyReadinessEntity.dateTimestamp` is epoch MILLIS (the app's
 * long-standing convention — e.g. MainActivity uses Instant.ofEpochMilli on
 * it). The server contract (§2.2) is epoch SECONDS. We convert ONLY here, at
 * the wire boundary, and never mutate stored values (§4.5, non-destructive).
 * Verify against a real stored row during the Phase 5 device checkpoint before
 * trusting this — see CHANGELOG / plan.
 */

private const val MILLIS_PER_SECOND = 1000L

fun DailyReadinessEntity.toDto(): DailyReadinessDto = DailyReadinessDto(
    dateTimestamp = dateTimestamp / MILLIS_PER_SECOND,           // ms -> s
    tz = tz,
    sleepCode = sleepCode,
    hrvCode = hrvCode,
    physicalLoadCode = physicalLoadCode,
    workCode = workCode,
    alcoholCode = alcoholCode,
    nutritionCode = nutritionCode,
    cnsDrain = cnsDrain,
    bodyDrain = bodyDrain,
    // Server wants a JSON-encoded array STRING; an empty local value is "[]".
    drainTags = drainTags.ifBlank { "[]" }
)

fun ReadinessRecordDto.toCacheEntity(fetchedAtMs: Long): ComputedScoreCacheEntity =
    ComputedScoreCacheEntity(
        dateTimestamp = dateTimestamp * MILLIS_PER_SECOND,        // s -> ms
        algorithmVersion = computed.algorithmVersion,
        readinessScore = computed.readinessScore,
        dCode = computed.dCode,
        totalCns = computed.totalCns,
        totalBody = computed.totalBody,
        overloadTriggered = computed.overloadTriggered,
        fetchedAt = fetchedAtMs
    )

/** Server error rows carry epoch SECONDS; convert to local ms for matching. */
fun Long.serverSecondsToLocalMs(): Long = this * MILLIS_PER_SECOND
