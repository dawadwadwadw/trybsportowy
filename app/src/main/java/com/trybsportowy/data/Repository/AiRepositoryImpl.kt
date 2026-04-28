package com.trybsportowy.data.repository

import android.util.Log
import com.trybsportowy.data.remote.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import com.trybsportowy.BuildConfig // Import wygenerowanego pliku konfiguracyjnego

class AiRepositoryImpl {

    // Odczyt klucza z pliku local.properties (skonfigurowane w build.gradle)
    private val apiKey = BuildConfig.GEMINI_API_KEY

    // Dodano parametr 'modelId'
    suspend fun sendMessage(userMessage: String, systemPromptText: String, modelId: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Budowanie konwersacji w nowym standardzie
                val messages = listOf(
                    Message(role = "system", content = systemPromptText),
                    Message(role = "user", content = userMessage)
                )

                // Tworzymy request dla KONKRETNEGO modelu (Tani lub Drogi)
                val request = ChatRequest(
                    model = modelId,
                    messages = messages
                )

                // Płatne API używają nagłówka Bearer
                val response = PaidApiClient.apiService.generateContent(
                    authHeader = "Bearer $apiKey",
                    request = request
                )

                // Wyciąganie tekstu odpowiedzi
                response.choices?.firstOrNull()?.message?.content ?: "Trener milczy."

            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: "Brak detali"
                Log.e("PaidAPI", "Błąd API: ${e.code()} - $errorBody")
                "Odmowa od serwera (Błąd ${e.code()}):\n$errorBody"
            } catch (e: Exception) {
                Log.e("PaidAPI", "Wyjątek sieci: ", e)
                "Błąd łączności z AI: ${e.localizedMessage}"
            }
        }
    }
}