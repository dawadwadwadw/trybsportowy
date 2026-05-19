package com.trybsportowy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SyncAttemptDao {

    @Insert
    suspend fun insert(attempt: SyncAttemptEntity): Long

    /**
     * The most recent still-open attempt for an identical payload, used to
     * reuse the same Idempotency-Key on retry (CLAUDE.md §4.4 step 6).
     */
    @Query(
        "SELECT * FROM sync_attempt WHERE payloadHash = :payloadHash " +
            "AND status = 'pending' ORDER BY id DESC LIMIT 1"
    )
    suspend fun findOpenByPayloadHash(payloadHash: String): SyncAttemptEntity?

    @Query("UPDATE sync_attempt SET status = :status, responseSummary = :responseSummary WHERE id = :id")
    suspend fun markStatus(id: Long, status: String, responseSummary: String? = null)

    /** GC: success/auth_failed attempts older than the cutoff (CLAUDE.md §4.4). */
    @Query(
        "DELETE FROM sync_attempt WHERE createdAt < :cutoffEpochMs " +
            "AND status IN ('success', 'auth_failed')"
    )
    suspend fun deleteOlderThan(cutoffEpochMs: Long): Int

    @Query("SELECT * FROM sync_attempt ORDER BY id DESC LIMIT 1")
    suspend fun mostRecent(): SyncAttemptEntity?
}
