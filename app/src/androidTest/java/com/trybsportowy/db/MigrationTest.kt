package com.trybsportowy.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trybsportowy.data.local.AppDatabase
import com.trybsportowy.data.local.migrations.Migration_3_4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies Migration_3_4 (CLAUDE.md §7.5) preserves existing rows and adds the
 * new columns/tables, validated structurally against the exported v4 schema.
 *
 * The project did not export a v3 schema baseline (exportSchema was false until
 * Phase 2), so MigrationTestHelper.createDatabase(name, 3) is unavailable. We
 * instead hand-seed the exact v3 SQLite schema + `PRAGMA user_version = 3`,
 * then let runMigrationsAndValidate apply Migration_3_4 and validate the
 * resulting schema against schemas/.../4.json (bundled as an androidTest asset).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate_3_to_4_keeps_existing_rows_and_adds_columns_and_tables() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.deleteDatabase(TEST_DB)

        // --- Seed the exact v3 schema ---
        ctx.openOrCreateDatabase(TEST_DB, 0, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_readiness` (" +
                    "`dateTimestamp` INTEGER NOT NULL, " +
                    "`sleepCode` TEXT NOT NULL, " +
                    "`hrvCode` TEXT NOT NULL, " +
                    "`stressCode` TEXT NOT NULL, " +
                    "`workCode` TEXT NOT NULL, " +
                    "`alcoholCode` TEXT NOT NULL, " +
                    "`physicalLoadCode` TEXT NOT NULL, " +
                    "`cnsDrain` INTEGER NOT NULL, " +
                    "`bodyDrain` INTEGER NOT NULL, " +
                    "`drainTags` TEXT NOT NULL, " +
                    "`nutritionCode` TEXT NOT NULL, " +
                    "PRIMARY KEY(`dateTimestamp`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `decay_settings` (" +
                    "`id` INTEGER NOT NULL, " +
                    "`weightToday` REAL NOT NULL, " +
                    "`weightYesterday` REAL NOT NULL, " +
                    "`weightYesterdaySleep` REAL NOT NULL, " +
                    "`weightTwoDaysAgo` REAL NOT NULL, " +
                    "`weightThreeDaysAgo` REAL NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`dateTimestamp` INTEGER NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`role` TEXT NOT NULL, " +
                    "`content` TEXT NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO daily_readiness (dateTimestamp, sleepCode, hrvCode, " +
                    "stressCode, workCode, alcoholCode, physicalLoadCode, cnsDrain, " +
                    "bodyDrain, drainTags, nutritionCode) VALUES " +
                    "(1747094400, 'S2', 'H2', 'L', 'W0', 'A0', 'P0', 0, 0, '[]', 'N1')"
            )
            db.version = 3
        }

        // --- Apply Migration_3_4 and validate against the exported v4 schema ---
        helper.runMigrationsAndValidate(TEST_DB, 4, true, Migration_3_4).use { db ->
            db.query(
                "SELECT tz, syncState, syncError, updatedAt FROM daily_readiness " +
                    "WHERE dateTimestamp = 1747094400"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Europe/Warsaw", c.getString(0))
                assertEquals("PENDING", c.getString(1))
                assertTrue(c.isNull(2))                 // syncError nullable, untouched
                assertTrue(c.getLong(3) > 0L)           // updatedAt backfilled
            }
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='sync_attempt'"
            ).use { c -> assertTrue(c.moveToFirst()) }
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' " +
                    "AND name='readiness_computed_cache'"
            ).use { c -> assertTrue(c.moveToFirst()) }
        }
    }

    companion object {
        private const val TEST_DB = "migration-test.db"
    }
}
