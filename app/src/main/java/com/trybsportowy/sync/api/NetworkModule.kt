package com.trybsportowy.sync.api

import com.trybsportowy.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds [ServerApi] instances. Supports on-the-fly creation with un-saved
 * inputs so the Settings test-connection button can validate a URL/secret
 * before they are committed (CLAUDE.md §8.3).
 *
 * The Authorization header is redacted from all logging output regardless of
 * level (§4.6); the AuthInterceptor itself is added in Phase 4.
 */
object NetworkModule {

    private fun loggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
            redactHeader("Authorization")
            redactHeader("Idempotency-Key")
        }

    private fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    /**
     * @param secret reserved for Phase 4 (AuthInterceptor). Phase 3 only calls
     *               the public health endpoint, which needs no auth.
     */
    fun create(baseUrl: String, secret: String? = null): ServerApi {
        val client = OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor())
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ServerApi::class.java)
    }
}
