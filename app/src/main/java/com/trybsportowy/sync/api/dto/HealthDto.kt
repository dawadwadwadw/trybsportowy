package com.trybsportowy.sync.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** GET /api/android/health — public, no auth (CLAUDE.md §2.2). */
@JsonClass(generateAdapter = true)
data class HealthDto(
    @Json(name = "status") val status: String = "ok",
    @Json(name = "uptime_s") val uptimeS: Long = 0,
    @Json(name = "version") val version: String = ""
)
