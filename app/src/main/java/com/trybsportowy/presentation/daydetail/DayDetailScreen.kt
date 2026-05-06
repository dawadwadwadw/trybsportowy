package com.trybsportowy.presentation.daydetail

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.presentation.quickentry.BODY_TAGS
import com.trybsportowy.presentation.quickentry.CNS_TAGS
import com.trybsportowy.presentation.quickentry.QuickEntryActivity
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ─── Helpers lokalne ─────────────────────────────────────────────────────────

private fun drainLabel(points: Int): Pair<String, Color> = when {
    points <= 2  -> "✅ Świeży"        to Color(0xFF81C784)
    points <= 5  -> "🟡 Lekki drenaż" to Color(0xFFFFF176)
    points <= 9  -> "🟠 Przeciążony"  to Color(0xFFFFB74D)
    else         -> "🔴 Kryzys"       to Color(0xFFE57373)
}

private fun drainCode(total: Int) = when {
    total <= 3  -> "D:L"
    total <= 7  -> "D:M"
    total <= 13 -> "D:H"
    else        -> "D:X"
}

private fun codeDescription(code: String): String = when (code) {
    // Sen
    "S0" -> "💤 Zapaść (<6h)"
    "S1" -> "💤 Deficyt (6–7h)"
    "S2" -> "💤 Optymalnie (7–8h)"
    "S3" -> "💤 Bardzo dobrze (8–9h)"
    "S4" -> "💤 Ekstremalna regeneracja (9h+)"
    // HRV
    "H0" -> "🫀 Zapaść (1–3)"
    "H1" -> "🫀 Ostrzeżenie (4–5)"
    "H2" -> "🫀 Neutralnie (6–7)"
    "H3" -> "🫀 Szczytowa forma (8–10)"
    // Trening
    "P0" -> "🏃 Brak / Rozruch"
    "P1" -> "🏃 Lekko"
    "P2" -> "🏋️ Solidnie"
    "P3" -> "🏋️ Mocno"
    "P4" -> "💀 Ekstremum"
    // Praca
    "W0" -> "💼 Wolne / Home"
    "W1" -> "💼 Lekko (<4h)"
    "W2" -> "💼 Normalnie (4–6h)"
    "W3" -> "💼 Ciężko (6–8h)"
    "W4" -> "💼 Maraton (10h+)"
    // Alkohol
    "A0" -> "🍺 Zero"
    "A1" -> "🍺 Lekko (1–2 piwa)"
    "A2" -> "🍺 Średnio (zakłócony sen)"
    "A3" -> "🍺 Kac"
    // Dieta
    "N0" -> "🥗 Ideał (pełne makro, 2.5L+)"
    "N1" -> "🍽️ Normalnie (przeciętna dieta)"
    "N2" -> "🍔 Słabo (fast food, odwodnienie)"
    "N3" -> "⚠️ Kryzys (głodzenie / brak jedzenia)"
    // Stres
    "L"  -> "🟢 D:L — niski drenaż"
    "M"  -> "🟡 D:M — średni drenaż"
    "H"  -> "🟠 D:H — wysoki drenaż"
    "X"  -> "🔴 D:X — kryzys"
    else -> code
}

// ─── Główny ekran ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    entity: DailyReadinessEntity,
    readinessScore: Float,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onDelete: suspend () -> Unit
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val dateStr = remember(entity.dateTimestamp) {
        Instant.ofEpochMilli(entity.dateTimestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd MMMM yyyy (EEEE)"))
    }

    val activeTags = remember(entity.drainTags) {
        if (entity.drainTags.isBlank()) emptyList()
        else entity.drainTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    val activeCnsTags  = remember(activeTags) { CNS_TAGS.filter  { it.id in activeTags } }
    val activeBodyTags = remember(activeTags) { BODY_TAGS.filter { it.id in activeTags } }

    val totalDrain = entity.cnsDrain + entity.bodyDrain
    val dCode = drainCode(totalDrain)
    val (cnsLabel,  cnsColor)  = drainLabel(entity.cnsDrain)
    val (bodyLabel, bodyColor) = drainLabel(entity.bodyDrain)

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Usunąć wpis?") },
            text = { Text("Wpis z dnia $dateStr zostanie trwale usunięty.") },
            // G:/trybsportowy/app/src/main/java/com/trybsportowy/presentation/daydetail/DayDetailScreen.kt

// ... (linia ok. 138)
            confirmButton = {
                Button(
                    onClick = {
                        // ZAMIAST: kotlinx.coroutines.MainScope().launch
                        scope.launch {
                            onDelete()
                            onDeleted()
                        }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Usuń") }
            },

            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) { Text("Anuluj") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dateStr, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Usuń", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── Łączny wynik ───────────────────────────────────────────────
            item {
                ReadinessScoreCard(score = readinessScore)
            }

            // ─── Kody główne ─────────────────────────────────────────────────
            item {
                DetailCard(title = "KODY GŁÓWNE") {
                    DetailRow("Sen",     entity.sleepCode,        codeDescription(entity.sleepCode))
                    DetailRow("HRV",     entity.hrvCode,          codeDescription(entity.hrvCode))
                    DetailRow("Trening", entity.physicalLoadCode, codeDescription(entity.physicalLoadCode))
                    DetailRow("Praca",   entity.workCode,         codeDescription(entity.workCode))
                    DetailRow("Alkohol", entity.alcoholCode,      codeDescription(entity.alcoholCode))
                    DetailRow("Dieta",   entity.nutritionCode,    codeDescription(entity.nutritionCode))
                }
            }

            // ─── Stres Systemowy ─────────────────────────────────────────────
            item {
                DetailCard(title = "STRES SYSTEMOWY") {
                    DrainStatusRow("🧠 CNS",  entity.cnsDrain,  cnsLabel,  cnsColor)
                    Spacer(Modifier.height(4.dp))
                    DrainStatusRow("💪 Ciało", entity.bodyDrain, bodyLabel, bodyColor)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Kod D:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        val dColor = when (dCode) {
                            "D:L" -> Color(0xFF81C784)
                            "D:M" -> Color(0xFFFFF176)
                            "D:H" -> Color(0xFFFFB74D)
                            else  -> Color(0xFFE57373)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = dColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = dCode,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = dColor
                            )
                        }
                    }
                }
            }

            // ─── Zaznaczone Tagi ─────────────────────────────────────────────
            if (activeTags.isNotEmpty()) {
                item {
                    DetailCard(title = "ZAZNACZONE TAGI") {
                        if (activeCnsTags.isNotEmpty()) {
                            Text("🧠 CNS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            activeCnsTags.forEach { tag ->
                                val sign = if (tag.points >= 0) "+${tag.points}" else "${tag.points}"
                                TagDetailRow(tag.emoji, tag.label, sign, tag.points < 0)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (activeBodyTags.isNotEmpty()) {
                            Text("💪 Ciało", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            activeBodyTags.forEach { tag ->
                                val sign = if (tag.points >= 0) "+${tag.points}" else "${tag.points}"
                                TagDetailRow(tag.emoji, tag.label, sign, tag.points < 0)
                            }
                        }
                    }
                }
            }

            // ─── Akcje ───────────────────────────────────────────────────────
            item {
                Button(
                    onClick = {
                        val intent = Intent(context, QuickEntryActivity::class.java).apply {
                            putExtra("EXTRA_DATE", entity.dateTimestamp)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("✏️ EDYTUJ TEN DZIEŃ")
                }

                Spacer(Modifier.height(4.dp))

                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("🗑️ USUŃ WPIS")
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ─── Sub-komponenty ───────────────────────────────────────────────────────────

@Composable
private fun ReadinessScoreCard(score: Float) {
    val scoreColor = when {
        score >= 50f  -> Color(0xFF81C784)
        score >= 0f   -> Color(0xFFAED581)
        score >= -30f -> Color(0xFFFFB74D)
        else          -> Color(0xFFE57373)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("ŁĄCZNY WYNIK READINESS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${score.toInt()} pkt",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = scoreColor
                )
            }
            Text(
                text = when {
                    score >= 50f  -> "🟢"
                    score >= 0f   -> "🟡"
                    score >= -30f -> "🟠"
                    else          -> "🔴"
                },
                fontSize = 40.sp
            )
        }
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, code: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(68.dp)
        )
        Text(
            text = code,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(32.dp)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DrainStatusRow(label: String, points: Int, statusLabel: String, statusColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(72.dp)
        )
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = statusColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = "$statusLabel ($points pkt)",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor
            )
        }
    }
}

@Composable
private fun TagDetailRow(emoji: String, label: String, pointsStr: String, isPositive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 16.sp, modifier = Modifier.width(28.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = pointsStr,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isPositive) Color(0xFF81C784) else MaterialTheme.colorScheme.error
        )
    }
}
