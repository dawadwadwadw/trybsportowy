package com.trybsportowy.domain.usecase

import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.data.local.DecaySettingsEntity

class CalculateReadinessUseCase {

    fun execute(
        daysData: List<DailyReadinessEntity>,
        settings: DecaySettingsEntity,
        todayTimestamp: Long
    ): Float {
        var totalScore = 0f
        val msInDay = 86400000L

        for (day in daysData) {
            val daysAgo = ((todayTimestamp - day.dateTimestamp) / msInDay).toInt()
            if (daysAgo > 3) continue
            totalScore += calculateDayScore(day, daysAgo, settings)
        }

        return totalScore
    }

    private fun calculateDayScore(day: DailyReadinessEntity, daysAgo: Int, settings: DecaySettingsEntity): Float {
        // ─── Punkty bazowe (istniejące kody) ─────────────────────────────────
        val sleepPoints = when (day.sleepCode) {
            "S1" -> 5f; "S2" -> 20f; "S3" -> 25f; "S4" -> 35f
            else -> 0f
        }
        val hrvPoints = when (day.hrvCode) {
            "H0" -> -35f; "H1" -> -15f; "H3" -> 15f
            else -> 0f // H2 to 0
        }
        val stressPoints = when (day.stressCode) {
            "M" -> 10f; "H" -> 25f; "X" -> 40f
            else -> 0f // L to 0
        }
        val workPoints = when (day.workCode) {
            "W1" -> 5f; "W2" -> 15f; "W3" -> 30f; "W4" -> 55f
            else -> 0f
        }
        val alcoholPoints = when (day.alcoholCode) {
            "A1" -> 5f; "A2" -> 15f; "A3" -> 35f
            else -> 0f
        }
        val physicalPoints = when (day.physicalLoadCode) {
            "P1" -> 10f; "P2" -> 30f; "P3" -> 55f; "P4" -> 85f
            else -> 0f
        }

        // ─── Nowe modyfikatory (Etap 4) ───────────────────────────────────────
        // Kod N — dieta wpływa na regenerację
        val nutritionModifier = when (day.nutritionCode) {
            "N0" -> +5f
            "N1" -> 0f
            "N2" -> -8f
            "N3" -> -15f
            else -> 0f
        }
        // CNS Drain — fundament gotowości (waga 1.5)
        val cnsDrainModifier = -(day.cnsDrain * 1.5f)
        // Body Drain — aparat ruchu (waga 1.0)
        val bodyDrainModifier = -(day.bodyDrain * 1.0f)

        val positiveBase = sleepPoints + hrvPoints + nutritionModifier
        val negativeBase = stressPoints + workPoints + alcoholPoints + physicalPoints +
                           cnsDrainModifier + bodyDrainModifier

        // ─── Mnożniki rozpadu ─────────────────────────────────────────────────
        return when (daysAgo) {
            0 -> positiveBase - negativeBase
            1 -> {
                val sleepAdjusted = sleepPoints * settings.weightYesterdaySleep
                val hrvAdjusted = hrvPoints * settings.weightYesterday
                val negativeAdjusted = negativeBase * settings.weightYesterday
                (sleepAdjusted + hrvAdjusted) - negativeAdjusted
            }
            2 -> (positiveBase - negativeBase) * settings.weightTwoDaysAgo
            3 -> (positiveBase - negativeBase) * settings.weightThreeDaysAgo
            else -> 0f
        }
    }
}