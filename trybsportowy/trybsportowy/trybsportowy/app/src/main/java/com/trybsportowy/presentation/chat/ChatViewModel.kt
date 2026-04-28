package com.trybsportowy.presentation.chat

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trybsportowy.data.local.ChatMessageEntity
import com.trybsportowy.data.repository.AiRepositoryImpl
import com.trybsportowy.domain.repository.ReadinessRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class ChatViewModel(
    private val repository: ReadinessRepository,
    private val aiRepository: AiRepositoryImpl = AiRepositoryImpl()
) : ViewModel() {

    val messages = mutableStateListOf<ChatMessageEntity>()
    private val todayTimestamp = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    init {
        loadTodayChat()
    }

    private fun loadTodayChat() {
        viewModelScope.launch {
            messages.clear()
            messages.addAll(repository.getChatHistory(todayTimestamp))
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            // 1. Zapisujemy Twoją wiadomość
            val userMsg = ChatMessageEntity(dateTimestamp = todayTimestamp, timestamp = System.currentTimeMillis(), role = "user", content = text)
            repository.saveChatMessage(userMsg)
            messages.add(userMsg)

            // 2. Pobieramy 4 dni wstecz
            val fourDaysAgo = todayTimestamp - (3 * 86400000L)
            val recentData = repository.getReadinessSince(fourDaysAgo)

            // 3. Budujemy super-kontekst dla elitarnego Trenera AI
            val contextString = buildString {
                for (i in 0..3) {
                    val dayMillis = todayTimestamp - (i * 86400000L)
                    val dayData = recentData.find { it.dateTimestamp == dayMillis }
                    val label = when(i) {
                        0 -> "Dzisiaj (100% wagi)"
                        1 -> "Wczoraj (75% wagi)"
                        2 -> "2 dni temu (50% wagi)"
                        else -> "3 dni temu (20% wagi)"
                    }
                    if (dayData != null) {
                        // Wyciągamy same cyfry/litery dla AI, żeby oszczędzać limit tokenów
                        appendLine("$label: H${dayData.hrvCode.last()}, S${dayData.sleepCode.last()}, P${dayData.physicalLoadCode.last()}, W${dayData.workCode.last()}, A${dayData.alcoholCode.last()}")
                    } else {
                        appendLine("$label: Brak danych")
                    }
                }
                appendLine("Wiadomość od zawodnika: \"$text\"")
            }

            // 4. Pokazujemy, że Trener pisze
            val typingMsg = ChatMessageEntity(dateTimestamp = todayTimestamp, timestamp = System.currentTimeMillis(), role = "model", content = "Trener analizuje dane fizjologiczne...")
            messages.add(typingMsg)

            // 5. Strzał do API
            val aiResponseText = aiRepository.sendMessage(userMessage = text, readinessContext = contextString)

            // 6. Podmiana na właściwą odpowiedź
            messages.removeLast()
            val aiMsg = ChatMessageEntity(dateTimestamp = todayTimestamp, timestamp = System.currentTimeMillis(), role = "model", content = aiResponseText)
            repository.saveChatMessage(aiMsg)
            messages.add(aiMsg)
        }
    }
}