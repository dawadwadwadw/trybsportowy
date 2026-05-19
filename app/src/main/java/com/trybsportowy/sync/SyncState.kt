package com.trybsportowy.sync

/**
 * Per-row sync lifecycle (CLAUDE.md §4.3). Stored as the enum NAME in
 * DailyReadinessEntity.syncState. Only the repository layer transitions it.
 */
enum class SyncState { PENDING, SYNCING, SYNCED, FAILED }
