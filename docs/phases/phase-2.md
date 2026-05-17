# Phase 2 — Schema Evolution (procedure)

> Active-phase procedure. Canonical rules live in `/CLAUDE.md`. Do not start this phase until Phase 1 has passed its checkpoint and the user replied `next`.

---

## §7. Phase 2 — Schema Evolution

Extend the Room schema for sync. Three changes: extend `DailyReadinessEntity`, add `SyncAttemptEntity`, add `ComputedScoreCacheEntity`. Ship migrations and a `MigrationTest`.

### §7.1 Backup

Before editing any DB file:
```bash
mkdir -p docs/history
cp app/src/main/java/<pkg>/db/AppDatabase.kt           docs/history/AppDatabase.$(date +%Y%m%d_%H%M%S).bak
cp app/src/main/java/<pkg>/db/DailyReadinessEntity.kt  docs/history/DailyReadinessEntity.$(date +%Y%m%d_%H%M%S).bak
```

Inspect the current schema:
```bash
ls app/schemas/<pkg>.AppDatabase/  # if exportSchema was enabled
```
If `app/schemas/` doesn't exist, the project hasn't been exporting schemas. That is itself a deviation from §4.2 — fix it: set `exportSchema = true` in the `@Database` annotation and `room.schemaLocation` in `build.gradle.kts`'s ksp/kapt args. Then build once to generate the **current** schema as the baseline.

Let `N` be the current Room version (likely `1`). The new version is `N+1`.

### §7.2 Extend `DailyReadinessEntity`

Add columns:

```kotlin
@Entity
data class DailyReadinessEntity(
    @PrimaryKey val dateTimestamp: Long,
    val sleepCode: String = "S2",
    val hrvCode: String = "H2",
    val physicalLoadCode: String = "P0",
    val workCode: String = "W0",
    val alcoholCode: String = "A0",
    val nutritionCode: String = "N1",
    val cnsDrain: Int = 0,
    val bodyDrain: Int = 0,
    val drainTags: String = "[]",
    // NEW Phase 2:
    val tz: String = "Europe/Warsaw",                 // IANA, set at entry time
    val updatedAt: Long = 0L,                          // UTC epoch ms; bumped on every edit
    val syncState: String = "PENDING",                 // SyncState.name
    val syncError: String? = null                      // last sync error message, if any
)
```

In the repository, `updatedAt` is set to `System.currentTimeMillis()` on every insert/update. `syncState` defaults to `PENDING` on edit; only the repository transitions it.

### §7.3 Add `SyncAttemptEntity` and `ComputedScoreCacheEntity`

```kotlin
@Entity(tableName = "sync_attempt")
data class SyncAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val idempotencyKey: String,    // UUIDv4
    val payloadHash: String,        // SHA-256 hex lowercase of canonical payload
    val createdAt: Long,            // UTC epoch ms
    val status: String,             // 'pending' | 'success' | 'auth_failed' | 'failed'
    val rowDateTimestamps: String,  // CSV of included dateTimestamps
    val responseSummary: String? = null  // truncated to 1KB
)

@Entity(tableName = "readiness_computed_cache")
data class ComputedScoreCacheEntity(
    @PrimaryKey val dateTimestamp: Long,
    val algorithmVersion: String,          // "v1"
    val readinessScore: Double,
    val dCode: Double,
    val totalCns: Double,
    val totalBody: Double,
    val overloadTriggered: Boolean,
    val fetchedAt: Long                    // UTC epoch ms when pulled from server
)
```

Add corresponding `Dao` interfaces with the queries the later phases need:
- `SyncAttemptDao`: `insert`, `findOpenByPayloadHash`, `markStatus` (with an optional `responseSummary` parameter), `deleteOlderThan`
- `ComputedScoreCacheDao`: `upsert`, `replaceAll`, `getByDateRange`, `getMostRecent`, `clear`

### §7.4 Migration

`Migration_<N>_<N+1>.kt`:

```kotlin
object Migration_<N>_<N+1> : Migration(<N>, <N+1>) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Extend DailyReadinessEntity
        db.execSQL("ALTER TABLE DailyReadinessEntity ADD COLUMN tz TEXT NOT NULL DEFAULT 'Europe/Warsaw'")
        db.execSQL("ALTER TABLE DailyReadinessEntity ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE DailyReadinessEntity ADD COLUMN syncState TEXT NOT NULL DEFAULT 'PENDING'")
        db.execSQL("ALTER TABLE DailyReadinessEntity ADD COLUMN syncError TEXT")

        // 2. Backfill updatedAt so existing rows look "freshly edited" and will sync on next run
        val now = System.currentTimeMillis()
        db.execSQL("UPDATE DailyReadinessEntity SET updatedAt = $now WHERE updatedAt = 0")

        // 3. Create sync_attempt
        db.execSQL("""
            CREATE TABLE sync_attempt (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                idempotencyKey TEXT NOT NULL,
                payloadHash TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                status TEXT NOT NULL,
                rowDateTimestamps TEXT NOT NULL,
                responseSummary TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_sync_attempt_key ON sync_attempt(idempotencyKey)")
        db.execSQL("CREATE INDEX idx_sync_attempt_created ON sync_attempt(createdAt)")

        // 4. Create readiness_computed_cache
        db.execSQL("""
            CREATE TABLE readiness_computed_cache (
                dateTimestamp INTEGER NOT NULL PRIMARY KEY,
                algorithmVersion TEXT NOT NULL,
                readinessScore REAL NOT NULL,
                dCode REAL NOT NULL,
                totalCns REAL NOT NULL,
                totalBody REAL NOT NULL,
                overloadTriggered INTEGER NOT NULL,
                fetchedAt INTEGER NOT NULL
            )
        """.trimIndent())
    }
}
```

Register it on the `Room.databaseBuilder(...).addMigrations(Migration_<N>_<N+1>)`. Bump the `@Database(version = <N+1>, ...)` annotation.

### §7.5 Migration test

`app/src/androidTest/java/<pkg>/db/MigrationTest.kt`:

```kotlin
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test fun migrate_<N>_to_<N+1>_keeps_existing_rows_and_adds_columns() {
        val dbName = "test-migration.db"
        helper.createDatabase(dbName, <N>).use { db ->
            db.execSQL("""
                INSERT INTO DailyReadinessEntity (dateTimestamp, sleepCode, hrvCode, physicalLoadCode, workCode, alcoholCode, nutritionCode, cnsDrain, bodyDrain, drainTags)
                VALUES (1747094400, 'S2', 'H2', 'P0', 'W0', 'A0', 'N1', 0, 0, '[]')
            """.trimIndent())
        }
        helper.runMigrationsAndValidate(dbName, <N+1>, true, Migration_<N>_<N+1>).use { db ->
            db.query("SELECT tz, syncState, updatedAt FROM DailyReadinessEntity").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Europe/Warsaw", c.getString(0))
                assertEquals("PENDING", c.getString(1))
                assertTrue(c.getLong(2) > 0)
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='sync_attempt'").use { c ->
                assertTrue(c.moveToFirst())
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='readiness_computed_cache'").use { c ->
                assertTrue(c.moveToFirst())
            }
        }
    }
}
```

### §7.6 Checkpoint

```bash
# 1. App compiles
./gradlew assembleDebug
# expect: BUILD SUCCESSFUL

# 2. Schemas exported
ls app/schemas/<pkg>.AppDatabase/
# expect: <N>.json AND <N+1>.json

# 3. Algorithm tests still green (no regressions)
./gradlew test --tests "*CalculateReadinessUseCaseTest*"
# expect: 6 tests passed

# 4. Migration instrumented test passes (requires an emulator or device)
./gradlew connectedAndroidTest --tests "*MigrationTest*"
# expect: 1 test passed

# 5. fallbackToDestructive is absent
grep -RE "fallbackToDestructive" app/src/main/ || echo CLEAN
# expect: CLEAN

# 6. New entities + DAOs exist
ls app/src/main/java/<pkg>/db/
# expect: includes SyncAttemptEntity.kt, ComputedScoreCacheEntity.kt, etc.
```

Append to `CHANGELOG.md`. Wait for `next`.

---
