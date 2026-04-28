package com.trybsportowy.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.data.local.DecaySettingsEntity
import com.trybsportowy.domain.repository.ReadinessRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: ReadinessRepository) : ViewModel() {
    private val _settings = MutableStateFlow(DecaySettingsEntity())
    val settings = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            _settings.value = repository.getDecaySettings()
        }
    }

    fun updateSettings(newSettings: DecaySettingsEntity) {
        _settings.value = newSettings
        viewModelScope.launch {
            repository.saveDecaySettings(newSettings)
        }
    }

    suspend fun getAllHistory(): List<DailyReadinessEntity> {
        return repository.getReadinessSince(0)
    }

    suspend fun importData(data: List<DailyReadinessEntity>) {
        data.forEach { entity ->
            repository.saveDailyReadiness(entity)
        }
    }
}
