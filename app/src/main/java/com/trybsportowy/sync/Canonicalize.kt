package com.trybsportowy.sync

import com.trybsportowy.sync.api.dto.DailyReadinessDto

/**
 * Canonical JSON for the sync payload hash (CLAUDE.md §4.4).
 *
 * Must be byte-identical to the server's canonicalization. The contract is:
 * compact JSON, keys sorted lexicographically, list order preserved, standard
 * JSON string escaping, integers without a decimal point — i.e. exactly what
 * Python's `json.dumps(obj, sort_keys=True, separators=(",", ":"))` produces.
 *
 * We emit by hand rather than via Moshi so there is zero number/precision
 * ambiguity between Kotlin and Python at the byte level. The DTO is flat and
 * fixed-schema, so this stays trivial and is locked by CanonicalPayloadTest.
 *
 * The list is sorted by `date_timestamp` ascending first, so the same set of
 * rows always hashes identically regardless of input order (stable idempotency
 * key across retries, §4.4 step 6).
 */
fun canonicalize(payload: List<DailyReadinessDto>): String =
    payload.sortedBy { it.dateTimestamp }
        .joinToString(prefix = "[", postfix = "]", separator = ",") { canonicalizeOne(it) }

private fun canonicalizeOne(d: DailyReadinessDto): String {
    // Keys MUST be in lexicographic order.
    val sb = StringBuilder()
    sb.append('{')
    sb.append("\"alcohol_code\":").append(jsonString(d.alcoholCode)).append(',')
    sb.append("\"body_drain\":").append(d.bodyDrain).append(',')
    sb.append("\"cns_drain\":").append(d.cnsDrain).append(',')
    sb.append("\"date_timestamp\":").append(d.dateTimestamp).append(',')
    sb.append("\"drain_tags\":").append(jsonString(d.drainTags)).append(',')
    sb.append("\"hrv_code\":").append(jsonString(d.hrvCode)).append(',')
    sb.append("\"nutrition_code\":").append(jsonString(d.nutritionCode)).append(',')
    sb.append("\"physical_load_code\":").append(jsonString(d.physicalLoadCode)).append(',')
    sb.append("\"sleep_code\":").append(jsonString(d.sleepCode)).append(',')
    sb.append("\"tz\":").append(jsonString(d.tz)).append(',')
    sb.append("\"work_code\":").append(jsonString(d.workCode))
    sb.append('}')
    return sb.toString()
}

/**
 * Minimal RFC 8259 string escaping matching Python json.dumps defaults:
 * short escapes for " \ \n \r \t \b \f, \uXXXX for other control chars.
 * Code-point matching avoids any non-printable char literals in source.
 * `internal` so the entity->DTO mapper reuses the exact same escaping (one
 * source of truth keeps the canonical idempotency hash consistent).
 */
internal fun jsonString(s: String): String {
    val sb = StringBuilder(s.length + 2)
    sb.append('"')
    for (c in s) {
        when (c.code) {
            0x22 -> sb.append("\\\"")   // "
            0x5C -> sb.append("\\\\")   // \
            0x0A -> sb.append("\\n")
            0x0D -> sb.append("\\r")
            0x09 -> sb.append("\\t")
            0x08 -> sb.append("\\b")
            0x0C -> sb.append("\\f")
            else -> if (c.code < 0x20) {
                sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            } else {
                sb.append(c)
            }
        }
    }
    sb.append('"')
    return sb.toString()
}
