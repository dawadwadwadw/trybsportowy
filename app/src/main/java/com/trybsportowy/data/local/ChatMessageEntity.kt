package com.trybsportowy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateTimestamp: Long, // Aby filtrować rozmowy z "dzisiaj"
    val timestamp: Long,     // Dokładny czas do sortowania bąbelków
    val role: String,        // "user" lub "model" (Gemini wymaga tych nazw)
    val content: String
)