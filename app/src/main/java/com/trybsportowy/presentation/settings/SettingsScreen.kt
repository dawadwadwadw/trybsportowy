package com.trybsportowy.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val currentSettings by viewModel.settings.collectAsState()

    // 1. DEKLARACJE ZMIENNYCH NA SAMEJ GÓRZE
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val backupManager = remember { com.trybsportowy.data.backup.BackupManager() }

    // Systemowe okno do wyboru pliku z pamięci telefonu
    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                val importedData = backupManager.importFromJson(context, it)
                if (importedData != null && importedData.isNotEmpty()) {
                    viewModel.importData(importedData)
                    android.widget.Toast.makeText(context, "Przywrócono ${importedData.size} dni!", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    android.widget.Toast.makeText(context, "Błąd lub plik jest pusty.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 2. STRUKTURA EKRANU
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Ustawienia i Bezpieczeństwo") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // --- SEKCJA ALGORYTMU ---
            Text("Parametry Algorytmu", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            // Suwak dla Snu (Wczoraj)
            Text(
                text = "Mnożnik snu z wczoraj: ${"%.2f".format(currentSettings.weightYesterdaySleep)}",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = currentSettings.weightYesterdaySleep,
                onValueChange = { viewModel.updateSettings(currentSettings.copy(weightYesterdaySleep = it)) },
                valueRange = 0.5f..1.2f // Bezpieczny zakres
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Suwak dla Wagi Dnia (Wczoraj)
            Text(
                text = "Waga 'Wczoraj': ${"%.2f".format(currentSettings.weightYesterday)}",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = currentSettings.weightYesterday,
                onValueChange = { viewModel.updateSettings(currentSettings.copy(weightYesterday = it)) },
                valueRange = 0f..1f
            )

            Spacer(modifier = Modifier.weight(1f)) // Wypycha resztę na dół

            // --- SEKCJA BEZPIECZEŃSTWA (BACKUP) ---
            Text("BEZPIECZEŃSTWO DANYCH", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        val allData = viewModel.getAllHistory()
                        if (allData.isEmpty()) {
                            android.widget.Toast.makeText(context, "Brak danych do eksportu.", android.widget.Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val success = backupManager.exportToDownloads(context, allData)
                        if (success) {
                            android.widget.Toast.makeText(context, "Kopia w folderze Pobrane (Downloads)!", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            android.widget.Toast.makeText(context, "Błąd eksportu.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
            ) {
                Text("Eksportuj Kopię (JSON)", color = Color.White)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    // Otwiera menedżer plików (application/json)
                    filePickerLauncher.launch(arrayOf("application/json", "*/*"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Importuj Kopię", color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- PRZYCISK POWROTU ---
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("POWRÓT I ZAPISZ")
            }
        }
    }
}