package com.trybsportowy.domain.repository

import com.trybsportowy.data.local.ChatMessageEntity
import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.data.local.DecaySettingsEntity

interface ReadinessRepository {
    suspend fun saveDailyReadiness(entity: DailyReadinessEntity)
    suspend fun getReadinessSince(fromTimestamp: Long): List<DailyReadinessEntity>
    suspend fun getDecaySettings(): DecaySettingsEntity
    suspend fun saveDecaySettings(settings: DecaySettingsEntity)

    suspend fun saveChatMessage(message: ChatMessageEntity)
    suspend fun getChatHistory(dayTimestamp: Long): List<ChatMessageEntity>

}