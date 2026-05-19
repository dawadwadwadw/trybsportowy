package com.trybsportowy.data.repository
import com.trybsportowy.data.local.ChatMessageEntity
import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.data.local.DecaySettingsEntity
import com.trybsportowy.data.local.ReadinessDao
import com.trybsportowy.domain.repository.ReadinessRepository
import com.trybsportowy.sync.SyncState

class ReadinessRepositoryImpl(private val dao: ReadinessDao) : ReadinessRepository {
    /**
     * Single entity-write path (§1.5). Every create/edit stamps sync metadata
     * so the row is picked up by the next sync (§4.3, §7.2): updatedAt bumped,
     * syncState reset to PENDING, prior error cleared.
     */
    override suspend fun saveDailyReadiness(entity: DailyReadinessEntity) {
        dao.insertDailyReadiness(
            entity.copy(
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING.name,
                syncError = null
            )
        )
    }
    override suspend fun deleteDailyReadiness(entity: DailyReadinessEntity) {
        dao.deleteDailyReadiness(entity)
    }

    override suspend fun getReadinessSince(fromTimestamp: Long): List<DailyReadinessEntity> {
        return dao.getReadinessSince(fromTimestamp)
    }

    override suspend fun getDecaySettings(): DecaySettingsEntity {
        // Zwraca zapisane ustawienia lub domyślne, jeśli baza jest pusta
        return dao.getDecaySettings() ?: DecaySettingsEntity()
    }

    override suspend fun saveDecaySettings(settings: DecaySettingsEntity) {
        dao.insertDecaySettings(settings)
    }


    override suspend fun saveChatMessage(message: ChatMessageEntity) {
        dao.insertChatMessage(message)
    }
    override suspend fun getChatHistory(dayTimestamp: Long): List<ChatMessageEntity> {
        return dao.getChatHistory(dayTimestamp)
    }
    override suspend fun deleteChatMessage(message: ChatMessageEntity) {
        dao.deleteChatMessage(message)
    }
    override suspend fun clearChatForDay(dayTimestamp: Long) {
        dao.deleteChatForDay(dayTimestamp)
    }

}