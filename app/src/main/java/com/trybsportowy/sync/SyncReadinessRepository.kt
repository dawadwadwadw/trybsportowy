package com.trybsportowy.sync

import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.data.local.ReadinessDao
import kotlinx.coroutines.flow.Flow

/**
 * The §10.2 / §1.5 sync write-path over readiness rows. Named
 * SyncReadinessRepository (not just "ReadinessRepository") to avoid colliding
 * with the legacy domain interface of that name; this is the sole authority
 * for syncState transitions — Workers/ViewModels never mutate it directly.
 */
class SyncReadinessRepository(
    private val dao: ReadinessDao,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun upsert(entity: DailyReadinessEntity) {
        dao.insertDailyReadiness(
            entity.copy(
                updatedAt = now(),
                syncState = SyncState.PENDING.name,
                syncError = null
            )
        )
    }

    fun observeAll(): Flow<List<DailyReadinessEntity>> = dao.observeAll()

    suspend fun pendingRows(): List<DailyReadinessEntity> =
        dao.getBySyncState(SyncState.PENDING.name)

    suspend fun markSyncing(ids: List<Long>) =
        dao.markSyncState(ids, SyncState.SYNCING.name)

    suspend fun markSynced(ids: List<Long>) =
        dao.markSyncState(ids, SyncState.SYNCED.name)

    suspend fun markPending(ids: List<Long>, error: String?) =
        dao.markSyncStateWithError(ids, SyncState.PENDING.name, error)

    suspend fun markFailed(ids: List<Long>, error: String) =
        dao.markSyncStateWithError(ids, SyncState.FAILED.name, error)
}
