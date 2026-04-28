package com.trybsportowy.presentation.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trybsportowy.data.local.ChatMessageEntity
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

class ChatViewModel(
    private val repository: ReadinessRepository,
    private val aiRepository: AiRepositoryImpl = AiRepositoryImpl()
) : ViewModel() {

    val messages = mutableStateListOf<ChatMessageEntity>()

    // Flaga "trener pisze" — używamy delegacji `by` dla czystości kodu Compose
    var isTyping by mutableStateOf(false)
        private set

    // Opcjonalna wiadomość błędu widoczna dla UI (nie zapisywana w Room)
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val todayTimestamp: Long = LocalDate.now()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    init {
        loadTodayChat()
    }

    // ─── Wczytaj historię dnia z bazy ────────────────────────────────────────

    private fun loadTodayChat() {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) {
                repository.getChatHistory(todayTimestamp)
            }
            messages.clear()
            messages.addAll(history)
        }
    }

    // ─── Wysyłanie wiadomości ─────────────────────────────────────────────────

    fun sendMessage(text: String) {
        if (text.isBlank() || isTyping) return
        errorMessage = null

        viewModelScope.launch {
            try {
                // 1. Utwórz i zapisz wiadomość użytkownika (I/O)
                val userMsg = ChatMessageEntity(
                    dateTimestamp = todayTimestamp,
                    timestamp = System.currentTimeMillis(),
                    role = "user",
                    content = text
                )
                withContext(Dispatchers.IO) {
                    repository.saveChatMessage(userMsg)
                }
                // Aktualizacja UI wyłącznie na Main Thread
                messages.add(userMsg)
                isTyping = true

                // 2. Pobierz 7 dni danych fizjologicznych (I/O) — kontrola tokenów
                val sevenDaysAgo = todayTimestamp - (6 * 86_400_000L)
                val recentData = withContext(Dispatchers.IO) {
                    repository.getReadinessSince(sevenDaysAgo)
                }

                // 3. Buduj kontekst 7-dniowy
                val contextString = buildString {
                    appendLine("=== DANE FIZJOLOGICZNE (7 DNI) ===")
                    for (i in 0..6) {
                        val dayMillis = todayTimestamp - (i * 86_400_000L)
                        val dayData = recentData.find { it.dateTimestamp == dayMillis }
                        val label = when (i) {
                            0 -> "Dzisiaj   "
                            1 -> "Wczoraj   "
                            else -> "${i} dni temu".take(10)
                        }
                        val date = Instant.ofEpochMilli(dayMillis)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("dd.MM"))

                        if (dayData != null) {
                            appendLine(
                                "$label ($date): " +
                                "HRV=${dayData.hrvCode} | " +
                                "Sen=${dayData.sleepCode} | " +
                                "Obciąż.fiz=${dayData.physicalLoadCode} | " +
                                "Stres.psych=${dayData.workCode} | " +
                                "Alkohol=${dayData.alcoholCode} | " +
                                "Stres.subiekty=${dayData.stressCode}"
                            )
                        } else {
                            appendLine("$label ($date): BRAK DANYCH")
                        }
                    }
                }

                // 4. System Prompt — "Naukowy Wojownik"
                val systemPrompt = """
Jesteś elitarnym trenerem przygotowania motorycznego i naukowcem sportowym.
Persona: **Naukowy Wojownik** — precyzja fizjologa + bezpośredniość trenera MMA.

## ZASADY:

**Fizjologia w centrum:**
Wyjaśniaj MECHANIZM: kortyzol, glikogen mięśniowy, układ przywspółczulny/współczulny,
HRV jako marker ANS, debet snu, okno superkompensacji. Bez żargonu akademickiego.

**Format (OBOWIĄZKOWY):**
- Nagłówki Markdown (##, ###)
- Listy punktowe (- item)
- **Pogrubienia** dla kluczowych wniosków
- Zwięźle. Zero lania wody.

**Proaktywność diagnostyczna (KLUCZOWE):**
NA KOŃCU każdej odpowiedzi ZAWSZE zadaj 1-2 konkretne pytania diagnostyczne, np.:
- „HRV spada mimo braku treningu — infekcja lub stres emocjonalny?"
- „3 dni bez danych — przerwa planowana czy unikasz logowania złych dni?"
- „Sen 5h przez 4 doby — sytuacja zewnętrzna (praca, dziecko) czy bezsenność?"

**Ton:** Bezpośredni. Merytoryczny. Szanujący zawodnika. Zero banałów.

## DANE ZAWODNIKA (ostatnie 7 dni):
$contextString
""".trimIndent()

                // 5. Historia konwersacji dla API
                val conversationHistory = mutableListOf<Message>()
                conversationHistory.add(Message(role = "system", content = systemPrompt))
                messages
                    .filter { it.role == "user" || it.role == "model" }
                    .forEach { entity ->
                        conversationHistory.add(
                            Message(
                                role = if (entity.role == "user") "user" else "assistant",
                                content = entity.content
                            )
                        )
                    }

                // 6. Strzał sieciowy (I/O)
                val aiResponseText = withContext(Dispatchers.IO) {
                    aiRepository.sendMessage(
                        conversationHistory = conversationHistory,
                        modelId = "gpt-5.4"
                    )
                }

                // 7. Zapisz odpowiedź AI w Room i wyświetl (Main Thread)
                val aiMsg = ChatMessageEntity(
                    dateTimestamp = todayTimestamp,
                    timestamp = System.currentTimeMillis(),
                    role = "model",
                    content = aiResponseText
                )
                withContext(Dispatchers.IO) {
                    repository.saveChatMessage(aiMsg)
                }
                messages.add(aiMsg)

            } catch (e: Exception) {
                // Błąd NIE jest zapisywany w Room — tylko wyświetlany w UI
                e.printStackTrace()
                errorMessage = "Błąd połączenia z serwerem. Sprawdź internet i spróbuj ponownie."
            } finally {
                // Zawsze wyłącz spinner — niezależnie czy sukces czy błąd
                isTyping = false
            }
        }
    }

    // ─── Czyszczenie historii dnia ────────────────────────────────────────────

    fun clearChatHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.clearChatForDay(todayTimestamp)
            }
            messages.clear()
            errorMessage = null
        }
    }

    // ─── Usuwanie konkretnej wiadomości ──────────────────────────────────────

    fun deleteMessage(msg: ChatMessageEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteChatMessage(msg)
            }
            messages.remove(msg)
        }
    }

    fun dismissError() {
        errorMessage = null
    }
}