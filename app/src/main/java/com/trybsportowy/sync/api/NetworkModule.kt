package com.trybsportowy.sync.api

import com.squareup.moshi.Moshi
import com.trybsportowy.BuildConfig
import com.trybsportowy.settings.SecretsStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds [ServerApi] instances (CLAUDE.md §9.6). Supports on-the-fly creation
 * with an un-saved base URL so the Settings test-connection button can validate
 * before the values are committed.
 *
 * The Authorization (and Idempotency-Key) headers are redacted from ALL logging
 * output regardless of level (§4.6); AuthInterceptor injects the Bearer token.
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

    fun createApi(secrets: SecretsStore, baseUrl: String? = null): ServerApi {
        val url = normalizeBaseUrl(
            baseUrl ?: secrets.getServerUrl() ?: SecretsStore.DEFAULT_SERVER_URL
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(secrets))
            .addInterceptor(loggingInterceptor())
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        // Codegen adapters (@JsonClass) auto-register; no reflective factory.
        val moshi = Moshi.Builder().build()

        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ServerApi::class.java)
    }
}
