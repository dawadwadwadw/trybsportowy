package com.trybsportowy.sync.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * POST /api/android/sync request item (CLAUDE.md §2.2). `drain_tags` is a
 * JSON-encoded array STRING on the wire — the server's choice; keep it that
 * way. Never let this DTO escape the `sync` package (§9.2).
 */
@JsonClass(generateAdapter = true)
data class DailyReadinessDto(
    @Json(name = "date_timestamp") val dateTimestamp: Long,
    @Json(name = "tz") val tz: String,
    @Json(name = "sleep_code") val sleepCode: String,
    @Json(name = "hrv_code") val hrvCode: String,
    @Json(name = "physical_load_code") val physicalLoadCode: String,
    @Json(name = "work_code") val workCode: String,
    @Json(name = "alcohol_code") val alcoholCode: String,
    @Json(name = "nutrition_code") val nutritionCode: String,
    @Json(name = "cns_drain") val cnsDrain: Int,
    @Json(name = "body_drain") val bodyDrain: Int,
    @Json(name = "drain_tags") val drainTags: String
)
