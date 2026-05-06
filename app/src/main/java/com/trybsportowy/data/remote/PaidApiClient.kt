package com.trybsportowy.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// --- MODELE DANYCH (STANDARD OPENAI) ---
data class ChatRequest(
    val model: String, // Tu będziemy przekazywać "gpt-5.4" lub "gpt-5.4-mini"
    val messages: List<Message>,
    val temperature: Double = 0.7
)

data class Message(
    val role: String, // "system" lub "user"
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>?
)

data class Choice(
    val message: Message?
)

// --- INTERFEJS RETROFIT ---
interface PaidApiService {
    @POST("v1/chat/completions") // Standardowa końcówka płatnych API
    suspend fun generateContent(
        @Header("Authorization") authHeader: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: ChatRequest
    ): ChatResponse
}

// --- KLIENT API ---
object PaidApiClient {
    // UWAGA: Zmień ten URL na adres dostawcy, u którego kupiłeś klucz API
    // Jeśli to oficjalne OpenAI, zostaw jak jest.
    private const val BASE_URL = "https://api.openai.com/"

    // Cierpliwość 60 sekund
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val apiService: PaidApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PaidApiService::class.java)
    }
}