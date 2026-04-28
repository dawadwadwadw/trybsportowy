package com.trybsportowy.presentation.dashboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trybsportowy.TrybsportowyApplication
import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.domain.usecase.CalculateReadinessUseCase
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProDashboardScreen(app: TrybsportowyApplication, onBack: () -> Unit) {
    val history = remember { mutableStateListOf<DailyReadinessEntity>() }
    // POPRAWKA: Na start dajemy puste ustawienia, żeby nie blokować ekranu
    val settings = remember { mutableStateOf(com.trybsportowy.data.local.DecaySettingsEntity()) }
    val calculator = remember { CalculateReadinessUseCase() }

    LaunchedEffect(Unit) {
        // POPRAWKA: Pobieramy dane z bazy bezpiecznie "w tle"
        history.addAll(app.repository.getReadinessSince(0))
        settings.value = app.repository.getDecaySettings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DASHBOARD PRO", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("✕", fontSize = 20.sp) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFF121212)) // Ciemny grafit
                .verticalScroll(rememberScrollState())
        ) {
            MirrorChart(history, settings.value, calculator)

            InjuryGuardWidget(history)

            // WPINAMY ETAP 3 TUTAJ:
            AiDetectiveWidget(history)

            Spacer(modifier = Modifier.height(32.dp))
        }

    }
}

@Composable
fun MirrorChart(history: List<DailyReadinessEntity>, settings: com.trybsportowy.data.local.DecaySettingsEntity, calculator: CalculateReadinessUseCase) {
    val scrollState = rememberScrollState()
    val today = LocalDate.now()
    val days = (0..20).map { today.minusDays(it.toLong()) } // Widok 21 dni

    // Ustawienia skali
    val chartHeight = 300.dp
    val zeroLineY = 150.dp // Środek wykresu (linia zero)

    Box(modifier = Modifier.fillMaxWidth().height(chartHeight + 100.dp)) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            days.forEach { date ->
                val timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val dataForDay = history.find { it.dateTimestamp == timestamp }

                // Obliczanie Readiness (GÓRA)
                val relevantHistory = history.filter { it.dateTimestamp <= timestamp }
                val score = if (relevantHistory.isNotEmpty()) calculator.execute(relevantHistory, settings, timestamp) else 0f

                // Obliczanie wag "Zęba" (DÓŁ)
                val load = calculateStackedLoad(dataForDay)

                Column(
                    modifier = Modifier.width(60.dp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // --- GÓRA: Readiness (0 to 100) ---
                    Box(modifier = Modifier.height(zeroLineY).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        val scoreHeight = (score.coerceIn(0f, 100f) / 100f * 150).dp
                        if (score > 0) {
                            Box(modifier = Modifier.width(4.dp).height(scoreHeight).background(Color(0xFF81C784), RoundedCornerShape(2.dp)))
                        }
                    }

                    // --- LINIA ZERO ---
                    HorizontalDivider(
                        Modifier,
                        thickness = 1.dp,
                        color = Color.Gray.copy(alpha = 0.5f)
                    )

                    // --- DÓŁ: Skumulowany Ząb (P, W, A, D) ---
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Słupki skumulowane (Kolejność: P -> W -> A -> D)
                            LoadBar(load.pPoints, Color(0xFFE57373)) // Czerwony: Trening
                            LoadBar(load.wPoints, Color(0xFF64B5F6)) // Niebieski: Praca
                            LoadBar(load.aPoints, Color(0xFFBA68C8)) // Fioletowy: Alkohol
                            LoadBar(load.dPoints, Color(0xFFFFD54F)) // Żółty: Stres
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Daty na samym dole
                    Text(date.format(DateTimeFormatter.ofPattern("dd.MM")), fontSize = 10.sp, color = Color.Gray)
                    Text(date.format(DateTimeFormatter.ofPattern("E")), fontSize = 10.sp, color = if (date.dayOfWeek.value >= 6) Color.Red else Color.White)
                }
            }
        }
    }
}

@Composable
fun LoadBar(points: Float, color: Color) {
    if (points > 0) {
        val h = (points * 1.5f).dp // Skalowanie wizualne
        Box(modifier = Modifier.width(12.dp).height(h).background(color))
    }
}

data class DayLoad(val pPoints: Float, val wPoints: Float, val aPoints: Float, val dPoints: Float)

fun calculateStackedLoad(entity: DailyReadinessEntity?): DayLoad {
    if (entity == null) return DayLoad(0f, 0f, 0f, 0f)

    // Surowe punkty z Twojego algorytmu
    val pRaw = when(entity.physicalLoadCode) { "P1" -> 10f; "P2" -> 30f; "P3" -> 55f; "P4" -> 85f; else -> 0f }
    val wRaw = when(entity.workCode) { "W1" -> 5f; "W2" -> 15f; "W3" -> 30f; "W4" -> 55f; else -> 0f }
    val aRaw = when(entity.alcoholCode) { "A1" -> 5f; "A2" -> 15f; "A3" -> 35f; else -> 0f }
    val dRaw = when(entity.stressCode) { "M" -> 10f; "H" -> 25f; "X" -> 40f; else -> 0f }

    // Mnożniki wagi (Trening 1.0, Praca 0.8, Alkohol 0.6, Stres 0.3)
    return DayLoad(
        pPoints = pRaw * 1.0f,
        wPoints = wRaw * 0.8f,
        aPoints = aRaw * 0.6f,
        dPoints = dRaw * 0.3f
    )
}


@Composable
fun InjuryGuardWidget(history: List<DailyReadinessEntity>) {
    // 1. Matematyka A:C Ratio
    val today = LocalDate.now()
    var acuteLoad = 0f
    var chronicLoad = 0f

    for (i in 0..27) {
        val date = today.minusDays(i.toLong())
        // Szukamy wpisu dla danej daty (porównujemy LocalDate)
        val entity = history.find {
            java.time.Instant.ofEpochMilli(it.dateTimestamp).atZone(ZoneId.systemDefault()).toLocalDate() == date
        }

        val load = calculateStackedLoad(entity) // Używamy naszej funkcji z wagami
        val totalDayLoad = load.pPoints + load.wPoints + load.aPoints + load.dPoints

        chronicLoad += totalDayLoad
        if (i < 7) {
            acuteLoad += totalDayLoad
        }
    }

    val acuteAvg = acuteLoad / 7f
    val chronicAvg = chronicLoad / 28f
    val acRatio = if (chronicAvg > 0) acuteAvg / chronicAvg else 0f

    // 2. Logika kolorów i ostrzeżeń
    val (statusColor, statusText) = when {
        acRatio == 0f -> Pair(Color.DarkGray, "BRAK DANYCH (Zacznij trenować)")
        acRatio < 0.8f -> Pair(Color(0xFF64B5F6), "ROZTRENOWANIE (Możesz docisnąć)")
        acRatio <= 1.3f -> Pair(Color(0xFF81C784), "SWEET SPOT (Idealny progres)")
        acRatio <= 1.5f -> Pair(Color(0xFFFFD54F), "OSTRZEŻENIE (Kumulacja obciążeń)")
        else -> Pair(Color(0xFFE57373), "STREFA KONTUZJI (Hamuj!)")
    }

    // 3. UI (Wygląd)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "INJURY GUARD (A:C RATIO)",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = String.format("%.2f", acRatio),
                    style = MaterialTheme.typography.headlineLarge,
                    color = statusColor,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pasek postępu
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color.DarkGray, RoundedCornerShape(4.dp))
            ) {
                // Skalujemy pasek: ratio 2.0 to 100% szerokości
                val fraction = (acRatio / 2.0f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(statusColor, RoundedCornerShape(4.dp))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Średnia 7 dni: ${acuteAvg.toInt()} pkt | Średnia 28 dni: ${chronicAvg.toInt()} pkt",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}


@Composable
fun AiDetectiveWidget(history: List<DailyReadinessEntity>) {
    var isExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var aiReport by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable {
                // Pozwala zwinąć panel, jeśli już jest otwarty
                if (aiReport != null) isExpanded = !isExpanded
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🤖 DETEKTYW AI (KORELACJE)",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFBA68C8), // Fioletowy neonowy
                    fontWeight = FontWeight.Bold
                )
                if (aiReport != null) {
                    Text(if (isExpanded) "▲ Zwiń" else "▼ Rozwiń", color = Color.Gray, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (aiReport == null && !isLoading) {
                Button(
                    onClick = {
                        isLoading = true
                        isExpanded = true
                        scope.launch {
                            // Tu wywołujemy funkcję, która uderza do API
                            aiReport = fetchAiReportFromApi(history)
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) {
                    Text("GENERUJ RAPORT AI", color = Color.White)
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFBA68C8))
                }
            }

            // Wyświetlanie gotowego raportu
            if (aiReport != null && isExpanded && !isLoading) {
                Text(
                    text = aiReport ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            aiReport = fetchAiReportFromApi(history)
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Odśwież analizę", color = Color.Gray)
                }
            }
        }
    }
}



suspend fun fetchAiReportFromApi(history: List<DailyReadinessEntity>): String =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val today = java.time.LocalDate.now()
            val daysData = (0..27).mapNotNull { i ->
                val date = today.minusDays(i.toLong())
                val entry = history.find { e ->
                    java.time.Instant.ofEpochMilli(e.dateTimestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate() == date
                }
                if (entry != null) {
                    "Dzień ${28 - i} [$date]: S:${entry.sleepCode}, H:${entry.hrvCode}, " +
                            "P:${entry.physicalLoadCode}, W:${entry.workCode}, " +
                            "A:${entry.alcoholCode}, D:${entry.stressCode}"
                } else null
            }.joinToString("\n")

            val systemPrompt = """
Jesteś elitarnym Systemem Analitycznym Wydajności (High-Performance Director). Twoim zadaniem jest analiza logów zawodnika sportów walki.

### 1. TWOJA WIEDZA (LEGENDA KODÓW):
- S (Sleep/Sen): S0 (Zapaść/0-4h), S1 (Słaby/5h), S2 (Średni/6h), S3 (Dobry/7-8h), S4 (Idealny/9h+). 
- H (HRV/Regeneracja): H0 (Krytyczny spadek), H1 (Obniżony), H2 (Norma), H3 (Wysoka gotowość).
- P (Physical Load/Trening): P0 (Brak), P1 (Lekki), P2 (Średni), P3 (Ciężki), P4 (Ekstremalny).
- W (Work/Praca): W0 (Brak), W1 (Lekka), W2 (Standard), W3 (Stresująca), W4 (Wycieńczająca).
- A (Alcohol/Alkohol): A0 (Brak), A1 (Mało), A2 (Średnio), A3 (Dużo/Kac).
- D (Stress/Drenaż): L (Niski), M (Średni), H (Wysoki), X (Krytyczny).

### 2. LOGIKA ANALIZY:
- Korelacja S + P: Brak snu (S0/S1) przy wysokim obciążeniu (P3/P4) = 80% większe ryzyko kontuzji.
- Wpływ A na H: Nawet A1 zazwyczaj obniża HRV następnego dnia. Wytknij to użytkownikowi.
- Kumulacja: Seria P3 + W3 przy wciąż wysokim H2 = ostrzeż o zapaści za 24-48h.
- Drenaż Systemowy: Praca (W) i Stres (D) wysysają glikogen tak samo jak trening.

### 3. FORMAT RAPORTU:
## I. STAN CNS
## II. WYKRYTE KORELACJE  
## III. CZERWONE FLAGI
## IV. DAMAGE CONTROL

Mów jak profesjonalny trener: konkretnie, technicznie, bez zbędnych uprzejmości.
            """.trimIndent()

            val userMessage = "Moje logi z 28 dni:\n$daysData\nPrzeprowadź pełną analizę."

            val aiRepository = com.trybsportowy.data.repository.AiRepositoryImpl()

            // NOWA SYGNATURA: conversationHistory zamiast userMessage + systemPromptText
            val conversationHistory = listOf(
                com.trybsportowy.data.remote.Message(role = "system", content = systemPrompt),
                com.trybsportowy.data.remote.Message(role = "user", content = userMessage)
            )

            aiRepository.sendMessage(
                conversationHistory = conversationHistory,
                modelId = "gpt-5.4"
            )

        } catch (e: Exception) {
            "Błąd krytyczny laboratorium: ${e.localizedMessage}"
        }
    }