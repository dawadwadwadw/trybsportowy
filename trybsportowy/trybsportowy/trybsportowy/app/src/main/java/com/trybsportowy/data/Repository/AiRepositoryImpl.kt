package com.trybsportowy.data.repository

import android.util.Log
import com.trybsportowy.BuildConfig
import com.trybsportowy.data.remote.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class AiRepositoryImpl {

    private val apiKey = BuildConfig.GEMINI_API_KEY

    suspend fun sendMessage(userMessage: String, readinessContext: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // NOWY, ELITARNY SYSTEM PROMPT
                val systemPrompt = Part(
                    "Jesteś elitarnym analitykiem sportowym i trenerem przygotowania motorycznego w sportach walki (Kickboxing, MMA). Zastępujesz głównego trenera w zarządzaniu obciążeniami zawodnika. " +
                            "Otrzymasz listę kodów reprezentujących ostatnie 4 dni zawodnika (według algorytmu rozpadu zmęczenia). " +
                            "SŁOWNIK: S (Sen): S0-S1 (deprywacja), S2-S3 (optymalnie), S4 (9h+). H (HRV): H0 (-35 pkt, zapaść/wirus), H1 (sympatyczny overreaching), H2 (adaptacja), H3 (szczyt parasympatyczny). P (Trening): P3/P4 (rzeźnia CNS), P1 (lekki flow). W (Praca), A (Alkohol), D (Stres ogólny). " +
                            "TWOJE ZADANIE: Nie udzielaj krótkich porad. Przeprowadź pogłębioną, wielowątkową dyskusję. Zachowuj się jak trener, który analizuje dane z Oura/Elite HRV przed treningiem. " +
                            "STRUKTURA: 1. Diagnoza Fizjologiczna: Rozbij na atomy, co widzisz. Połącz kropki. 2. Weryfikacja Pomysłów zawodnika chłodnym okiem biomechaniki. 3. Strategia i Protokół (Damage Control). " +
                            "STYL: Inteligentny, analityczny, szczery do bólu. Bądź kategoryczny, gdy zawodnik chce zaryzykować kontuzję.\n" +
                            "DANE ZAWODNIKA:\n$readinessContext"
                )

                val userContent = Content("user", listOf(Part(userMessage)))

                val request = GeminiRequest(
                    systemInstruction = SystemInstruction(listOf(systemPrompt)),
                    contents = listOf(userContent)
                )

                val response = GeminiApiClient.apiService.generateContent(
                    apiKey = apiKey,
                    request = request
                )

                response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Trener milczy."

            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: "Brak detali"
                Log.e("GeminiAPI", "Błąd API: ${e.code()} - $errorBody")
                "Odmowa od Google (Błąd ${e.code()}):\n$errorBody"
            } catch (e: Exception) {
                Log.e("GeminiAPI", "Wyjątek sieci: ", e)
                "Błąd łączności z AI: ${e.localizedMessage}"
            }
        }
    }
}
