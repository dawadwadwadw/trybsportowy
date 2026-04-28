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
import com.trybsportowy.presentation.quickentry.QuickEntryActivity
import com.trybsportowy.presentation.settings.SettingsScreen
import com.trybsportowy.presentation.settings.SettingsViewModel
import com.trybsportowy.ui.theme.TrybsportowyTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TrybsportowyApplication
        // Wewnątrz MainActivity -> onCreate -> setContent:
        setContent {
            TrybsportowyTheme {
                var showSettings by remember { mutableStateOf(false) }
                var showProDashboard by remember { mutableStateOf(false) } // NOWY STAN

                if (showSettings) {
                    SettingsScreen(viewModel = SettingsViewModel(app.repository), onBack = { showSettings = false })
                } else if (showProDashboard) {
                    // TUTAJ OTWIERAMY NOWY WIDOK
                    ProDashboardScreen(
                        app = app,
                        onBack = { showProDashboard = false }
                    )
                } else {
                    MainScreen(
                        app = app,
                        onSettingsClick = { showSettings = true },
                        onChartClick = { showProDashboard = true } // PRZEKAZUJEMY KLIKNIĘCIE
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(app: TrybsportowyApplication, onSettingsClick: () -> Unit, onChartClick: () -> Unit) {
    var history by remember { mutableStateOf<List<DailyReadinessEntity>>(emptyList()) }
    var settings by remember { mutableStateOf(DecaySettingsEntity()) }
    val calculator = remember { CalculateReadinessUseCase() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var showDatePicker by remember { mutableStateOf(false) }
    var showLegend by remember { mutableStateOf(false) } // Stan dla Legendy
    val datePickerState = rememberDatePickerState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    history = app.repository.getReadinessSince(0)
                    settings = app.repository.getDecaySettings()
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
                    IconButton(onClick = { showLegend = true }) { Icon(Icons.Default.Info, contentDescription = "Legenda") }
                    IconButton(onClick = { context.startActivity(Intent(context, ChatActivity::class.java)) }) { Text("🤖", fontSize = 24.sp) }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, contentDescription = "Ustawienia") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDatePicker = true }) { Text("+") }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {

            // DODAJ TĘ LINIJKĘ (żeby wyświetlić mały wykres na górze listy):
            item {
                ReadinessChart(history, settings, calculator, onChartClick = onChartClick)
            }

            items(history) { item ->
                // reszta Twojego kodu...
                val dayScore = calculator.execute(listOf(item), settings, item.dateTimestamp)
                Box(modifier = Modifier.clickable {
                    val intent = Intent(context, QuickEntryActivity::class.java).apply { putExtra("EXTRA_DATE", item.dateTimestamp) }
                    context.startActivity(intent)
                }) {
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
                    val selectedMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    val intent = Intent(context, QuickEntryActivity::class.java).apply { putExtra("EXTRA_DATE", selectedMillis) }
                    context.startActivity(intent)
                }) { Text("DALEJ") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("ANULUJ") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showLegend) {
        LegendBottomSheet(onDismiss = { showLegend = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegendBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTabIndex by remember { mutableStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Szybka Ściągawka") })
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Fizjologia (PRO)") })
            }
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                if (selectedTabIndex == 0) {
                    Text("💤 Sen (Bateria)\nS4 (9h+): Ekstremalna regeneracja\nS3 (8-9h): Bardzo dobrze\nS2 (7-8h): Optymalnie\nS1 (6-7h): Deficyt\nS0 (<6h): Zapaść\n\n🫀 HRV (Gotowość CNS)\nH3 (8-10): Szczytowa forma\nH2 (6-7): Neutralnie\nH1 (4-5): Ostrzeżenie\nH0 (1-3): Zapaść / Choroba!\n\n🥊 Trening (Wczoraj)\nP0: Brak/rozruch\nP1: Lekki flow\nP2: Mocno (Klubowy)\nP3: 2 Mocne treningi, Bardzo mocny trening\nP4: Ekstremum (Zawody) / 3 treningi\n\n🚴 Praca (Wczoraj)\nW0: Wolne\nW1 (<4h): Lekko\nW2 (4-6h): Średnio\nW3 (6-8h): Ciężka zmiana\nW4 (10h+): Maraton\n\n🍺 Alkohol (Wczoraj)\nA0: Zero\nA1: Lekko (1-2 piwa)\nA2: Średnio\nA3: Mocno (Kac)\n\n🔥 Stres / Choroba\nL: Niski (Chill)\nM: Średni\nH: Wysoki\nX: Ekstremalny (Choroba)")
                } else {
                    Text(
                        text = "🔥 KOD D: STRES SYSTEMOWY I DIETA\n" +
                                "Mózg nie odróżnia stresu od wysiłku. Zła dieta i odwodnienie to też stres.\n" +
                                "L: Czysta karta, dobra dieta, luz. Ciało w trybie naprawy.\n" +
                                "M: Standardowy dzień w biegu, trochę nauki, zwykłe obciążenie operacyjne.\n" +
                                "H: Czerwona strefa. Egzaminy, nerwy ALBO fatalna dieta (fast food, odwodnienie). Organizm gasi pożary w ciele, regeneracja fizyczna stoi.\n" +
                                "X: System Crash. Choroba, gorączka. Trening w tym stanie to uszkodzenie serca.\n\n" +

                                "🍺 KOD A: ALKOHOL (Bloker Adaptacji)\n" +
                                "A0: Czysta wątroba, idealna synteza białek.\n" +
                                "A1: Lekki strzał (1-2 piwa), małe opóźnienie regeneracji.\n" +
                                "A2: Średnio. Zniszczona faza REM, podwyższone tętno w nocy. Okno anaboliczne zamknięte.\n" +
                                "A3: Toksykologia (Kac). Silne odwodnienie, skasowany progres z 48h. Trening = niszczenie CNS.\n\n" +

                                "🥊 KOD P: OBCIĄŻENIE FIZYCZNE\n" +
                                "P0: Pełny rest.\n" +
                                "P1: Zone 1/2. Lekki flow, technika, rolowanie.\n" +
                                "P2: Solidna jednostka klubowa lub mocny WF.\n" +
                                "P3: Double Session (2 treningi) lub rzeźnia (np. shark tank).\n" +
                                "P4: Triple Threat (3 jednostki) lub dzień zawodów.\n\n" +

                                "🚴 KOD W: PRACA (Dostawy / Zone 2)\n" +
                                "Praca tlenowa drenuje z czasem Twój układ nerwowy i glikogen.\n" +
                                "W1 (<4h): Aktywna regeneracja.\n" +
                                "W2 (4-6h): Solidna dniówka. Wymaga dołożenia węglowodanów.\n" +
                                "W3 (6-8h): Ciężki drenaż (zwłaszcza w deszcz/wiatr). Zmęczenie powięzi.\n" +
                                "W4 (10h+): Ultra-maraton. Katabolizm mięśni. Trening P3 po tej pracy to błąd.",
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun ReadinessRow(item: DailyReadinessEntity, score: Float) {
    val dateStr = Instant.ofEpochMilli(item.dateTimestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM (EEEE)"))
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = dateStr, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Sen: ${item.sleepCode} | HRV: ${item.hrvCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            Text(text = "${score.toInt()} pkt", style = MaterialTheme.typography.headlineSmall, color = if (score >= 0) Color(0xFF81C784) else Color(0xFFE57373))
        }
    }
}

@Composable
fun ReadinessChart(history: List<DailyReadinessEntity>, settings: DecaySettingsEntity, calculator: CalculateReadinessUseCase, onChartClick: () -> Unit) {
    val scrollState = rememberScrollState()
    val today = LocalDate.now()
    val last14Days = (0..13).map { today.minusDays(it.toLong()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .clickable { onChartClick() } // KLIKNIĘCIE OTWIERA PEŁNY EKRAN PRO!
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Trend formy", style = MaterialTheme.typography.titleMedium)
            Text(text = "DASHBOARD PRO 🚀", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(16.dp))

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
                    score >= 50f -> Color(0xFF81C784)
                    score < 0f -> Color(0xFFFFB74D)
                    else -> Color(0xFFAED581)
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
                        modifier = Modifier.size(36.dp).background(barColor, shape = RoundedCornerShape(18.dp))
                    ) {
                        Text(text = score.toInt().toString(), color = Color.Black, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = dayName, style = MaterialTheme.typography.bodySmall, color = if (isWeekend) Color(0xFFE57373) else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (isWeekend) FontWeight.Bold else FontWeight.Normal)
                    Text(text = dateNum, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        }
    }
}
