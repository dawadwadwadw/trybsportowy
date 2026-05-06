package com.trybsportowy.presentation.dashboard

// implementation("androidx.datastore:datastore-preferences:1.1.1")
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trybsportowy.TrybsportowyApplication
import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.data.local.DecaySettingsEntity
import com.trybsportowy.domain.usecase.CalculateReadinessUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_report_prefs")

object AiReportStore {
    private val REPORT_KEY = stringPreferencesKey("ai_report")
    private val TIMESTAMP_KEY = longPreferencesKey("ai_timestamp")

    suspend fun saveReport(context: Context, report: String, timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[REPORT_KEY] = report
            prefs[TIMESTAMP_KEY] = timestamp
        }
    }

    suspend fun loadReport(context: Context): Pair<String, Long>? {
        val prefs = context.dataStore.data.first()
        val report = prefs[REPORT_KEY]
        val timestamp = prefs[TIMESTAMP_KEY]
        return if (report != null && timestamp != null) Pair(report, timestamp) else null
    }
}

// ─── Kolory systemowe ─────────────────────────────────────────────────────────
private val C_P = Color(0xFFEF5350)
private val C_W = Color(0xFF42A5F5)
private val C_A = Color(0xFFAB47BC)
private val C_D = Color(0xFFFFCA28)

private fun hrvColor(c: String) = when(c) {
    "H3" -> Color(0xFF00E676); "H1" -> Color(0xFFFFB74D)
    "H0" -> Color(0xFFEF5350); else -> Color(0xFFB0BEC5)
}
private fun nutritionColor(c: String) = when(c) {
    "N0" -> Color(0xFF66BB6A); "N2" -> Color(0xFFFFB74D)
    "N3" -> Color(0xFFEF5350); else -> Color(0xFFB0BEC5)
}
private fun readinessColor(s: Float) = when {
    s >= 70f -> Color(0xFF1B5E20); s >= 40f -> Color(0xFF66BB6A)
    s >= 10f -> Color(0xFFA5D6A7); s >= 0f  -> Color(0xFFCFD8DC)
    s >= -30f -> Color(0xFFFFB74D); else -> Color(0xFFEF5350)
}

// ─── Point helpers ────────────────────────────────────────────────────────────
fun sleepPts(c: String)    = when(c) { "S1"->5f;"S2"->20f;"S3"->25f;"S4"->35f; else->0f }
fun hrvPts(c: String)      = when(c) { "H0"->-35f;"H1"->-15f;"H3"->15f; else->0f }
fun physPts(c: String)     = when(c) { "P1"->10f;"P2"->30f;"P3"->55f;"P4"->85f; else->0f }
fun workPts(c: String)     = when(c) { "W1"->5f;"W2"->15f;"W3"->30f;"W4"->55f; else->0f }
fun alcoholPts(c: String)  = when(c) { "A1"->5f;"A2"->15f;"A3"->35f; else->0f }
fun nutritionPts(c: String)= when(c) { "N0"->5f;"N2"->-8f;"N3"->-15f; else->0f }

// ─── Data ─────────────────────────────────────────────────────────────────────
data class DayLoad(val pPts: Float, val wPts: Float, val aPts: Float, val dPts: Float)

fun calculateStackedLoad(e: DailyReadinessEntity?): DayLoad {
    if (e == null) return DayLoad(0f, 0f, 0f, 0f)
    val stress = when(e.stressCode) { "M"->10f;"H"->25f;"X"->40f; else->0f }
    return DayLoad(physPts(e.physicalLoadCode), workPts(e.workCode), alcoholPts(e.alcoholCode), stress)
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProDashboardScreen(app: TrybsportowyApplication, onBack: () -> Unit) {
    val history  = remember { mutableStateListOf<DailyReadinessEntity>() }
    val settings = remember { mutableStateOf(DecaySettingsEntity()) }
    val calc     = remember { CalculateReadinessUseCase() }
    var loading  by remember { mutableStateOf(true) }
    var error    by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<DailyReadinessEntity?>(null) }

    LaunchedEffect(Unit) {
        try {
            val data = withContext(Dispatchers.IO) { app.repository.getReadinessSince(0) }
            val sett = withContext(Dispatchers.IO) { app.repository.getDecaySettings() }
            history.addAll(data)
            settings.value = sett
        } catch (e: Exception) {
            error = e.localizedMessage
        } finally {
            loading = false
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("DASHBOARD PRO", fontWeight = FontWeight.Black) },
            navigationIcon = { IconButton(onClick = onBack) { Text("✕", fontSize = 20.sp) } }
        )
    }) { pad ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), Alignment.Center) {
                Text("⚠️ Błąd: $error", color = C_P, textAlign = TextAlign.Center)
            }
            else -> Column(
                Modifier.padding(pad).fillMaxSize()
                    .background(Color(0xFF121212)).verticalScroll(rememberScrollState())
            ) {
                MirrorChart(history, settings.value, calc, onDayClick = { selected = it })
                InjuryGuardWidget(history)
                AiDetectiveWidget(history)
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    selected?.let {
        DayDetailBottomSheet(it, calc, settings.value, history, onDismiss = { selected = null })
    }
}

// ─── Legend row ───────────────────────────────────────────────────────────────
@Composable
private fun ChartLegend() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .background(Color(0xFF1C1C1C), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendPill(C_P, "P=Trening")
        LegendPill(C_W, "W=Praca")
        LegendPill(C_A, "A=Alkohol")
        LegendPill(C_D, "D=Stres")
        
        LegendPill(Color(0xFF66BB6A), "H=HRV")
        LegendPill(Color(0xFFFFB74D), "N=Dieta")
    }
}

@Composable
private fun LegendPill(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(7.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─── MirrorChart ──────────────────────────────────────────────────────────────
@Composable
fun MirrorChart(
    history: List<DailyReadinessEntity>,
    settings: DecaySettingsEntity,
    calculator: CalculateReadinessUseCase,
    onDayClick: (DailyReadinessEntity?) -> Unit
) {
    val scrollState = rememberScrollState()
    val today = LocalDate.now()
    val days  = (0..20).map { today.minusDays(it.toLong()) }

    Column(Modifier.fillMaxWidth()) {
        ChartLegend()
        Row(Modifier.horizontalScroll(scrollState).padding(horizontal = 6.dp, vertical = 4.dp)) {
            days.forEach { date ->
                val ts  = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val ent = history.find {
                    Instant.ofEpochMilli(it.dateTimestamp).atZone(ZoneId.systemDefault()).toLocalDate() == date
                }
                val score = if (history.any { it.dateTimestamp <= ts }) {
                    calculator.execute(history.filter { it.dateTimestamp <= ts }, settings, ts)
                } else 0f
                val load = calculateStackedLoad(ent)
                DayColumn(date, score, load, ent, date == today, date.dayOfWeek.value >= 6) {
                    onDayClick(ent)
                }
            }
        }
    }
}

// ─── DayColumn ────────────────────────────────────────────────────────────────
@Composable
private fun DayColumn(
    date: LocalDate, score: Float, load: DayLoad,
    entity: DailyReadinessEntity?, isToday: Boolean, isWeekend: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier.width(72.dp).background(Color.Transparent, RoundedCornerShape(6.dp))
            .clickable { onClick() }.padding(horizontal = 3.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── TOP: Readiness ──────────────────────────────────────────────
        Box(Modifier.height(130.dp).fillMaxWidth(), Alignment.BottomCenter) {
            val color  = readinessColor(score)
            val height = (score.coerceIn(0f, 150f) / 150f * 120f).coerceAtLeast(if (score != 0f) 2f else 0f).dp
            if (score != 0f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text(
                        text = "${if (score > 0) "+" else ""}${score.toInt()}",
                        color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.width(8.dp).height(height).background(color, RoundedCornerShape(4.dp)))
                }
            }
        }

        // ── ZERO ────────────────────────────────────────────────────────
        HorizontalDivider(color = Color.Gray.copy(alpha = 0.5f), thickness = 1.dp)

        // ── BOTTOM: Load bars (P, W, A, D) ─────────────────────────────
        Box(Modifier.height(115.dp).fillMaxWidth(), Alignment.TopCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                LoadBarWithLabel(load.pPts, C_P)
                LoadBarWithLabel(load.wPts, C_W)
                LoadBarWithLabel(load.aPts, C_A)
                LoadBarWithLabel(load.dPts, C_D)
                if (entity?.hrvCode == "H0" || entity?.hrvCode == "H1") {
                    LoadBarWithLabel(kotlin.math.abs(hrvPts(entity.hrvCode)), Color(0xFF66BB6A))
                }
                if (entity?.nutritionCode == "N2" || entity?.nutritionCode == "N3") {
                    LoadBarWithLabel(kotlin.math.abs(nutritionPts(entity.nutritionCode)), Color(0xFFFFB74D))
                }
            }
        }

        // ── H + N chips ─────────────────────────────────────────────────
        if (entity != null) {
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Surface(color = hrvColor(entity.hrvCode).copy(alpha = 0.15f), shape = RoundedCornerShape(3.dp)) {
                    Text(entity.hrvCode, color = hrvColor(entity.hrvCode), fontSize = 7.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.width(22.dp).padding(horizontal = 4.dp, vertical = 2.dp))
                }
                Spacer(Modifier.width(3.dp))
                Surface(color = nutritionColor(entity.nutritionCode).copy(alpha = 0.15f), shape = RoundedCornerShape(3.dp)) {
                    Text(entity.nutritionCode, color = nutritionColor(entity.nutritionCode), fontSize = 7.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.width(22.dp).padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
        } else {
            Spacer(Modifier.height(14.dp))
        }

        // ── Date ─────────────────────────────────────────────────────────
        Text(date.format(DateTimeFormatter.ofPattern("dd.MM")), fontSize = 9.sp,
            color = if (isWeekend) C_P else Color.Gray)
        Text(date.format(DateTimeFormatter.ofPattern("E")), fontSize = 9.sp,
            color = if (isWeekend) C_P else Color.White,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun LoadBarWithLabel(pts: Float, color: Color) {
    if (pts <= 0f) return
    val h = (pts * 1.2f).coerceAtMost(42f).dp
    Box(Modifier.width(10.dp).height(h).background(color, RoundedCornerShape(2.dp)))
}

// ─── InjuryGuard ──────────────────────────────────────────────────────────────
@Composable
fun InjuryGuardWidget(history: List<DailyReadinessEntity>) {
    val today = LocalDate.now()
    var acutePts  = 0f; var chronicPts = 0f
    var acuteP = 0f; var acuteW = 0f; var acuteA = 0f; var acuteD = 0f

    for (i in 0..27) {
        val date = today.minusDays(i.toLong())
        val ent  = history.find {
            Instant.ofEpochMilli(it.dateTimestamp).atZone(ZoneId.systemDefault()).toLocalDate() == date
        }
        val load = calculateStackedLoad(ent)
        val day  = load.pPts + load.wPts + load.aPts + load.dPts
        chronicPts += day
        if (i < 7) {
            acutePts += day
            acuteP += load.pPts; acuteW += load.wPts
            acuteA += load.aPts; acuteD += load.dPts
        }
    }
    val acuteAvg   = acutePts / 7f
    val chronicAvg = chronicPts / 28f
    val ratio      = if (chronicAvg > 0f) acuteAvg / chronicAvg else 0f

    val (statusColor, statusText) = when {
        ratio == 0f   -> Pair(Color.DarkGray,    "BRAK DANYCH")
        ratio < 0.8f  -> Pair(Color(0xFF64B5F6), "ROZTRENOWANIE – możesz docisnąć")
        ratio <= 1.3f -> Pair(Color(0xFF81C784),  "SWEET SPOT – idealny progres")
        ratio <= 1.5f -> Pair(Color(0xFFFFD54F),  "OSTRZEŻENIE – kumulacja obciążeń")
        else          -> Pair(C_P,               "STREFA KONTUZJI – hamuj!")
    }

    Card(Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
        Column(Modifier.padding(16.dp)) {
            Text("INJURY GUARD — A:C RATIO", style = MaterialTheme.typography.labelMedium,
                color = Color.Gray, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Bottom) {
                Text(String.format("%.2f", ratio), style = MaterialTheme.typography.headlineLarge,
                    color = statusColor, fontWeight = FontWeight.Black)
                Text(statusText, style = MaterialTheme.typography.bodySmall,
                    color = statusColor, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(10.dp))
            // Pasek
            Box(Modifier.fillMaxWidth().height(8.dp).background(Color.DarkGray, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth((ratio / 2f).coerceIn(0f,1f)).fillMaxHeight()
                    .background(statusColor, RoundedCornerShape(4.dp)))
            }
            Spacer(Modifier.height(10.dp))
            Text("7d: ${acuteAvg.toInt()} pkt  |  28d: ${chronicAvg.toInt()} pkt",
                style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

// ─── DayDetailBottomSheet ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailBottomSheet(
    entity: DailyReadinessEntity,
    calculator: CalculateReadinessUseCase,
    settings: DecaySettingsEntity,
    history: List<DailyReadinessEntity>,
    onDismiss: () -> Unit
) {
    val score = if (history.any { it.dateTimestamp <= entity.dateTimestamp }) {
        calculator.execute(history.filter { it.dateTimestamp <= entity.dateTimestamp }, settings, entity.dateTimestamp)
    } else 0f
    val dateStr = Instant.ofEpochMilli(entity.dateTimestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"))

    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1A1A1A)) {
        Column(Modifier.padding(20.dp).fillMaxWidth()) {
            Text(dateStr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            val sc = readinessColor(score)
            Text("${if (score > 0) "+" else ""}${score.toInt()} pkt",
                color = sc, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))

            DetailRow("💤 Sen",     entity.sleepCode,         sleepPts(entity.sleepCode),    Color(0xFF64B5F6), true)
            DetailRow("🫀 HRV",     entity.hrvCode,           hrvPts(entity.hrvCode),         hrvColor(entity.hrvCode), hrvPts(entity.hrvCode) >= 0)
            DetailRow("🏋️ Trening", entity.physicalLoadCode,  physPts(entity.physicalLoadCode), C_P, false)
            DetailRow("💼 Praca",   entity.workCode,          workPts(entity.workCode),       C_W, false)
            DetailRow("🍺 Alkohol", entity.alcoholCode,       alcoholPts(entity.alcoholCode), C_A, false)
            DetailRow("🥗 Dieta",   entity.nutritionCode,     nutritionPts(entity.nutritionCode), nutritionColor(entity.nutritionCode), nutritionPts(entity.nutritionCode) >= 0)

            if (entity.cnsDrain > 0 || entity.bodyDrain > 0) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                Spacer(Modifier.height(8.dp))
                DetailRow("🧠 CNS Drain",  "−${entity.cnsDrain}", -(entity.cnsDrain * 1.5f), Color(0xFFFF7043), false)
                DetailRow("💪 Body Drain", "−${entity.bodyDrain}", -(entity.bodyDrain.toFloat()), Color(0xFFFF8A65), false)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, code: String, pts: Float, color: Color, positive: Boolean) {
    val sign   = if (positive || pts > 0f) "+" else ""
    val ptsStr = "${sign}${pts.toInt()} pkt"
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, color = Color.LightGray, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
            Text(code, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(ptsStr, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
    }
}

// ─── AiDetectiveWidget ────────────────────────────────────────────────────────
@Composable
fun AiDetectiveWidget(history: List<DailyReadinessEntity>) {
    var isExpanded by remember { mutableStateOf(false) }
    var isLoading  by remember { mutableStateOf(false) }
    var aiReport   by remember { mutableStateOf<String?>(null) }
    var lastUpdate by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    LaunchedEffect(Unit) {
        val saved = AiReportStore.loadReport(context)
        if (saved != null) {
            aiReport = saved.first
            lastUpdate = saved.second
            isExpanded = true
        }
    }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        .clickable { if (aiReport != null) isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("🤖 DETEKTYW AI (KORELACJE)", style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFBA68C8), fontWeight = FontWeight.Bold)
                if (aiReport != null)
                    Text(if (isExpanded) "▲ Zwiń" else "▼ Rozwiń", color = Color.Gray, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))

            if (aiReport == null && !isLoading) {
                Button(onClick = {
                    isLoading = true; isExpanded = true
                    scope.launch {
                        val report = fetchAiReportFromApi(history)
                        aiReport = report
                        lastUpdate = System.currentTimeMillis()
                        AiReportStore.saveReport(context, report, lastUpdate!!)
                        isLoading = false
                    }
                }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))) {
                    Text("GENERUJ RAPORT AI", color = Color.White)
                }
            }
            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFBA68C8))
                }
            }
            if (aiReport != null && isExpanded && !isLoading) {
                Text(aiReport ?: "", style = MaterialTheme.typography.bodyMedium,
                    color = Color.White, lineHeight = 22.sp)
                Spacer(Modifier.height(16.dp))

                lastUpdate?.let { ts ->
                    val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
                    Text("Ostatnia analiza: $dateStr", color = Color.Gray, fontSize = 10.sp)
                    Spacer(Modifier.height(8.dp))
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        isLoading = true
                        scope.launch {
                            val report = fetchAiReportFromApi(history)
                            aiReport = report
                            lastUpdate = System.currentTimeMillis()
                            AiReportStore.saveReport(context, report, lastUpdate!!)
                            isLoading = false
                        }
                    }, Modifier.weight(1f)) {
                        Text("Odśwież analizę", color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(aiReport ?: ""))
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Kopiuj", tint = Color.Gray)
                    }
                }
            }
        }
    }
}

// ─── AI fetch ─────────────────────────────────────────────────────────────────
suspend fun fetchAiReportFromApi(history: List<DailyReadinessEntity>): String =
    withContext(Dispatchers.IO) {
        try {
            val today   = LocalDate.now()
            val daysData = (0..27).mapNotNull { i ->
                val date = today.minusDays(i.toLong())
                val e    = history.find {
                    Instant.ofEpochMilli(it.dateTimestamp).atZone(ZoneId.systemDefault()).toLocalDate() == date
                }
                e?.let {
                    "Dzień ${28-i} [$date]: S:${it.sleepCode}, H:${it.hrvCode}, " +
                            "P:${it.physicalLoadCode}, W:${it.workCode}, A:${it.alcoholCode}, D:${it.stressCode}"
                }
            }.joinToString("\n")

            val systemPrompt = """
Jesteś elitarnym Systemem Analitycznym Wydajności. Analizujesz logi zawodnika sportów walki.
KODY: S0-S4=sen, H0-H3=HRV, P0-P4=trening, W0-W4=praca, A0-A3=alkohol, D(L/M/H/X)=stres.
KORELACJE: S0/S1+P3/P4=ryzyko kontuzji. A->obniżone HRV dzień później. Seria P3+W3=zapaść za 48h.
FORMAT: ## I.STAN CNS ## II.KORELACJE ## III.CZERWONE FLAGI ## IV.DAMAGE CONTROL
Mów jak trener: konkretnie, technicznie.""".trimIndent()

            val aiRepository = com.trybsportowy.data.repository.AiRepositoryImpl()
            aiRepository.sendMessage(
                conversationHistory = listOf(
                    com.trybsportowy.data.remote.Message(role = "system", content = systemPrompt),
                    com.trybsportowy.data.remote.Message(role = "user",
                        content = "Moje logi z 28 dni:\n$daysData\nPrzeprowadź pełną analizę.")
                ),
                modelId = "gpt-5.4"
            )
        } catch (e: Exception) {
            "Błąd krytyczny: ${e.localizedMessage}"
        }
    }