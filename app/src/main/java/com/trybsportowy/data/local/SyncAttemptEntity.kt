package com.trybsportowy.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per logical sync attempt (CLAUDE.md §4.4). The idempotency key is
 * generated once and reused on every retry of the same payload so a flaky
 * network can never double-write server-side.
 *
 * Indices are declared here so Room's exported schema (v4) includes them and
 * the hand-written Migration_3_4 creates indices with matching names.
 */
@Entity(
    tableName = "sync_attempt",
    indices = [
        Index(value = ["idempotencyKey"], name = "index_sync_attempt_idempotencyKey"),
        Index(value = ["payloadHash"], name = "index_sync_attempt_payloadHash"),
        Index(value = ["createdAt"], name = "index_sync_attempt_createdAt")
    ]
)
data class SyncAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val idempotencyKey: String,           // UUIDv4
    val payloadHash: String,              // SHA-256 hex lowercase of canonical payload
    val createdAt: Long,                  // UTC epoch ms
    val status: String,                   // 'pending' | 'success' | 'auth_failed' | 'failed'
    val rowDateTimestamps: String,        // CSV of included dateTimestamps
    val responseSummary: String? = null   // {synced,errors} JSON, truncated to 1KB
)
