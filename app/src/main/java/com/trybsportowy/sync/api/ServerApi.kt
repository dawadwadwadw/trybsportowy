package com.trybsportowy.sync.api

import com.trybsportowy.sync.api.dto.HealthDto
import retrofit2.http.GET

/**
 * Retrofit interface for /api/android/* (CLAUDE.md §1.10 — the single
 * sanctioned transport; no hand-rolled URL.openConnection anywhere).
 *
 * Phase 3 introduces only the public health endpoint (used by the Settings
 * test-connection button). Phase 4 adds `sync` and `getReadiness` plus the
 * AuthInterceptor and header redaction.
 */
interface ServerApi {

    @GET("api/android/health")
    suspend fun health(): HealthDto
}
