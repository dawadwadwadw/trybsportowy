package com.trybsportowy.readiness

import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.data.local.DecaySettingsEntity
import com.trybsportowy.domain.usecase.CalculateReadinessUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Algorithm parity tests (CLAUDE.md §4.1, §6, phase-1.md §4.1.3/§4.1.4).
 *
 * One @Test per fixture for clear failure messages. Every numeric field is asserted with a
 * 1e-9 tolerance; `overload_triggered` is asserted exactly. `DecaySettingsEntity()` supplies
 * the canonical decay weights (its defaults equal the spec, incl. the Day -1 sleep 0.8
 * exception). Fixtures carry no stress code — it is retired from scoring.
 */
class CalculateReadinessUseCaseTest {

    private val useCase = CalculateReadinessUseCase()
    private val fixtures = FixtureLoader.load().fixtures.associateBy { it.name }

    // Fixed reference "today" in epoch ms; the use case derives daysAgo from ms.
    private val today = 1_700_000_000_000L
    private val msInDay = 86_400_000L
    private val tolerance = 1e-9

    private fun runFixture(name: String) {
        val fixture = fixtures[name] ?: error("Missing fixture: $name")

        val rows = fixture.input.map { i ->
            DailyReadinessEntity(
                dateTimestamp = today + i.dayOffset * msInDay,
                sleepCode = i.sleepCode,
                hrvCode = i.hrvCode,
                workCode = i.workCode,
                alcoholCode = i.alcoholCode,
                physicalLoadCode = i.physicalLoadCode,
                cnsDrain = i.cnsDrain,
                bodyDrain = i.bodyDrain,
                nutritionCode = i.nutritionCode
            )
        }

        val result = useCase.calculate(rows, DecaySettingsEntity(), today)
        val e = fixture.expected

        assertEquals("$name: base_score", e.baseScore, result.baseScore, tolerance)
        assertEquals("$name: readiness_score", e.readinessScore, result.readinessScore, tolerance)
        assertEquals("$name: total_cns", e.totalCns, result.totalCns, tolerance)
        assertEquals("$name: total_body", e.totalBody, result.totalBody, tolerance)
        assertEquals("$name: d_code", e.dCode, result.dCode, tolerance)
        assertEquals("$name: overload_triggered", e.overloadTriggered, result.overloadTriggered)
    }

    @Test fun default_today_only() = runFixture("default_today_only")

    @Test fun day_minus_one_sleep_weight_080_exception() =
        runFixture("day_minus_one_sleep_weight_080_exception")

    @Test fun full_window_overload_triggered() = runFixture("full_window_overload_triggered")

    @Test fun full_window_just_under_overload() = runFixture("full_window_just_under_overload")

    @Test fun full_window_just_over_overload() = runFixture("full_window_just_over_overload")

    @Test fun missing_intermediate_days() = runFixture("missing_intermediate_days")
}
