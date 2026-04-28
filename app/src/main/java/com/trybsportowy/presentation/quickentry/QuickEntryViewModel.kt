package com.trybsportowy.presentation.quickentry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.data.remote.Message
import com.trybsportowy.data.repository.AiRepositoryImpl
import com.trybsportowy.domain.repository.ReadinessRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class QuickEntryViewModel(
    private val repository: ReadinessRepository,
    private val initialDate: Long
) : ViewModel() {

    var sleep by mutableStateOf("S0")
    var hrv by mutableStateOf("H0")
    var stress by mutableStateOf("L")
    var work by mutableStateOf("W0")
    var alcohol by mutableStateOf("A0")
    var physical by mutableStateOf("P0")

    var journalText by mutableStateOf("")
    var isAiLoading by mutableStateOf(false)
    var headerTitle by mutableStateOf("Ładowanie...")

    private val normalizedTimestamp: Long

    init {
        normalizedTimestamp = Instant.ofEpochMilli(initialDate)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        setupHeader()
        loadExistingData()
    }

    private fun setupHeader() {
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (normalizedTimestamp == today) {
            headerTitle = "Dodaj dzisiejszą gotowość"
        } else {
            val dateStr = Instant.ofEpochMilli(normalizedTimestamp)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            headerTitle = "Edytuj dzień: $dateStr"
        }
    }

    private fun loadExistingData() {
        viewModelScope.launch {
            val history = repository.getReadinessSince(normalizedTimestamp)
            val existingEntry = history.find { it.dateTimestamp == normalizedTimestamp }
            if (existingEntry != null) {
                sleep = existingEntry.sleepCode
                hrv = existingEntry.hrvCode
                stress = existingEntry.stressCode
                work = existingEntry.workCode
                alcohol = existingEntry.alcoholCode
                physical = existingEntry.physicalLoadCode
            }
        }
    }

    suspend fun evaluateStressWithAI(journalText: String) {
        if (journalText.isBlank()) return
        isAiLoading = true
        try {
            val systemPrompt = "Jesteś analizatorem stresu w sporcie. Przeczytaj tekst użytkownika i oceń obciążenie jego organizmu. Zwróć TYLKO JEDNĄ LITERĘ i nic więcej, żadnego lania wody. Legenda: L (dzień chillowy/normalny), M (lekki stres, małe błędy w diecie, 1 fakt negatywny), H (duży drenaż, np. kłótnia + śmieciowe jedzenie + odwodnienie), X (ekstremalny kryzys, choroba, zapaść systemu)."

            val aiRepository = AiRepositoryImpl()

            val conversationHistory = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = journalText)
            )

            val resultText = aiRepository.sendMessage(
                conversationHistory = conversationHistory,
                modelId = "gpt-4o-mini"
            )

            val finalLetter = resultText.trim().uppercase().take(1)
            if (finalLetter in listOf("L", "M", "H", "X")) {
                stress = finalLetter
            } else {
                android.util.Log.e("STRESS_AI", "AI wypluło śmieci: $resultText")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("STRESS_AI", "Błąd oceniania stresu: ${e.message}")
        } finally {
            isAiLoading = false
        }
    }

    fun saveEntry(onSaved: () -> Unit) {
        viewModelScope.launch {
            val entity = DailyReadinessEntity(
                dateTimestamp = normalizedTimestamp,
                sleepCode = sleep,
                hrvCode = hrv,
                stressCode = stress,
                workCode = work,
                alcoholCode = alcohol,
                physicalLoadCode = physical
            )
            repository.saveDailyReadiness(entity)
            onSaved()
        }
    }
}