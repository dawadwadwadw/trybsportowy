package com.trybsportowy.presentation.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.trybsportowy.TrybsportowyApplication
import com.trybsportowy.ui.theme.TrybsportowyTheme

class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as TrybsportowyApplication
        val viewModel = ChatViewModel(app.repository)

        setContent {
            TrybsportowyTheme {
                ChatScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}