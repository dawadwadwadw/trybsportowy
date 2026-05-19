package com.trybsportowy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadinessDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyReadiness(entity: DailyReadinessEntity)

    @Delete
    suspend fun deleteDailyReadiness(entity: DailyReadinessEntity)

    @Query("SELECT * FROM daily_readiness WHERE dateTimestamp >= :fromTimestamp ORDER BY dateTimestamp DESC")
    suspend fun getReadinessSince(fromTimestamp: Long): List<DailyReadinessEntity>

    // ─── Sync (Phase 5) — query-only, no schema change ───────────────
    @Query("SELECT * FROM daily_readiness ORDER BY dateTimestamp DESC")
    fun observeAll(): Flow<List<DailyReadinessEntity>>

    @Query("SELECT * FROM daily_readiness WHERE syncState = :state ORDER BY dateTimestamp ASC")
    suspend fun getBySyncState(state: String): List<DailyReadinessEntity>

    @Query("UPDATE daily_readiness SET syncState = :state WHERE dateTimestamp IN (:ids)")
    suspend fun markSyncState(ids: List<Long>, state: String)

    @Query("UPDATE daily_readiness SET syncState = :state, syncError = :error WHERE dateTimestamp IN (:ids)")
    suspend fun markSyncStateWithError(ids: List<Long>, state: String, error: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecaySettings(settings: DecaySettingsEntity)

    @Query("SELECT * FROM decay_settings WHERE id = 1")
    suspend fun getDecaySettings(): DecaySettingsEntity?

    @Insert
    suspend fun insertChatMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE dateTimestamp = :dayTimestamp ORDER BY timestamp ASC")
    suspend fun getChatHistory(dayTimestamp: Long): List<ChatMessageEntity>

    @Delete
    suspend fun deleteChatMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE dateTimestamp = :dayTimestamp")
    suspend fun deleteChatForDay(dayTimestamp: Long)

}