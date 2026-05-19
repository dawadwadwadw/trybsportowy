package com.trybsportowy.sync

import com.trybsportowy.data.local.DailyReadinessEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the §2.2 wire contract for drain_tags: local CSV storage must be
 * emitted as a JSON-encoded array STRING. Regression guard for the live sync
 * failure ("Synchronizacja nieudana — ponowię gdy będzie sieć").
 */
class MappersTest {

    @Test fun csv_to_json_array_string_cases() {
        assertEquals("[]", csvToJsonArrayString(""))
        assertEquals("[]", csvToJsonArrayString("   "))
        assertEquals("""["DOMS"]""", csvToJsonArrayString("DOMS"))
        assertEquals("""["a","b"]""", csvToJsonArrayString("a,b"))
        // trims members and drops empties
        assertEquals("""["a","b","c"]""", csvToJsonArrayString("a, b , ,c"))
        // already-JSON (legacy/imported) passes through trimmed, not mangled
        assertEquals("""["x","y"]""", csvToJsonArrayString("""  ["x","y"]  """))
        // escaping matches canonicalize()'s jsonString
        assertEquals("""["he\"llo"]""", csvToJsonArrayString("""he"llo"""))
    }

    @Test fun toDto_emits_drain_tags_as_json_array_string() {
        val entity = DailyReadinessEntity(
            dateTimestamp = 1_747_094_400_000L,
            drainTags = "DOMS,Deadline"
        )
        val dto = entity.toDto()
        assertEquals("""["DOMS","Deadline"]""", dto.drainTags)
        // ms -> s boundary still holds
        assertEquals(1_747_094_400L, dto.dateTimestamp)
    }

    @Test fun toDto_empty_drain_tags_is_empty_json_array() {
        val dto = DailyReadinessEntity(dateTimestamp = 1_000L).toDto()
        assertEquals("[]", dto.drainTags)
    }
}
