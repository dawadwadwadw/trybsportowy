package com.trybsportowy.domain.usecase

import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.data.local.DecaySettingsEntity
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Full result of the readiness computation for a given day.
 *
 * This mirrors the canonical algorithm spec (CLAUDE.md §4.1.1) field-for-field so the phone
 * stays bit-identical with the server's `readiness_calc.py`. All numbers are [Double];
 * only [dCode] is rounded (HALF_UP, 1 decimal).
 */
data class ReadinessResult(
    val baseScore: Double,
    val readinessScore: Double,
    val totalCns: Double,
    val totalBody: Double,
    val overloadTriggered: Boolean,
    val dCode: Double
)

/**
 * Computes the readiness score over a 4-day fatigue-decay window.
 *
 * Conforms to CLAUDE.md §4.1.1 (the server is canonical; the phone must match it for every
 * fixture in `algorithm_v1_fixtures.json`). Notable points:
 *  - `stressCode` is intentionally NOT read: it has no slot in the algorithm spec, the server
 *    contract, or the Master Blueprint. It remains on the entity but no longer affects scoring.
 *  - cnsDrain / bodyDrain do NOT contribute to the base score; they only drive the CNS
 *    overload penalty and the D-Code.
 *  - Decay weights come from [DecaySettingsEntity]; its defaults equal the canonical weights,
 *    including the deliberate Day -1 sleep exception (0.8, not 0.75).
 */
class CalculateReadinessUseCase {

    /** Backward-compatible entry point: returns only the final readiness score. */
    fun execute(
        daysData: List<DailyReadinessEntity>,
        settings: DecaySettingsEntity,
        todayTimestamp: Long
    ): Float = calculate(daysData, settings, todayTimestamp).readinessScore.toFloat()

    fun calculate(
        daysData: List<DailyReadinessEntity>,
        settings: DecaySettingsEntity,
        todayTimestamp: Long
    ): ReadinessResult {
        val msInDay = 86_400_000L

        var base = 0.0
        var totalCns = 0.0
        var totalBody = 0.0

        for (day in daysData) {
            val daysAgo = ((todayTimestamp - day.dateTimestamp) / msInDay).toInt()
            if (daysAgo < 0 || daysAgo > 3) continue

            // Sleep has a special Day -1 weight (0.8); everything else (incl. drains) uses
            // the "other" weight (Day -1 = 0.75).
            val sleepWeight: Double
            val otherWeight: Double
            when (daysAgo) {
                0 -> { sleepWeight = settings.weightToday;          otherWeight = settings.weightToday }
                1 -> { sleepWeight = settings.weightYesterdaySleep; otherWeight = settings.weightYesterday }
                2 -> { sleepWeight = settings.weightTwoDaysAgo;      otherWeight = settings.weightTwoDaysAgo }
                else -> { sleepWeight = settings.weightThreeDaysAgo; otherWeight = settings.weightThreeDaysAgo }
            }

            val s = sleepPoints(day.sleepCode)
            val others = hrvPoints(day.hrvCode) +
                physicalPoints(day.physicalLoadCode) +
                workPoints(day.workCode) +
                alcoholPoints(day.alcoholCode) +
                nutritionPoints(day.nutritionCode)

            base += sleepWeight * s + otherWeight * others
            totalCns += otherWeight * day.cnsDrain
            totalBody += otherWeight * day.bodyDrain
        }

        val dSum = totalCns + totalBody
        val overload = dSum > 6.0
        val readiness = if (overload) base * 0.85 else base
        val dCode = BigDecimal(dSum).setScale(1, RoundingMode.HALF_UP).toDouble()

        return ReadinessResult(
            baseScore = base,
            readinessScore = readiness,
            totalCns = totalCns,
            totalBody = totalBody,
            overloadTriggered = overload,
            dCode = dCode
        )
    }

    private fun sleepPoints(code: String): Double = when (code) {
        "S1" -> 5.0; "S2" -> 20.0; "S3" -> 25.0; "S4" -> 35.0
        else -> 0.0 // S0
    }

    private fun hrvPoints(code: String): Double = when (code) {
        "H0" -> -35.0; "H1" -> -15.0; "H3" -> 15.0
        else -> 0.0 // H2
    }

    private fun physicalPoints(code: String): Double = when (code) {
        "P1" -> -10.0; "P2" -> -30.0; "P3" -> -55.0; "P4" -> -85.0
        else -> 0.0 // P0
    }

    private fun workPoints(code: String): Double = when (code) {
        "W1" -> -5.0; "W2" -> -15.0; "W3" -> -30.0; "W4" -> -55.0
        else -> 0.0 // W0
    }

    private fun alcoholPoints(code: String): Double = when (code) {
        "A1" -> -5.0; "A2" -> -15.0; "A3" -> -35.0
        else -> 0.0 // A0
    }

    private fun nutritionPoints(code: String): Double = when (code) {
        "N0" -> 5.0; "N2" -> -8.0; "N3" -> -15.0
        else -> 0.0 // N1
    }
}
