package com.trybsportowy.data.repository

import android.util.Log
import com.trybsportowy.data.remote.*
import com.trybsportowy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class AiRepositoryImpl {

    private val apiKey = BuildConfig.GEMINI_API_KEY

    /**
     * Wysyła pełną historię konwersacji do API.
     * @param conversationHistory Lista wiadomości [system, user, assistant, user, ...]
     *                            budowana przez ViewModel. Zachowuje ciągłość wątku.
     * @param modelId Identyfikator modelu (np. "gpt-4o")
     */
    suspend fun sendMessage(
        conversationHistory: List<Message>,
        modelId: String
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val request = ChatRequest(
                    model = modelId,
                    messages = conversationHistory
                )

                val response = PaidApiClient.apiService.generateContent(
                    authHeader = "Bearer $apiKey",
                    request = request
                )

                response.choices?.firstOrNull()?.message?.content
                    ?: "Trener nie odpowiedział."

            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: "Brak detali"
                Log.e("AiRepo", "HTTP ${e.code()}: $errorBody")
                "Błąd serwera (${e.code()}): $errorBody"
            } catch (e: Exception) {
                Log.e("AiRepo", "Wyjątek: ", e)
                "Błąd połączenia: ${e.localizedMessage}"
            }
        }
    }
}