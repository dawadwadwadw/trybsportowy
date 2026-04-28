package com.trybsportowy.data.remote

import okhttp3.OkHttpClient // Nowy import!
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit // Nowy import!

data class GeminiRequest(
    val contents: List<Content>,
    val systemInstruction: SystemInstruction? = null
)

data class SystemInstruction(val parts: List<Part>)
data class Content(val role: String, val parts: List<Part>)
data class Part(val text: String)

data class GeminiResponse(val candidates: List<Candidate>?)
data class Candidate(val content: Content?)

interface GeminiApiService {
    // Ustawiamy najmądrzejszy model, jaki masz odblokowany (Gemini 2.5 Flash)
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiApiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    // DODAJEMY KLIENTA: Cierpliwość wydłużona do 60 sekund
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client) // PODPINAMY KLIENTA
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }
}