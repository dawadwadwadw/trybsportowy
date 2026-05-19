package com.trybsportowy.sync.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** `computed` block of GET /api/android/readiness (CLAUDE.md §2.2). */
@JsonClass(generateAdapter = true)
data class ComputedDto(
    @Json(name = "algorithm_version") val algorithmVersion: String,
    @Json(name = "readiness_score") val readinessScore: Double,
    @Json(name = "d_code") val dCode: Double,
    @Json(name = "total_cns") val totalCns: Double,
    @Json(name = "total_body") val totalBody: Double,
    @Json(name = "overload_triggered") val overloadTriggered: Boolean
)
