package com.trybsportowy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.data.local.DecaySettingsEntity
import com.trybsportowy.domain.usecase.CalculateReadinessUseCase
import com.trybsportowy.presentation.chat.ChatActivity
import com.trybsportowy.presentation.dashboard.ProDashboardScreen
import com.trybsportowy.presentation.daydetail.DayDetailScreen
import com.trybsportowy.presentation.quickentry.QuickEntryActivity
import com.trybsportowy.presentation.settings.SettingsScreen
import com.trybsportowy.presentation.settings.SettingsViewModel
import com.trybsportowy.ui.theme.TrybsportowyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TrybsportowyApplication
        this.setContent {
            TrybsportowyTheme {
                var showSettings    by remember { mutableStateOf(false) }
                var showProDashboard by remember { mutableStateOf(false) }
                var detailEntity    by remember { mutableStateOf<DailyReadinessEntity?>(null) }
                var detailScore     by remember { mutableStateOf(0f) }

                when {
                    showSettings -> SettingsScreen(
                        viewModel = SettingsViewModel(app.repository),
                        onBack = { showSettings = false }
                    )

                    showProDashboard -> ProDashboardScreen(
                        app = app,
                        onBack = { showProDashboard = false }
                    )

                    detailEntity != null -> DayDetailScreen(
                        entity = detailEntity!!,
                        readinessScore = detailScore,
                        onBack = { detailEntity = null },
                        onDeleted = { detailEntity = null },
                        onDelete = {
                            withContext(Dispatchers.IO) {
                                app.repository.deleteDailyReadiness(detailEntity!!)
                            }
                        }
                    )

                    else -> MainScreen(
                        app = app,
                        onSettingsClick = { showSettings = true },
                        onChartClick    = { showProDashboard = true },
                        onDayClick      = { entity, score ->
                            detailEntity = entity
                            detailScore  = score
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    app: TrybsportowyApplication,
    onSettingsClick: () -> Unit,
    onChartClick: () -> Unit,
    onDayClick: (DailyReadinessEntity, Float) -> Unit
) {
    var history  by remember { mutableStateOf<List<DailyReadinessEntity>>(emptyList()) }
    var settings by remember { mutableStateOf(DecaySettingsEntity()) }
    val calculator  = remember { CalculateReadinessUseCase() }
    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var showDatePicker by remember { mutableStateOf(false) }
    var showLegend     by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    history  = withContext(Dispatchers.IO) { app.repository.getReadinessSince(0) }
                    settings = withContext(Dispatchers.IO) { app.repository.getDecaySettings() }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("trybsportowy") },
                actions = {
                    IconButton(onClick = { showLegend = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Legenda")
                    }
                    IconButton(onClick = { context.startActivity(Intent(context, ChatActivity::class.java)) }) {
                        Text("🤖", fontSize = 24.sp)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Ustawienia")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDatePicker = true }) { Text("+") }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                ReadinessChart(history, settings, calculator, onChartClick = onChartClick)
            }
            items(history) { item ->
                val dayScore = calculator.execute(listOf(item), settings, item.dateTimestamp)
                Box(modifier = Modifier.clickable { onDayClick(item, dayScore) }) {
                    ReadinessRow(item, dayScore)
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    val millis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    context.startActivity(
                        Intent(context, QuickEntryActivity::class.java).apply { putExtra("EXTRA_DATE", millis) }
                    )
                }) { Text("DALEJ") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("ANULUJ") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showLegend) {
        LegendBottomSheet(onDismiss = { showLegend = false })
    }
}

// ─── Legenda — Etap 6 ─────────────────────────────────────────────────────────

private data class LegendEntry(
    val code: String,
    val emoji: String,
    val name: String,
    val physiology: String,
    val trainingImpact: String
)

private val LEGEND_ENTRIES = listOf(
    // Sen
    LegendEntry("S4","💤","Ekstremalna regeneracja","9h+ snu maksymalizuje wydzielanie hormonu wzrostu i syntezę białek mięśniowych.","Zielone światło dla treningu mocy i techniki — CNS w pełni naładowany."),
    LegendEntry("S3","💤","Bardzo dobrze","8–9h snu zapewnia pełne cykle REM i regenerację układu nerwowego.","Możesz trenować z pełną intensywnością."),
    LegendEntry("S2","💤","Optymalnie","7–8h — minimalne optimum dla sportowca. Kortyzol poranny w normie.","Normalny trening, obserwuj HRV."),
    LegendEntry("S1","💤","Deficyt","6–7h zaburza syntezę glikogenu i podnosi kortyzol o ~20%.","Redukuj objętość. Unikaj P3/P4."),
    LegendEntry("S0","💤","Zapaść","Poniżej 6h — cortisol skacze, okno anaboliczne zamknięte, ryzyko kontuzji rośnie.","Tylko regeneracja aktywna. Żadnych ciężkich obciążeń."),
    // HRV
    LegendEntry("H3","🫀","Szczytowa forma","HRV 8–10: dominacja przywspółczulna — organizm w trybie naprawy i adaptacji.","Idealny moment na ciężki trening lub zawody."),
    LegendEntry("H2","🫀","Neutralnie","HRV 6–7: układ autonomiczny w równowadze. Brak wyraźnych sygnałów.","Standardowy trening zgodnie z planem."),
    LegendEntry("H1","🫀","Ostrzeżenie","HRV 4–5: wzrost aktywności współczulnej — ciało walczy ze stresem lub zmęczeniem.","Obniż intensywność, sprawdź sen i stres."),
    LegendEntry("H0","🫀","Zapaść","HRV 1–3: kryzys ANS. Często infekcja lub skrajne przetrenowanie.","Pełny odpoczynek. Żadnego treningu."),
    // Trening
    LegendEntry("P4","💀","Ekstremum","Zawody / 2 pełne treningi — drenaż glikogenu i mikrouszkodzenia włókien na poziomie maksymalnym.","48–72h pełnej regeneracji. Ryzyko przetrenowania niefunkcjonalnego."),
    LegendEntry("P3","🏋️","Mocno","Ciężka siłownia + bieganie tego samego dnia. Wysoka produkcja mleczanu, katabolizm.","Następny dzień: tylko lekka aktywność lub rest."),
    LegendEntry("P2","🏋️","Solidnie","Normalna jednostka klubowa lub intensywny trening siłowy.","Standardowe okno regeneracji 24h."),
    LegendEntry("P1","🏃","Lekko","Zone 2, technika, lekki basen — aktywna regeneracja.","Nie nakłada się istotnie na następny dzień."),
    LegendEntry("P0","🏃","Brak / Rozruch","Pełen odpoczynek lub rozciąganie.","CNS w trybie naprawy."),
    // Praca
    LegendEntry("W4","💼","Maraton pracy","10h+ pracy fizycznej lub umysłowej wyczerpuje glikogen wątrobowy i drenuje kortyzol.","Trening P3 po W4 to błąd — katabolizm i ryzyko kontuzji."),
    LegendEntry("W3","💼","Ciężko","6–8h: istotny drenaż neurochemiczny — dopamina i norepinefryna zużyte.","Redukuj objętość treningu technicznego."),
    LegendEntry("W2","💼","Normalnie","4–6h: standardowe obciążenie operacyjne, znikomy wpływ na regenerację.","Trening zgodnie z planem."),
    LegendEntry("W1","💼","Lekko","Poniżej 4h: możliwa nawet aktywna regeneracja.","Brak ograniczeń."),
    LegendEntry("W0","💼","Wolne / Home","Minimalny wydatek energetyczny. Ciało może skupić się na naprawie.","Optymalny dzień na regenerację lub ciężki trening."),
    // Alkohol
    LegendEntry("A3","🍺","Kac","Silne odwodnienie + aldehyd octowy blokuje syntezę białek przez 48h.","Trening = niszczenie CNS. Tylko nawadnianie."),
    LegendEntry("A2","🍺","Średnio","Zaburzona faza REM, podwyższone tętno w nocy, zamknięte okno anaboliczne.","Zredukuj intensywność, obserwuj HRV rano."),
    LegendEntry("A1","🍺","Lekko","1–2 piwa — niewielkie opóźnienie regeneracji, HRV może spaść o 5–10%.","Lekkie treningi, obserwuj dane następnego dnia."),
    LegendEntry("A0","🍺","Zero","Czysta wątroba, idealna synteza białek i glikogenu.","Brak ograniczeń."),
    // Dieta
    LegendEntry("N0","🥗","Ideał","Pełne makro, 2.5L+, zero śmieciowego — maksymalna resynteza glikogenu i synteza białek.","Odżywienie wspiera każdą intensywność treningu."),
    LegendEntry("N1","🍽️","Normalnie","Przeciętna dieta, ok nawodnienie — drobne niedobory możliwe.","Brak istotnego wpływu na trening."),
    LegendEntry("N2","🍔","Słabo","Fast food, odwodnienie, niedobór białka — stan zapalny rośnie, resynteza glikogenu spowolniona.","Ogranicz objętość. Ciało nie ma materiału do naprawy."),
    LegendEntry("N3","⚠️","Kryzys","Głodzenie lub brak jedzenia przed treningiem — hipoglikemia, katabolizm mięśni, ryzyko omdlenia.","STOP. Nie trenuj w tej sytuacji."),
    // Drenaż
    LegendEntry("D:L","🟢","Niski drenaż","CNS + Body łącznie 0–3 pkt — ciało w trybie spokojnej adaptacji.","Zielone światło dla każdego treningu."),
    LegendEntry("D:M","🟡","Średni drenaż","4–7 pkt — widoczne obciążenie systemowe, ale w granicach normy.","Obserwuj. Nie dodawaj ekstra objętości."),
    LegendEntry("D:H","🟠","Wysoki drenaż","8–13 pkt — organizm walczy na wielu frontach jednocześnie.","Zredukuj do P1/P2. Priorytet: sen i nawodnienie."),
    LegendEntry("D:X","🔴","Kryzys systemowy","14+ pkt — przetrenowanie funkcjonalne lub choroba.","Pełen odpoczynek. Trening pogłębi problem.")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegendBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Ściągawka") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Fizjologia PRO") })
            }

            if (selectedTab == 0) {
                // Szybka ściągawka — kompaktowa tabela
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val groups = listOf(
                        "💤 Sen" to LEGEND_ENTRIES.filter { it.code.startsWith("S") },
                        "🫀 HRV" to LEGEND_ENTRIES.filter { it.code.startsWith("H") },
                        "🏃 Trening" to LEGEND_ENTRIES.filter { it.code.startsWith("P") },
                        "💼 Praca" to LEGEND_ENTRIES.filter { it.code.startsWith("W") },
                        "🍺 Alkohol" to LEGEND_ENTRIES.filter { it.code.startsWith("A") },
                        "🥗 Dieta" to LEGEND_ENTRIES.filter { it.code.startsWith("N") },
                        "🔥 Drenaż D" to LEGEND_ENTRIES.filter { it.code.startsWith("D:") }
                    )
                    groups.forEach { (groupName, entries) ->
                        item {
                            Text(
                                groupName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }
                        items(entries) { e ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(e.code, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                                Text(e.emoji, modifier = Modifier.width(24.dp))
                                Text(e.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            } else {
                // Fizjologia PRO — karty z opisem
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(LEGEND_ENTRIES) { entry ->
                        LegendCard(entry)
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LegendCard(entry: LegendEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = entry.code,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(entry.emoji, fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text(entry.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Text(
                text = "🔬 ${entry.physiology}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "🏋️ ${entry.trainingImpact}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── ReadinessRow ─────────────────────────────────────────────────────────────

@Composable
fun ReadinessRow(item: DailyReadinessEntity, score: Float) {
    val dateStr = Instant.ofEpochMilli(item.dateTimestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd.MM (EEEE)"))
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(dateStr, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Sen: ${item.sleepCode} | HRV: ${item.hrvCode} | ${item.nutritionCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Text(
                "${score.toInt()} pkt",
                style = MaterialTheme.typography.headlineSmall,
                color = if (score >= 0) Color(0xFF81C784) else Color(0xFFE57373)
            )
        }
    }
}

// ─── ReadinessChart ───────────────────────────────────────────────────────────

@Composable
fun ReadinessChart(
    history: List<DailyReadinessEntity>,
    settings: DecaySettingsEntity,
    calculator: CalculateReadinessUseCase,
    onChartClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val today = LocalDate.now()
    val last14Days = (0..13).map { today.minusDays(it.toLong()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .clickable { onChartClick() }
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Trend formy", style = MaterialTheme.typography.titleMedium)
            Text("DASHBOARD PRO 🚀", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            last14Days.forEach { date ->
                val isWeekend = date.dayOfWeek == java.time.DayOfWeek.SATURDAY || date.dayOfWeek == java.time.DayOfWeek.SUNDAY
                val timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val daysData = history.filter { it.dateTimestamp <= timestamp }
                val score = if (daysData.isNotEmpty()) calculator.execute(daysData, settings, timestamp) else 0f
                val barColor = when {
                    score <= -50f -> Color(0xFFE57373)
                    score >= 50f  -> Color(0xFF81C784)
                    score < 0f   -> Color(0xFFFFB74D)
                    else         -> Color(0xFFAED581)
                }
                val dayName = date.format(DateTimeFormatter.ofPattern("E")).take(2).replaceFirstChar { it.uppercase() }
                val dateNum = date.format(DateTimeFormatter.ofPattern("dd.MM"))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(48.dp)
                        .background(if (isWeekend) Color(0x1AE57373) else Color.Transparent, RoundedCornerShape(8.dp))
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(36.dp).background(barColor, RoundedCornerShape(18.dp))
                    ) {
                        Text(score.toInt().toString(), color = Color.Black, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(dayName, style = MaterialTheme.typography.bodySmall, color = if (isWeekend) Color(0xFFE57373) else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (isWeekend) FontWeight.Bold else FontWeight.Normal)
                    Text(dateNum, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        }
    }
}
