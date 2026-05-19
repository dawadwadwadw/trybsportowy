package com.trybsportowy.sync

import java.security.MessageDigest

/** Lowercase hex SHA-256 of the canonical payload (CLAUDE.md §4.4). */
fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
