package com.trybsportowy.sync.api

import com.trybsportowy.sync.api.dto.DailyReadinessDto
import com.trybsportowy.sync.api.dto.HealthDto
import com.trybsportowy.sync.api.dto.ReadinessRecordDto
import com.trybsportowy.sync.api.dto.SyncResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for /api/android/* (CLAUDE.md §1.10 — the single
 * sanctioned transport; no hand-rolled URL.openConnection anywhere).
 *
 * `sync` returns Response<T> so the caller can distinguish 200 vs 207 vs 401
 * (§2.2). The others throw HttpException on non-2xx, which the repository
 * treats as retryable.
 */
interface ServerApi {

    @POST("api/android/sync")
    suspend fun sync(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body records: List<DailyReadinessDto>
    ): Response<SyncResponseDto>

    @GET("api/android/readiness")
    suspend fun getReadiness(@Query("days") days: Int): List<ReadinessRecordDto>

    @GET("api/android/health")
    suspend fun health(): HealthDto
}
