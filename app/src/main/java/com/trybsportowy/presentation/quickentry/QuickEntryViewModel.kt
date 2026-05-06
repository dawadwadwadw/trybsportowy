package com.trybsportowy.presentation.quickentry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.data.remote.Message
import com.trybsportowy.data.repository.AiRepositoryImpl
import com.trybsportowy.domain.repository.ReadinessRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ─── Definicje tagów ────────────────────────────────────────────────────────

data class DrainTag(
    val id: String,
    val emoji: String,
    val label: String,
    val points: Int,
    val tooltip: String
)

val CNS_TAGS = listOf(
    DrainTag("conflict",  "😤", "Kłótnia / konflikt",         +4, "Ostre zdarzenie emocjonalne mocno aktywuje oś HPA i zalewa ciało kortyzolem. Regeneracja po takim dniu jest znacznie płytsza niż po spokojnym."),
    DrainTag("deadline",  "💼", "Deadline / presja",           +3, "Przedłużone napięcie poznawcze drenuje te same zasoby nerwowe co intensywny trening techniczny. Mózg nie odróżnia stresu pracy od stresu zawodów."),
    DrainTag("learning",  "📚", "Intensywna nauka",            +3, "Hipokamp i kora przedczołowa zużywają glukozę i neuroprzekaźniki intensywniej niż podczas odpoczynku. Po długiej nauce koordynacja i czas reakcji są gorsze."),
    DrainTag("traffic",   "🚗", "Korki / frustracja",          +2, "Nawet pasywny stres w korkach podnosi kortyzol i adrenalinę mierzalnie. Kumuluje się niepostrzeżenie przez cały dzień."),
    DrainTag("screen",    "📱", "Zbyt dużo ekranu",            +2, "Ciągła stymulacja dopaminergiczna przez telefon utrzymuje CNS w trybie czuwania zamiast odpoczynku. Zaburza jakość snu nawet przy odpowiedniej długości."),
    DrainTag("relax",     "🧘", "Pełny relaks / medytacja",    -2, "Aktywna dominacja przywspółczulna — ciało przełącza się w tryb naprawy. Nawet 20 minut dziennie mierzalnie obniża kortyzol.")
)

val BODY_TAGS = listOf(
    DrainTag("dehydration", "💧", "Odwodnienie",                       +3, "Już 2% odwodnienia obniża wydolność o 20% i zwalnia syntezę białek mięśniowych. Krew gęstnieje, transport składników odżywczych do mięśni spada drastycznie."),
    DrainTag("pain",        "🦵", "Ból stawów / mięśni",              +3, "Aktywny stan zapalny w tkankach oznacza że organizm już walczy na dwóch frontach. Dokładanie treningu do stanu zapalnego spowalnia gojenie zamiast je przyspieszać."),
    DrainTag("sick",        "🤒", "Choroba / przeziębienie",           +5, "Układ odpornościowy w trybie walki pochłania zasoby które normalnie idą na regenerację mięśni. Trening podczas choroby może przedłużyć infekcję o kilka dni."),
    DrainTag("sitting",     "🪑", "Długie siedzenie / brak ruchu",     +2, "Unieruchomienie powięzi przez wiele godzin tworzy napięcia strukturalne widoczne następnego dnia. Kręgosłup i biodra reagują bólem na nagłe obciążenie."),
    DrainTag("weather",     "🌧️", "Ciężkie warunki zewnętrzne",       +2, "Praca lub aktywność w zimnie, deszczu lub upale to dodatkowy stres termoregulacyjny. Ciało spala zasoby na utrzymanie temperatury zamiast na regenerację."),
    DrainTag("recovery",    "🧊", "Regeneracja aktywna",               -2, "Aktywna stymulacja układu naczyniowego przyspiesza usuwanie metabolitów z mięśni. Regularnie stosowana skraca czas między treningami nawet o 30%.")
)

// ─── ViewModel ──────────────────────────────────────────────────────────────

class QuickEntryViewModel(
    private val repository: ReadinessRepository,
    private val initialDate: Long
) : ViewModel() {

    // Strona Pagera (0 = kody główne, 1 = tagi stresu)
    var currentPage by mutableIntStateOf(0)
        private set

    // ─── Kody główne (Krok 1) ────────────────────────────────────────────────
    var sleep    by mutableStateOf("S0")
    var hrv      by mutableStateOf("H0")
    var stress   by mutableStateOf("L")
    var work     by mutableStateOf("W0")
    var alcohol  by mutableStateOf("A0")
    var physical by mutableStateOf("P0")
    var nutrition by mutableStateOf("N1")

    // ─── Tagi stresu (Krok 2) ────────────────────────────────────────────────
    val selectedCnsTags  = mutableSetOf<String>()
    val selectedBodyTags = mutableSetOf<String>()

    // Wyliczone sumy punktów (obserwowane przez UI)
    var cnsDrain  by mutableIntStateOf(0)
        private set
    var bodyDrain by mutableIntStateOf(0)
        private set

    // Pomocnicze — trigger rekomposycji po zmianie setów
    var cnsTagsVersion  by mutableIntStateOf(0)
        private set
    var bodyTagsVersion by mutableIntStateOf(0)
        private set

    // Pozostałe
    var journalText  by mutableStateOf("")
    var isAiLoading  by mutableStateOf(false)
    var headerTitle  by mutableStateOf("Ładowanie...")

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

    // ─── Nawigacja Pagera ────────────────────────────────────────────────────

    fun goToPage(page: Int) {
        currentPage = page
    }

    // ─── Toggle tagów ────────────────────────────────────────────────────────

    fun toggleCnsTag(tagId: String, points: Int) {
        if (selectedCnsTags.contains(tagId)) {
            selectedCnsTags.remove(tagId)
        } else {
            selectedCnsTags.add(tagId)
        }
        recalculateDrains()
        cnsTagsVersion++
    }

    fun toggleBodyTag(tagId: String, points: Int) {
        if (selectedBodyTags.contains(tagId)) {
            selectedBodyTags.remove(tagId)
        } else {
            selectedBodyTags.add(tagId)
        }
        recalculateDrains()
        bodyTagsVersion++
    }

    private fun recalculateDrains() {
        cnsDrain = CNS_TAGS
            .filter { selectedCnsTags.contains(it.id) }
            .sumOf { it.points }
            .coerceAtLeast(0)

        bodyDrain = BODY_TAGS
            .filter { selectedBodyTags.contains(it.id) }
            .sumOf { it.points }
            .coerceAtLeast(0)

        // Przelicz kod D (stresCode) na podstawie sumy
        val totalDrain = cnsDrain + bodyDrain
        stress = when {
            totalDrain <= 3  -> "L"
            totalDrain <= 7  -> "M"
            totalDrain <= 13 -> "H"
            else             -> "X"
        }
    }

    // ─── Setup ───────────────────────────────────────────────────────────────

    private fun setupHeader() {
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        headerTitle = if (normalizedTimestamp == today) {
            "Dodaj dzisiejszą gotowość"
        } else {
            val dateStr = Instant.ofEpochMilli(normalizedTimestamp)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            "Edytuj dzień: $dateStr"
        }
    }

    private fun loadExistingData() {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) {
                repository.getReadinessSince(normalizedTimestamp)
            }
            val existing = history.find { it.dateTimestamp == normalizedTimestamp }
            if (existing != null) {
                sleep     = existing.sleepCode
                hrv       = existing.hrvCode
                stress    = existing.stressCode
                work      = existing.workCode
                alcohol   = existing.alcoholCode
                physical  = existing.physicalLoadCode
                nutrition = existing.nutritionCode
                cnsDrain  = existing.cnsDrain
                bodyDrain = existing.bodyDrain
                // Odtwórz zaznaczone tagi z JSON (prosto: lista id oddzielona przecinkami)
                if (existing.drainTags.isNotEmpty()) {
                    val ids = existing.drainTags.split(",").map { it.trim() }
                    ids.forEach { id ->
                        if (CNS_TAGS.any { it.id == id })  selectedCnsTags.add(id)
                        if (BODY_TAGS.any { it.id == id }) selectedBodyTags.add(id)
                    }
                    cnsTagsVersion++
                    bodyTagsVersion++
                }
            }
        }
    }

    // ─── Zapis ───────────────────────────────────────────────────────────────

    fun saveEntry(onSaved: () -> Unit) {
        viewModelScope.launch {
            val allTagIds = (selectedCnsTags + selectedBodyTags).joinToString(",")
            val entity = DailyReadinessEntity(
                dateTimestamp  = normalizedTimestamp,
                sleepCode      = sleep,
                hrvCode        = hrv,
                stressCode     = stress,
                workCode       = work,
                alcoholCode    = alcohol,
                physicalLoadCode = physical,
                nutritionCode  = nutrition,
                cnsDrain       = cnsDrain,
                bodyDrain      = bodyDrain,
                drainTags      = allTagIds
            )
            withContext(Dispatchers.IO) {
                repository.saveDailyReadiness(entity)
            }
            onSaved()
        }
    }

    // ─── (Legacy) Ocena AI ────────────────────────────────────────────────────

    suspend fun evaluateStressWithAI(text: String) {
        if (text.isBlank()) return
        isAiLoading = true
        try {
            val aiRepository = AiRepositoryImpl()
            val resultText = aiRepository.sendMessage(
                conversationHistory = listOf(
                    Message(role = "system", content = "Jesteś analizatorem stresu. Zwróć TYLKO jedną literę: L, M, H lub X."),
                    Message(role = "user", content = text)
                ),
                modelId = "gpt-4o-mini"
            )
            val letter = resultText.trim().uppercase().take(1)
            if (letter in listOf("L", "M", "H", "X")) stress = letter
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isAiLoading = false
        }
    }
}