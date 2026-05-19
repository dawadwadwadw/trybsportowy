package com.trybsportowy.sync

import com.trybsportowy.data.local.ComputedScoreCacheDao
import com.trybsportowy.data.local.SyncAttemptDao
import com.trybsportowy.data.local.SyncAttemptEntity
import com.trybsportowy.settings.SecretsStore
import com.trybsportowy.sync.api.ServerApi
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Outbound sync orchestration (CLAUDE.md §10.3, §4.4, §1.9).
 *
 * Best-effort and never destructive: any failure reverts rows to PENDING (or,
 * for a per-row 207 validation error, FAILED with the message). A 401 marks
 * the secret invalid and leaves the data untouched. Idempotency-Key is reused
 * across retries of an identical payload.
 */
class SyncRepository(
    private val readinessRepo: SyncReadinessRepository,
    private val syncAttemptDao: SyncAttemptDao,
    private val cacheDao: ComputedScoreCacheDao,
    private val api: ServerApi,
    private val secrets: SecretsStore,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun syncOnce(): SyncOutcome {
        val pending = readinessRepo.pendingRows()
        if (pending.isEmpty()) {
            readBackBestEffort()           // keep the dashboard fresh anyway
            gcOldAttempts()
            return SyncOutcome.NothingToPush
        }

        val ids = pending.map { it.dateTimestamp }
        val dtos = pending.map { it.toDto() }
        val payloadHash = sha256Hex(canonicalize(dtos))

        // Reuse the open attempt for an identical payload (§4.4 step 6).
        val existing = syncAttemptDao.findOpenByPayloadHash(payloadHash)
        val key = existing?.idempotencyKey ?: UUID.randomUUID().toString()
        val attemptId = existing?.id ?: syncAttemptDao.insert(
            SyncAttemptEntity(
                idempotencyKey = key,
                payloadHash = payloadHash,
                createdAt = now(),
                status = "pending",
                rowDateTimestamps = ids.joinToString(",")
            )
        )

        readinessRepo.markSyncing(ids)

        return try {
            val response = api.sync(key, dtos)
            when {
                response.code() == 401 -> {
                    secrets.markSecretInvalid()
                    syncAttemptDao.markStatus(attemptId, "auth_failed")
                    readinessRepo.markPending(ids, "401 Unauthorized")
                    SyncOutcome.AuthFailed
                }

                response.isSuccessful -> {
                    val body = response.body()
                    val errors = body?.errors.orEmpty()
                    // Server error rows carry epoch SECONDS; pending ids are ms.
                    val failedIds = errors.mapNotNull { it.dateTimestamp }
                        .map { it.serverSecondsToLocalMs() }
                        .filter { it in ids }
                        .toSet()
                    val syncedIds = ids.filter { it !in failedIds }

                    if (syncedIds.isNotEmpty()) readinessRepo.markSynced(syncedIds)
                    if (failedIds.isNotEmpty()) {
                        readinessRepo.markFailed(
                            failedIds.toList(),
                            errors.joinToString(";") { it.describe() }.take(500)
                        )
                    }
                    secrets.clearSecretInvalid()
                    syncAttemptDao.markStatus(
                        attemptId,
                        "success",
                        "synced=${body?.synced ?: syncedIds.size} errors=${errors.size}"
                            .take(1024)
                    )
                    readBackBestEffort()
                    SyncOutcome.Pushed(syncedIds.size, failedIds.size)
                }

                else -> {
                    readinessRepo.markPending(ids, "HTTP ${response.code()}")
                    SyncOutcome.RetryableFailure("HTTP ${response.code()}")
                }
            }
        } catch (e: IOException) {
            // Network flake: leave attempt 'pending' so the SAME key is reused.
            readinessRepo.markPending(ids, e.message ?: "network")
            SyncOutcome.RetryableFailure(e.message ?: "network")
        } catch (e: Exception) {
            readinessRepo.markPending(ids, e.message ?: e.javaClass.simpleName)
            SyncOutcome.RetryableFailure(e.message ?: e.javaClass.simpleName)
        } finally {
            gcOldAttempts()
        }
    }

    /**
     * After any successful sync (and on idle), pull the server's computed
     * scores for the recent window into the cache. §2.3: a synced day can
     * change the server's view of the next 3 days too.
     */
    private suspend fun readBackBestEffort() {
        runCatching {
            val records = api.getReadiness(days = 28)
            cacheDao.replaceAll(records.map { it.toCacheEntity(now()) })
        }
    }

    private suspend fun gcOldAttempts() {
        runCatching {
            syncAttemptDao.deleteOlderThan(now() - TimeUnit.DAYS.toMillis(7))
        }
    }
}
