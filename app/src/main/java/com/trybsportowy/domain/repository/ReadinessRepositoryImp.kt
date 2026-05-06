package com.trybsportowy.data.repository
import com.trybsportowy.data.local.ChatMessageEntity
import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.data.local.DecaySettingsEntity
import com.trybsportowy.data.local.ReadinessDao
import com.trybsportowy.domain.repository.ReadinessRepository

class ReadinessRepositoryImpl(private val dao: ReadinessDao) : ReadinessRepository {
    override suspend fun saveDailyReadiness(entity: DailyReadinessEntity) {
        dao.insertDailyReadiness(entity)
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