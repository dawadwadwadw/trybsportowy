package com.trybsportowy.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trybsportowy.data.local.AppDatabase
import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.settings.SecretsStore
import com.trybsportowy.sync.api.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CLAUDE.md §10.7 — SyncRepository scenarios with in-memory Room + MockWebServer
 * + the real Retrofit/Moshi/AuthInterceptor stack.
 */
@RunWith(AndroidJUnit4::class)
class SyncRepositoryTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var secrets: SecretsStore
    private lateinit var repo: SyncRepository

    private val readinessJson = """
        [{"date_timestamp":1747094,"local_date":"2025-05-13","tz":"Europe/Warsaw",
          "raw":{"sleep_code":"S2","hrv_code":"H2","physical_load_code":"P2",
                 "work_code":"W0","alcohol_code":"A0","nutrition_code":"N1",
                 "cns_drain":3,"body_drain":1,"drain_tags":["DOMS"]},
          "computed":{"algorithm_version":"v1","readiness_score":42.5,"d_code":4.0,
                      "total_cns":3.0,"total_body":1.0,"overload_triggered":false}}]
    """.trimIndent()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        server = MockWebServer().also { it.start() }
        secrets = SecretsStore(ctx).apply {
            saveSecret("test-secret-123")
            saveServerUrl(server.url("/").toString())
            clearSecretInvalid()
        }
        repo = SyncRepository(
            readinessRepo = SyncReadinessRepository(db.readinessDao),
            syncAttemptDao = db.syncAttemptDao,
            cacheDao = db.computedScoreCacheDao,
            api = NetworkModule.createApi(secrets, server.url("/").toString()),
            secrets = secrets
        )
    }

    @After fun tearDown() {
        db.close()
        server.shutdown()
        secrets.clearSecret()
        secrets.clearSecretInvalid()
    }

    private fun seedPending(vararg tsMillis: Long) = runBlocking {
        tsMillis.forEach { db.readinessDao.insertDailyReadiness(DailyReadinessEntity(dateTimestamp = it)) }
    }

    @Test fun empty_pending_does_not_post_sync() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))   // the best-effort readBack GET
        val outcome = repo.syncOnce()
        assertEquals(SyncOutcome.NothingToPush, outcome)
        // No /sync POST was made.
        var sawSync = false
        repeat(server.requestCount) {
            val r = server.takeRequest()
            if (r.path?.contains("/api/android/sync") == true) sawSync = true
        }
        assertEquals(false, sawSync)
    }

    @Test fun success_marks_synced_and_populates_cache() = runBlocking {
        seedPending(1_000L, 2_000L)
        server.enqueue(MockResponse().setBody("""{"synced":2,"errors":[]}"""))
        server.enqueue(MockResponse().setBody(readinessJson))

        val outcome = repo.syncOnce()

        assertTrue(outcome is SyncOutcome.Pushed)
        assertEquals(2, db.readinessDao.getBySyncState("SYNCED").size)
        assertEquals(0, db.readinessDao.getBySyncState("PENDING").size)
        assertEquals("success", db.syncAttemptDao.mostRecent()!!.status)
        val cached = db.computedScoreCacheDao.getMostRecent()
        assertNotNull(cached)
        assertEquals(1747094L * 1000L, cached!!.dateTimestamp)   // s -> ms boundary
    }

    @Test fun auth_failure_reverts_to_pending_and_flags_secret() = runBlocking {
        seedPending(1_000L)
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))

        val outcome = repo.syncOnce()

        assertEquals(SyncOutcome.AuthFailed, outcome)
        assertEquals(1, db.readinessDao.getBySyncState("PENDING").size)
        assertEquals(0, db.readinessDao.getBySyncState("FAILED").size)
        assertTrue(secrets.isSecretInvalid())
    }

    @Test fun network_error_reverts_to_pending_attempt_stays_open() = runBlocking {
        seedPending(1_000L)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val outcome = repo.syncOnce()

        assertTrue(outcome is SyncOutcome.RetryableFailure)
        assertEquals(1, db.readinessDao.getBySyncState("PENDING").size)
        assertEquals("pending", db.syncAttemptDao.mostRecent()!!.status)
    }

    @Test fun idempotency_key_is_reused_across_retries() = runBlocking {
        seedPending(1_000L, 2_000L)
        // First attempt: server 503 -> RetryableFailure, attempt stays open.
        server.enqueue(MockResponse().setResponseCode(503))
        // Second attempt: success + readBack.
        server.enqueue(MockResponse().setBody("""{"synced":2,"errors":[]}"""))
        server.enqueue(MockResponse().setBody("[]"))

        repo.syncOnce()
        repo.syncOnce()

        val req1 = server.takeRequest()
        val req2 = server.takeRequest()
        assertEquals("/api/android/sync", req1.path)
        assertEquals("/api/android/sync", req2.path)
        assertEquals(
            req1.getHeader("Idempotency-Key"),
            req2.getHeader("Idempotency-Key")
        )
        assertEquals(1, db.syncAttemptDao.count())   // one logical attempt
        assertEquals(2, db.readinessDao.getBySyncState("SYNCED").size)
    }
}
