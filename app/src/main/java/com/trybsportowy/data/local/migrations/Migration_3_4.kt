package com.trybsportowy.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 -> v4 (CLAUDE.md Phase 2, §1.3 — hand-written, non-destructive).
 *
 * Notes specific to this codebase (deviations from the illustrative snippet
 * in docs/phases/phase-2.md, which used generic names):
 *  - The real table name is `daily_readiness` (not `DailyReadinessEntity`).
 *  - `stressCode` is RETAINED, not dropped. Dropping it would be a destructive
 *    migration (banned by §1.3) and it is explicitly kept per §1.12/CHANGELOG.
 *  - DDL mirrors Room's generated SQL (backticks, IF NOT EXISTS, column order,
 *    index names) so MigrationTestHelper.runMigrationsAndValidate against the
 *    exported v4 schema passes.
 *
 * Once this file is merged to `main` and a release is built off it, it is
 * IMMUTABLE (§1.11, §4.2). Any future bug is fixed forward with Migration_4_5.
 */
object Migration_3_4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Extend daily_readiness with the sync columns.
        db.execSQL(
            "ALTER TABLE `daily_readiness` ADD COLUMN `tz` TEXT NOT NULL DEFAULT 'Europe/Warsaw'"
        )
        db.execSQL(
            "ALTER TABLE `daily_readiness` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE `daily_readiness` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'PENDING'"
        )
        db.execSQL(
            "ALTER TABLE `daily_readiness` ADD COLUMN `syncError` TEXT"
        )

        // 2. Backfill updatedAt so pre-existing rows look "freshly edited" and
        //    are picked up by the first sync (they are all PENDING by default).
        val now = System.currentTimeMillis()
        db.execSQL("UPDATE `daily_readiness` SET `updatedAt` = $now WHERE `updatedAt` = 0")

        // 3. sync_attempt
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_attempt` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`idempotencyKey` TEXT NOT NULL, " +
                "`payloadHash` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`rowDateTimestamps` TEXT NOT NULL, " +
                "`responseSummary` TEXT)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sync_attempt_idempotencyKey` " +
                "ON `sync_attempt` (`idempotencyKey`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sync_attempt_payloadHash` " +
                "ON `sync_attempt` (`payloadHash`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sync_attempt_createdAt` " +
                "ON `sync_attempt` (`createdAt`)"
        )

        // 4. readiness_computed_cache
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `readiness_computed_cache` (" +
                "`dateTimestamp` INTEGER NOT NULL, " +
                "`algorithmVersion` TEXT NOT NULL, " +
                "`readinessScore` REAL NOT NULL, " +
                "`dCode` REAL NOT NULL, " +
                "`totalCns` REAL NOT NULL, " +
                "`totalBody` REAL NOT NULL, " +
                "`overloadTriggered` INTEGER NOT NULL, " +
                "`fetchedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`dateTimestamp`))"
        )
    }
}
