package com.trybsportowy.sync.api.dto

import com.google.gson.annotations.SerializedName

/** GET /api/android/health — public, no auth (CLAUDE.md §2.2). */
data class HealthDto(
    @SerializedName("status") val status: String,
    @SerializedName("uptime_s") val uptimeS: Long = 0,
    @SerializedName("version") val version: String = ""
)
