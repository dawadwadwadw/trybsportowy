package com.trybsportowy.presentation.quickentry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope // Nowy import!
import com.trybsportowy.TrybsportowyApplication
import com.trybsportowy.presentation.widget.ReadinessWidget
import com.trybsportowy.ui.theme.TrybsportowyTheme
import kotlinx.coroutines.launch // Nowy import!

class QuickEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as TrybsportowyApplication
        val dateExtra = intent.getLongExtra("EXTRA_DATE", System.currentTimeMillis())
        val viewModel = QuickEntryViewModel(app.repository, dateExtra)

        setContent {
            TrybsportowyTheme {
                QuickEntryScreen(
                    viewModel = viewModel,
                    onDismiss = {
                        // TŁO: Widżet aktualizuje się asynchronicznie, nie blokując ekranu!
                        lifecycleScope.launch {
                            ReadinessWidget().updateAll(this@QuickEntryActivity)
                        }
                        // Zamykamy aktywność NATYCHMIAST
                        finish()
                    }
                )
            }
        }
    }
}