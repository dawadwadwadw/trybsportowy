package com.trybsportowy.readiness

/**
 * The readiness algorithm version. Hard-coded per CLAUDE.md §1.2 — emitted in every sync
 * payload and stored on every cached computed-score row. Never change silently; a change
 * is a `v2` event (CLAUDE.md §1.12).
 */
const val ALGORITHM_VERSION: String = "v1"
