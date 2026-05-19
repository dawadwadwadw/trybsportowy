package com.trybsportowy.sync

import com.trybsportowy.sync.api.dto.DailyReadinessDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CLAUDE.md §4.4 / §9.8 — canonicalize() must be deterministic, keys sorted
 * lexicographically, list ordered by date_timestamp, byte-identical to the
 * server's json.dumps(sort_keys=True, separators=(",",":")).
 */
class CanonicalPayloadTest {

    private fun dto(ts: Long, drainTags: String = "[\"DOMS\"]") = DailyReadinessDto(
        dateTimestamp = ts,
        tz = "Europe/Warsaw",
        sleepCode = "S2",
        hrvCode = "H2",
        physicalLoadCode = "P2",
        workCode = "W0",
        alcoholCode = "A0",
        nutritionCode = "N1",
        cnsDrain = 3,
        bodyDrain = 1,
        drainTags = drainTags
    )

    @Test fun single_record_has_sorted_keys_and_compact_form() {
        val expected = "[{" +
            "\"alcohol_code\":\"A0\"," +
            "\"body_drain\":1," +
            "\"cns_drain\":3," +
            "\"date_timestamp\":1747094400," +
            "\"drain_tags\":\"[\\\"DOMS\\\"]\"," +
            "\"hrv_code\":\"H2\"," +
            "\"nutrition_code\":\"N1\"," +
            "\"physical_load_code\":\"P2\"," +
            "\"sleep_code\":\"S2\"," +
            "\"tz\":\"Europe/Warsaw\"," +
            "\"work_code\":\"W0\"" +
            "}]"
        assertEquals(expected, canonicalize(listOf(dto(1747094400))))
    }

    @Test fun input_order_does_not_change_output() {
        val a = listOf(dto(100), dto(200), dto(300))
        val b = listOf(dto(300), dto(100), dto(200))
        assertEquals(canonicalize(a), canonicalize(b))
    }

    @Test fun list_is_ordered_by_date_timestamp_ascending() {
        val out = canonicalize(listOf(dto(300), dto(100)))
        val first = out.indexOf("\"date_timestamp\":100")
        val second = out.indexOf("\"date_timestamp\":300")
        assertEquals(true, first in 0 until second)
    }

    @Test fun deterministic_across_calls() {
        val rows = listOf(dto(1), dto(2))
        assertEquals(canonicalize(rows), canonicalize(rows))
    }
}
