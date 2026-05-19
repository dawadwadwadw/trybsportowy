package com.trybsportowy.sync.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * POST /api/android/sync response (CLAUDE.md §2.2).
 *  - 200: {"synced": n, "errors": []}
 *  - 207: {"synced": n, "errors": [{...}]}  (per-record validation failures)
 *
 * The error object shape is not fully pinned by the contract; the fields that
 * matter to the phone (which row failed, why) are modelled nullable and Moshi
 * ignores any extra keys.
 */
@JsonClass(generateAdapter = true)
data class SyncResponseDto(
    @Json(name = "synced") val synced: Int = 0,
    @Json(name = "errors") val errors: List<SyncErrorDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SyncErrorDto(
    @Json(name = "date_timestamp") val dateTimestamp: Long? = null,
    @Json(name = "error") val error: String? = null,
    @Json(name = "message") val message: String? = null
) {
    fun describe(): String = error ?: message ?: "unknown error"
}
