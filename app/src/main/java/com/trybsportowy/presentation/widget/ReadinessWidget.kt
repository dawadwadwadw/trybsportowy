package com.trybsportowy.presentation.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.trybsportowy.MainActivity
import com.trybsportowy.TrybsportowyApplication
import com.trybsportowy.domain.usecase.CalculateReadinessUseCase
import com.trybsportowy.presentation.chat.ChatActivity
import com.trybsportowy.presentation.quickentry.QuickEntryActivity
import java.time.LocalDate
import java.time.ZoneId

class ReadinessWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReadinessWidget()
}

class ReadinessWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as TrybsportowyApplication
        val repository = app.repository
        val settings = repository.getDecaySettings()

        val todayTimestamp = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val yesterdayTimestamp = todayTimestamp - 86400000L
        val fourDaysAgo = todayTimestamp - (3 * 86400000L)

        val daysData = repository.getReadinessSince(fourDaysAgo)

        val scoreToday = CalculateReadinessUseCase().execute(daysData, settings, todayTimestamp)
        val scoreYesterday = CalculateReadinessUseCase().execute(daysData, settings, yesterdayTimestamp)

        provideContent {
            GlanceTheme {
                WidgetUI(scoreToday, scoreYesterday)
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun WidgetUI(score: Float, yesterdayScore: Float) {
    val context = LocalContext.current

    val colorRed = Color(0xFFF44336)
    val colorYellow = Color(0xFFFFC107)
    val colorGreen = Color(0xFF4CAF50)
    val darkSurface = Color(0x40000000) // Półprzezroczysty czarny dla kapsułek

    val bgColor = when {
        score <= -50f -> colorRed
        score >= 50f -> colorGreen
        score < 0f -> lerp(colorRed, colorYellow, (score + 50f) / 50f)
        else -> lerp(colorYellow, colorGreen, score / 50f)
    }

    val trendDiff = score - yesterdayScore
    val trendArrow = if (trendDiff >= 0) "⬆️" else "⬇️"
    val trendSign = if (trendDiff >= 0) "+" else ""

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .padding(16.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("READINESS", style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // WIELKI WYNIK
        Text(
            text = score.toInt().toString(),
            style = TextStyle(color = ColorProvider(Color.White), fontSize = 64.sp, fontWeight = FontWeight.Bold)
        )

        // STRZAŁKA TRENDU WZGLĘDEM WCZORAJ
        val trendColor = if (trendDiff >= 0) Color(0xFF81C784) else Color(0xFFFF5252) // Zielony lub Jasnoczerwony


        Row(

            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$trendArrow $trendSign${trendDiff.toInt()} od wczoraj",
                style = TextStyle(
                    color = ColorProvider(trendColor),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        // KAPSUŁKI AKCJI
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = GlanceModifier.defaultWeight())

            // Kapsułka AI
            Row(
                modifier = GlanceModifier
                    .background(darkSurface)
                    .cornerRadius(16.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable(actionStartActivity(Intent(context, ChatActivity::class.java))),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎀", style = TextStyle(color = ColorProvider(Color.White), fontSize = 16.sp, fontWeight = FontWeight.Bold))
            }

            Spacer(modifier = GlanceModifier.width(16.dp))

            // Kapsułka DODAJ
            Row(
                modifier = GlanceModifier
                    .background(darkSurface)
                    .cornerRadius(16.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable(actionStartActivity(Intent(context, QuickEntryActivity::class.java))),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("👀", style = TextStyle(color = ColorProvider(Color.White), fontSize = 16.sp, fontWeight = FontWeight.Bold))
            }

            Spacer(modifier = GlanceModifier.defaultWeight())
        }
    }
}