package com.trybsportowy.presentation.quickentry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ─── Mapowanie drain na status UI ──────────────────────────────────────────

private fun drainStatus(points: Int): Pair<String, Color> = when {
    points <= 2  -> "✅ Świeży"       to Color(0xFF81C784)
    points <= 5  -> "🟡 Lekki drenaż" to Color(0xFFFFF176)
    points <= 9  -> "🟠 Przeciążony"  to Color(0xFFFFB74D)
    else         -> "🔴 Kryzys"       to Color(0xFFE57373)
}

// ─── Główny ekran ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntryScreen(viewModel: QuickEntryViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    // Synchronizacja stanu pagera z ViewModelem (przyciski i swipe razem)
    LaunchedEffect(pagerState.currentPage) {
        viewModel.goToPage(pagerState.currentPage)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // ─── Nagłówek ───────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = viewModel.headerTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                // Wskaźnik kroków
                StepIndicator(currentPage = pagerState.currentPage)
            }

            HorizontalDivider()

            // ─── Pager ──────────────────────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) { page ->
                when (page) {
                    0 -> PageMainCodes(viewModel = viewModel)
                    1 -> PageStressTags(viewModel = viewModel)
                }
            }

            HorizontalDivider()

            // ─── Nawigacja dolna ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage == 1) {
                    OutlinedButton(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(0) }
                        }
                    ) {
                        Text("← Wstecz")
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (pagerState.currentPage == 0) {
                    Button(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(1) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Dalej →")
                    }
                } else {
                    Button(
                        onClick = { viewModel.saveEntry(onSaved = onDismiss) },
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    ) {
                        Text("ZAPISZ ✓")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─── Wskaźnik kroków ─────────────────────────────────────────────────────────

@Composable
private fun StepIndicator(currentPage: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(2) { index ->
            val isActive = index == currentPage
            Surface(
                modifier = Modifier
                    .height(4.dp)
                    .width(if (isActive) 28.dp else 14.dp),
                shape = MaterialTheme.shapes.small,
                color = if (isActive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outlineVariant
            ) {}
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${currentPage + 1} / 2",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Strona 1: Kody główne ───────────────────────────────────────────────────

@Composable
private fun PageMainCodes(viewModel: QuickEntryViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        CodeRow(
            title = "💤 Sen (Dzisiejsza noc)",
            options = listOf("S0", "S1", "S2", "S3", "S4"),
            subtitles = listOf("<6h", "6–7h", "7–8h", "8–9h", "9h+"),
            selected = viewModel.sleep,
            onSelect = { viewModel.sleep = it }
        )

        CodeRow(
            title = "🫀 HRV (Poranny pomiar)",
            options = listOf("H0", "H1", "H2", "H3"),
            subtitles = listOf("1–3", "4–5", "6–7", "8–10"),
            selected = viewModel.hrv,
            onSelect = { viewModel.hrv = it }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        CodeRow(
            title = "🏃 Trening (Wczoraj)",
            options = listOf("P0", "P1", "P2", "P3", "P4"),
            subtitles = listOf("Brak", "Lekko", "Solidnie", "Mocno", "Ekst."),
            selected = viewModel.physical,
            onSelect = { viewModel.physical = it }
        )

        CodeRow(
            title = "💼 Praca (Wczoraj)",
            options = listOf("W0", "W1", "W2", "W3", "W4"),
            subtitles = listOf("Wolne", "<4h", "4–6h", "6–8h", "10h+"),
            selected = viewModel.work,
            onSelect = { viewModel.work = it }
        )

        CodeRow(
            title = "🍺 Alkohol (Wczoraj)",
            options = listOf("A0", "A1", "A2", "A3"),
            subtitles = listOf("Zero", "1–2", "Więcej", "Kac"),
            selected = viewModel.alcohol,
            onSelect = { viewModel.alcohol = it }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        CodeRow(
            title = "🥗 Dieta i Nawodnienie",
            options = listOf("N0", "N1", "N2", "N3"),
            subtitles = listOf("Ideał", "Normalnie", "Słabo", "Kryzys"),
            selected = viewModel.nutrition,
            onSelect = { viewModel.nutrition = it }
        )

        Spacer(Modifier.height(8.dp))
    }
}

// ─── Strona 2: Tagi Stresu ────────────────────────────────────────────────────

@Composable
private fun PageStressTags(viewModel: QuickEntryViewModel) {
    // Odczytaj wersje, żeby rekomposycja się wywołała przy zmianie setów
    val cnsVersion  = viewModel.cnsTagsVersion
    val bodyVersion = viewModel.bodyTagsVersion

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ─── CNS ──────────────────────────────────────────────────────
        TagSection(
            sectionTitle = "🧠 STRES MENTALNY / CNS",
            tags = CNS_TAGS,
            selectedIds = viewModel.selectedCnsTags,
            drainPoints = viewModel.cnsDrain,
            onToggle = { tag -> viewModel.toggleCnsTag(tag.id, tag.points) },
            versionKey = cnsVersion
        )

        Spacer(Modifier.height(20.dp))

        // ─── Body ─────────────────────────────────────────────────────
        TagSection(
            sectionTitle = "💪 STRES FIZYCZNY / CIAŁO",
            tags = BODY_TAGS,
            selectedIds = viewModel.selectedBodyTags,
            drainPoints = viewModel.bodyDrain,
            onToggle = { tag -> viewModel.toggleBodyTag(tag.id, tag.points) },
            versionKey = bodyVersion
        )

        Spacer(Modifier.height(8.dp))
    }
}

// ─── Sekcja tagów (CNS lub Body) ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSection(
    sectionTitle: String,
    tags: List<DrainTag>,
    selectedIds: Set<String>,
    drainPoints: Int,
    onToggle: (DrainTag) -> Unit,
    versionKey: Int // wymuszamy rekomposycję
) {
    val (statusLabel, statusColor) = drainStatus(drainPoints)

    Column {
        // Nagłówek sekcji
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(6.dp))

        // Status live
        Surface(
            shape = MaterialTheme.shapes.small,
            color = statusColor.copy(alpha = 0.18f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = statusColor.copy(alpha = 1f)
                )
                Text(
                    text = "$drainPoints pkt",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Chipy tagów — dwa na wiersz
        val chunked = tags.chunked(2)
        chunked.forEach { rowTags ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowTags.forEach { tag ->
                    val isSelected = selectedIds.contains(tag.id)
                    val pointSign  = if (tag.points >= 0) "+${tag.points}" else "${tag.points}"
                    var showTooltip by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.weight(1f)) {
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggle(tag) },
                            label = {
                                Text(
                                    text = "${tag.emoji} ${tag.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp
                                )
                            },
                            trailingIcon = {
                                Text(
                                    text = pointSign,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (tag.points < 0)
                                        Color(0xFF81C784)
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Tooltip po kliknięciu ikony info
                        if (showTooltip) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 3.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = tag.tooltip,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    TextButton(
                                        onClick = { showTooltip = false },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("OK", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
                // Jeśli wiersz ma tylko 1 element — wypełnij miejsce
                if (rowTags.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ─── CodeRow — rząd przycisków kodu ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeRow(
    title: String,
    options: List<String>,
    subtitles: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 5.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { index, option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (index < subtitles.size) {
                                Text(
                                    text = subtitles[index],
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

// ─── (Stary helper — zachowany dla kompatybilności wstecznej) ────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelector(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(option) }
                )
            }
        }
    }
}
