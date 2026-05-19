package com.trybsportowy.sync.api

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * CLAUDE.md §4.6 / §9.8:
 *  1. With a saved secret, every request carries Authorization: Bearer <secret>.
 *  2. Logging output redacts the Authorization value (no raw "Bearer <token>").
 *  3. With no secret, no Authorization header is sent.
 */
class AuthInterceptorTest {

    private lateinit var server: MockWebServer
    private val logs = mutableListOf<String>()
    private val secret = "s3cr3t-AUTH-TOKEN-1234567890"

    private class FakeSecret(private val value: String?) : SecretProvider {
        override fun getSecret(): String? = value
    }

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
        logs.clear()
    }

    private fun api(provider: SecretProvider): ServerApi {
        val logging = HttpLoggingInterceptor { logs.add(it) }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
            redactHeader("Authorization")
            redactHeader("Idempotency-Key")
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(provider))
            .addInterceptor(logging)
            .build()
        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(ServerApi::class.java)
    }

    @Test fun request_carries_bearer_when_secret_present() {
        server.enqueue(MockResponse().setBody("""{"status":"ok","version":"abc"}"""))
        runBlockingTest { api(FakeSecret(secret)).health() }

        val recorded = server.takeRequest()
        assertEquals("Bearer $secret", recorded.getHeader("Authorization"))
    }

    @Test fun logging_redacts_the_secret() {
        server.enqueue(MockResponse().setBody("""{"status":"ok","version":"abc"}"""))
        runBlockingTest { api(FakeSecret(secret)).health() }

        val all = logs.joinToString("\n")
        assertFalse("secret leaked into logs:\n$all", all.contains(secret))
        assertTrue("expected redaction marker in logs:\n$all", all.contains("██"))
    }

    @Test fun no_authorization_header_when_secret_absent() {
        server.enqueue(MockResponse().setBody("""{"status":"ok","version":"abc"}"""))
        runBlockingTest { api(FakeSecret(null)).health() }

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    private fun runBlockingTest(block: suspend () -> Unit) =
        kotlinx.coroutines.runBlocking { block() }
}
