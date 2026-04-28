package com.trybsportowy.presentation.quickentry

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntryScreen(viewModel: QuickEntryViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = viewModel.headerTitle, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // Dziennik Dnia (AI)
            OutlinedTextField(
                value = viewModel.journalText,
                onValueChange = { viewModel.journalText = it },
                label = { Text("Dziennik Dnia (AI analizuje stres)") },
                placeholder = { Text("Np. Dziś był ciężki dzień, kłótnia w pracy i mało piłem wody...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { scope.launch { viewModel.evaluateStressWithAI(viewModel.journalText) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isAiLoading && viewModel.journalText.isNotBlank()
            ) {
                if (viewModel.isAiLoading) {
                    CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Oceń Stres (AI)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            CategorySelector("Sen (Dzisiejsza noc)", listOf("S0", "S1", "S2", "S3", "S4"), viewModel.sleep) { viewModel.sleep = it }
            CategorySelector("HRV (Poranny pomiar)", listOf("H0", "H1", "H2", "H3"), viewModel.hrv) { viewModel.hrv = it }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            CategorySelector("Stres Systemowy", listOf("L", "M", "H", "X"), viewModel.stress) { viewModel.stress = it }
            CategorySelector("Obciążenie Fizyczne (Wczoraj)", listOf("P0", "P1", "P2", "P3", "P4"), viewModel.physical) { viewModel.physical = it }
            CategorySelector("Praca / Dostawy (Wczoraj)", listOf("W0", "W1", "W2", "W3", "W4"), viewModel.work) { viewModel.work = it }
            CategorySelector("Alkohol (Wczoraj)", listOf("A0", "A1", "A2", "A3"), viewModel.alcohol) { viewModel.alcohol = it }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { viewModel.saveEntry(onSaved = onDismiss) }, modifier = Modifier.fillMaxWidth()) {
                Text("ZAPISZ DANE")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelector(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(option) })
            }
        }
    }
}

@Composable
fun CircularProgressIndicator(size: androidx.compose.ui.unit.Dp, strokeWidth: androidx.compose.ui.unit.Dp, color: androidx.compose.ui.graphics.Color) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = Modifier.size(size),
        strokeWidth = strokeWidth,
        color = color
    )
}
