package com.trybsportowy.sync.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Read-only seam over the Bearer secret so AuthInterceptor is unit-testable
 * without Android's EncryptedSharedPreferences. SecretsStore implements this;
 * it remains the sole real accessor (CLAUDE.md §4.6).
 */
interface SecretProvider {
    fun getSecret(): String?
}

/**
 * Adds `Authorization: Bearer <secret>` to every outbound request iff a secret
 * is set (CLAUDE.md §2.1, §4.6). No secret -> request proceeds unchanged (the
 * public /health endpoint needs none; an authed endpoint will then 401, which
 * the repository handles per §1.9).
 */
class AuthInterceptor(private val secrets: SecretProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val secret = secrets.getSecret()
        if (secret.isNullOrBlank()) return chain.proceed(original)
        val authed = original.newBuilder()
            .addHeader("Authorization", "Bearer $secret")
            .build()
        return chain.proceed(authed)
    }
}
