# Phase 5 — Sync Engine + WorkManager (procedure)

> Active-phase procedure. Canonical rules live in `/CLAUDE.md`. Do not start this phase until Phase 4 has passed its checkpoint and the user replied `next`.

---

## §10. Phase 5 — Sync Engine + WorkManager

The sync subsystem. `ReadinessRepository` for local writes, `SyncRepository` for outbound sync, `SyncWorker` for background runs, `SyncScheduler` for scheduling.

### §10.1 Dependencies

```kotlin
dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
```

### §10.2 `ReadinessRepository`

The single allowed write path to `DailyReadinessEntity`. Every Composable / ViewModel writes through this.

```kotlin
class ReadinessRepository(
    private val dao: ReadinessDao,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun upsert(entity: DailyReadinessEntity) {
        val withMeta = entity.copy(
            updatedAt = clock.millis(),
            syncState = SyncState.PENDING.name,
            syncError = null
        )
        dao.insert(withMeta)
    }

    fun observeAll(): Flow<List<DailyReadinessEntity>> = dao.observeAll()
    suspend fun pendingRows(): List<DailyReadinessEntity> = dao.getBySyncState(SyncState.PENDING.name)
    suspend fun markSyncing(dateTimestamps: List<Long>) { dao.markSyncState(dateTimestamps, SyncState.SYNCING.name) }
    suspend fun markSynced(dateTimestamps: List<Long>) { dao.markSyncState(dateTimestamps, SyncState.SYNCED.name) }
    suspend fun markPending(dateTimestamps: List<Long>, error: String?) {
        dao.markSyncStateWithError(dateTimestamps, SyncState.PENDING.name, error)
    }
    suspend fun markFailed(dateTimestamps: List<Long>, error: String) {
        dao.markSyncStateWithError(dateTimestamps, SyncState.FAILED.name, error)
    }
}
```

Update existing call sites that previously called the DAO directly to go through this repository instead. Any direct DAO write outside `ReadinessRepository` is a violation of §1.5 and must be fixed.

### §10.3 `SyncRepository`

```kotlin
class SyncRepository(
    private val readinessRepo: ReadinessRepository,
    private val syncAttemptDao: SyncAttemptDao,
    private val cacheDao: ComputedScoreCacheDao,
    private val api: ServerApi,
    private val secrets: SecretsStore,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun syncOnce(): SyncOutcome {
        val pending = readinessRepo.pendingRows()
        if (pending.isEmpty()) {
            // Still pull computed scores so the dashboard stays fresh
            readBack()
            return SyncOutcome.NothingToPush
        }
        val dtos = pending.map { it.toDto() }
        val payloadJson = canonicalize(dtos)
        val payloadHash = sha256(payloadJson)

        // Reuse existing attempt if payload identical and still pending
        val existing = syncAttemptDao.findOpenByPayloadHash(payloadHash)
        val key = existing?.idempotencyKey ?: UUID.randomUUID().toString()
        val attemptId = existing?.id ?: syncAttemptDao.insert(SyncAttemptEntity(
            idempotencyKey = key,
            payloadHash = payloadHash,
            createdAt = clock.millis(),
            status = "pending",
            rowDateTimestamps = pending.joinToString(",") { it.dateTimestamp.toString() }
        ))

        readinessRepo.markSyncing(pending.map { it.dateTimestamp })

        return try {
            val response = api.sync(key, dtos)
            when {
                response.code() == 401 -> {
                    secrets.markSecretInvalid()
                    syncAttemptDao.markStatus(attemptId, "auth_failed")
                    readinessRepo.markPending(pending.map { it.dateTimestamp }, "401 Unauthorized")
                    SyncOutcome.AuthFailed
                }
                response.isSuccessful -> {
                    val body = response.body()
                    val errors = body?.errors.orEmpty()
                    val failedDates = errors.mapNotNull { it.dateTimestamp }.toSet()
                    val syncedDates = pending.map { it.dateTimestamp }.filter { it !in failedDates }
                    readinessRepo.markSynced(syncedDates)
                    if (failedDates.isNotEmpty()) {
                        readinessRepo.markFailed(failedDates.toList(), errors.joinToString(";") { it.message ?: "?" })
                    }
                    secrets.clearSecretInvalid()
                    syncAttemptDao.markStatus(attemptId, "success", responseSummary = body?.summary())
                    readBack()
                    SyncOutcome.Pushed(syncedDates.size, failedDates.size)
                }
                else -> {
                    readinessRepo.markPending(pending.map { it.dateTimestamp }, "HTTP ${response.code()}")
                    SyncOutcome.RetryableFailure("HTTP ${response.code()}")
                }
            }
        } catch (e: IOException) {
            readinessRepo.markPending(pending.map { it.dateTimestamp }, e.message ?: "network")
            SyncOutcome.RetryableFailure(e.message ?: "network")
        } finally {
            gcOldAttempts()
        }
    }

    private suspend fun readBack() {
        val records = api.getReadiness(days = 28)
        cacheDao.replaceAll(records.map { it.toCacheEntity(clock.millis()) })
    }

    private suspend fun gcOldAttempts() {
        val cutoff = clock.millis() - TimeUnit.DAYS.toMillis(7)
        syncAttemptDao.deleteOlderThan(cutoff)
    }
}

sealed class SyncOutcome {
    object NothingToPush : SyncOutcome()
    data class Pushed(val synced: Int, val failed: Int) : SyncOutcome()
    data class RetryableFailure(val reason: String) : SyncOutcome()
    object AuthFailed : SyncOutcome()
}
```

`canonicalize(dtos)` produces sorted-key JSON via Moshi with a sorted-keys adapter. Implement and test it.

### §10.4 `SyncWorker`

```kotlin
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = AppContainer.syncRepository(applicationContext)  // wire via your DI; manual factory is fine
        return when (val outcome = repo.syncOnce()) {
            SyncOutcome.NothingToPush -> Result.success()
            is SyncOutcome.Pushed -> Result.success()
            is SyncOutcome.RetryableFailure -> Result.retry()
            SyncOutcome.AuthFailed -> Result.success()  // don't retry on auth; user must fix the secret
        }
    }
}
```

### §10.5 `SyncScheduler`

```kotlin
object SyncScheduler {
    private const val PERIODIC_NAME = "trybsportowy.sync.periodic"
    private const val ONE_TIME_NAME = "trybsportowy.sync.now"

    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun triggerOnce(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_NAME, ExistingWorkPolicy.REPLACE, request
        )
    }

    fun observeStatus(context: Context): Flow<WorkInfo?> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData(PERIODIC_NAME)
            .asFlow()
            .map { it.firstOrNull() }
}
```

Call `SyncScheduler.schedulePeriodic(this)` in `App.onCreate()`.

### §10.6 Manifest

If the project doesn't already declare WorkManager initialization, leave the default `androidx.startup` initializer alone. If it disables it, you'll need to provide a `Configuration.Provider` from your `App` class — but cross that bridge if you find it.

### §10.7 Tests

`SyncRepositoryTest.kt` (instrumented or local with in-memory Room):
1. Empty pending → `NothingToPush`, no API call.
2. Two pending rows, server returns 200 → both marked `SYNCED`, `sync_attempt` row created with status `success`, `readiness_computed_cache` populated.
3. Two pending rows, server returns 401 → both reverted to `PENDING`, `secret_invalid` flag set.
4. Network IOException → rows reverted to `PENDING`, attempt status remains `pending`.
5. **Idempotency reuse:** trigger `syncOnce()` twice with identical pending rows and a forced first-attempt timeout; assert the second attempt sends the same `Idempotency-Key`.

### §10.8 Checkpoint

```bash
# 1. App compiles, periodic work is scheduled
./gradlew assembleDebug
adb shell dumpsys jobscheduler | grep -A 2 "trybsportowy.sync.periodic" || true
# expect: at least one entry mentioning the work name

# 2. Manual sync end-to-end (real device, real server)
# Steps on device:
#   a. Settings: enter valid URL + secret, save.
#   b. QuickEntry: create or edit a record for today.
#   c. ProDashboard: tap "Synchronizuj teraz" (added in Phase 6) — or kick the worker:
adb shell cmd jobscheduler run -f <pkg> 999  # or use WorkManager test helper
# Then:
adb shell run-as <pkg> sqlite3 \
  /data/data/<pkg>/databases/<dbname> \
  "SELECT dateTimestamp, syncState FROM DailyReadinessEntity WHERE syncState='SYNCED' LIMIT 5"
# expect: at least one row in SYNCED state

# 3. Server confirms receipt
curl -s -H "Authorization: Bearer ${ANDROID_API_SECRET}" \
  "https://aiserver.tail198ba5.ts.net/api/android/readiness?days=7" | jq '.[0]'
# expect: the record(s) you just synced, with computed fields filled

# 4. Cache populated
adb shell run-as <pkg> sqlite3 /data/data/<pkg>/databases/<dbname> \
  "SELECT COUNT(*) FROM readiness_computed_cache"
# expect: > 0

# 5. Idempotency reuse on retry
# Force a flaky path: turn airplane mode on, edit a record (creates PENDING), turn airplane mode off.
# Worker runs, succeeds. Inspect sync_attempt — exactly one row per logical attempt.
adb shell run-as <pkg> sqlite3 /data/data/<pkg>/databases/<dbname> \
  "SELECT idempotencyKey, status, rowDateTimestamps FROM sync_attempt ORDER BY id DESC LIMIT 5"

# 6. 401 path
# In Settings, change the secret to a wrong value, save. Trigger a sync.
# Expect: the row that was edited stays PENDING (not FAILED), settings screen shows the
# "Token jest nieprawidłowy" indicator.

# 7. Tests pass
./gradlew test --tests "*SyncRepositoryTest*"
```

Append to `CHANGELOG.md`. Wait for `next`.

---
