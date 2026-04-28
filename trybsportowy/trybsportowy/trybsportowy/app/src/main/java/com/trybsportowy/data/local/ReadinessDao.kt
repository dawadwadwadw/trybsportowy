package com.trybsportowy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReadinessDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyReadiness(entity: DailyReadinessEntity)

    @Query("SELECT * FROM daily_readiness WHERE dateTimestamp >= :fromTimestamp ORDER BY dateTimestamp DESC")
    suspend fun getReadinessSince(fromTimestamp: Long): List<DailyReadinessEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecaySettings(settings: DecaySettingsEntity)

    @Query("SELECT * FROM decay_settings WHERE id = 1")
    suspend fun getDecaySettings(): DecaySettingsEntity?

    @Insert
    suspend fun insertChatMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE dateTimestamp = :dayTimestamp ORDER BY timestamp ASC")
    suspend fun getChatHistory(dayTimestamp: Long): List<ChatMessageEntity>


}