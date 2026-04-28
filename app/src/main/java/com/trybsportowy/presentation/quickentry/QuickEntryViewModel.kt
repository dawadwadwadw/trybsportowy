package com.trybsportowy.presentation.quickentry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trybsportowy.BuildConfig
import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.data.remote.Content
import com.trybsportowy.data.remote.GeminiApiClient
import com.trybsportowy.data.remote.GeminiRequest
import com.trybsportowy.data.remote.Part
import com.trybsportowy.data.remote.SystemInstruction
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

    // 1. Zmienne stanu
    var sleep by mutableStateOf("S0")
    var hrv by mutableStateOf("H0")
    var stress by mutableStateOf("L")
    var work by mutableStateOf("W0")
    var alcohol by mutableStateOf("A0")
    var physical by mutableStateOf("P0")

    // AI States
    var journalText by mutableStateOf("")
    var isAiLoading by mutableStateOf(false)

    var headerTitle by mutableStateOf("Ładowanie...")

    private val normalizedTimestamp: Long
    private val API_KEY = BuildConfig.GEMINI_API_KEY

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
            val systemPrompt = "Jesteś analizatorem stresu. Przeczytaj tekst użytkownika i oceń obciążenie jego organizmu. Zwróć TYLKO JEDNĄ LITERĘ i nic więcej. Legenda: L (dzień chillowy/normalny), M (lekki stres, małe błędy w diecie, np. 1 fakt negatywny), H (duży drenaż, np. kłótnia + śmieciowe jedzenie + odwodnienie), X (ekstremalny kryzys, choroba, zapaść systemu)."

            val request = GeminiRequest(
                contents = listOf(Content(role = "user", parts = listOf(Part(text = journalText)))),
                systemInstruction = SystemInstruction(parts = listOf(Part(text = systemPrompt)))
            )

            val response = GeminiApiClient.apiService.generateContent(apiKey = API_KEY, request = request)
            val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()?.uppercase()

            if (result != null && result in listOf("L", "M", "H", "X")) {
                stress = result
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
