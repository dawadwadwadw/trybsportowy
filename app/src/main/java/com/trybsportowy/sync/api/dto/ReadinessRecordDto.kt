package com.trybsportowy.sync.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * GET /api/android/readiness item (CLAUDE.md §2.2), newest first.
 *
 * NB: on this READ path `raw.drain_tags` is a JSON ARRAY (["DOMS"]), unlike
 * the POST path where `drain_tags` is a JSON-encoded STRING. The asymmetry is
 * the server's contract — modelled faithfully here.
 */
@JsonClass(generateAdapter = true)
data class ReadinessRecordDto(
    @Json(name = "date_timestamp") val dateTimestamp: Long,
    @Json(name = "local_date") val localDate: String,
    @Json(name = "tz") val tz: String,
    @Json(name = "raw") val raw: RawReadinessDto,
    @Json(name = "computed") val computed: ComputedDto
)

@JsonClass(generateAdapter = true)
data class RawReadinessDto(
    @Json(name = "sleep_code") val sleepCode: String,
    @Json(name = "hrv_code") val hrvCode: String,
    @Json(name = "physical_load_code") val physicalLoadCode: String,
    @Json(name = "work_code") val workCode: String,
    @Json(name = "alcohol_code") val alcoholCode: String,
    @Json(name = "nutrition_code") val nutritionCode: String,
    @Json(name = "cns_drain") val cnsDrain: Int,
    @Json(name = "body_drain") val bodyDrain: Int,
    @Json(name = "drain_tags") val drainTags: List<String> = emptyList()
)
