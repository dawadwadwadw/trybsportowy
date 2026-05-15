# CLAUDE.md — Trybsportowy (Android) Operating Manual

**For:** Claude Code agent running against the `trybsportowy` repo (`git clone https://github.com/dawadwadwadw/trybsportowy`).
**Audience:** A fresh Claude Code instance with no prior context. Read top to bottom before executing anything.
**Language rule:** This document is English. All Kotlin code, identifiers, comments, log messages, file names, git commit messages are English. **Every user-visible string is Polish, and lives in `res/values/strings.xml`.** No inline Polish literals inside Composables, ViewModels, or anywhere else.

---

## §0. Current State Pointer

The app already exists (Jetpack Compose + Kotlin + MVVM + Room + Coroutines + AlarmManager).
Its original behavior is captured by the **Master Blueprint** at `docs/MASTER_BLUEPRINT.md` —
read-only, canonical for what the app *was*; never edit it (see §1.12).

This file is the **always-loaded contract**: invariants (§1), the server contract (§2), the
repository map (§3), cross-cutting engineering rules including the canonical readiness
algorithm (§4), operational discipline (§5), and scope boundaries (§13).

**Per-phase procedure is deliberately NOT in this file.** Each phase's step-by-step lives in
`docs/phases/phase-N.md`. A working session loads only the invariants here plus the single
active phase — not all seven verbose phase bodies. As a phase completes, its lasting outcome
is distilled into §1/§4 and a `CHANGELOG.md` line; its procedure stays archived under
`docs/phases/`. (§4.1.3 required-fixtures and §4.1.4 test-invocation were relocated into
`docs/phases/phase-1.md` for the same reason — content unchanged.)

The server side is already built and verified (server Phase 9). The API contract is §2.

### Phase Index — active work queue

Run in order. Stop at every phase checkpoint. Do not start Phase N+1 until the user replies
`next` (or equivalent) after Phase N's checkpoint (§1.13).

| Phase | Title | Procedure |
|---|---|---|
| 1 | Algorithm Parity | `docs/phases/phase-1.md` |
| 2 | Schema Evolution | `docs/phases/phase-2.md` |
| 3 | Settings + Secret Provisioning | `docs/phases/phase-3.md` |
| 4 | Networking Layer | `docs/phases/phase-4.md` |
| 5 | Sync Engine + WorkManager | `docs/phases/phase-5.md` |
| 6 | UI Integration (Minimal) | `docs/phases/phase-6.md` |
| 7 | End-to-end Verification | `docs/phases/phase-7.md` |

**Current phase: 1 — Algorithm Parity.** Begin at `docs/phases/phase-1.md`.

---
## §1. Hard Invariants (Never Violate)

These rules govern every change. They are the conclusions from prior architectural discussion; never reopen them without an explicit owner conversation.

1. **The phone's readiness algorithm and the server's `readiness_calc.py` must produce bit-identical results** for every input in `algorithm_v1_fixtures.json`. If a fixture disagrees, the implementation that disagrees with the spec in §4.1 is wrong. The server is canonical for the spec; the phone must conform.
2. **`ALGORITHM_VERSION = "v1"`** is hard-coded in `app/src/main/java/.../readiness/AlgorithmVersion.kt` as a Kotlin constant, emitted in every sync payload, stored on every cached computed-score row. Never silently change it.
3. **No destructive Room migrations.** `fallbackToDestructiveMigration()` is banned in the codebase. Every Room version bump ships with a hand-written `Migration` class committed in the same change. CI fails if `fallbackToDestructive*` appears anywhere under `app/src/main/`.
4. **Secrets never reach logs, never reach git.** The Bearer token for the server lives only in `EncryptedSharedPreferences`. OkHttp's `HttpLoggingInterceptor` filters the `Authorization` header at every level. No `BuildConfig` field, no `local.properties` entry, no `gradle.properties` entry contains the token. `.gitignore` blocks any file under `app/src/main/assets/secrets/` and any file matching `*.secret`.
5. **All writes to readiness data go through `ReadinessRepository`.** No Composable, no ViewModel, no WorkManager `Worker` may touch the DAO directly for writes. Reads via Flow are fine.
6. **Every outbound sync POST carries an `Idempotency-Key` header**, a UUIDv4 generated once per sync attempt, persisted in the `sync_attempt` table, reused on every retry of that same attempt. The server requires this (server §4.2). Without it, network flakiness corrupts data.
7. **Polish for users, English for code.** All user-visible strings live in `res/values/strings.xml` (and optionally `res/values-en/strings.xml` later if you ever ship English). Identifiers, comments, exceptions, log lines, commit messages, branch names are English. Mixing the two in source — `val przyciskZapisz = "Zapisz"` — is forbidden.
8. **UTC epoch in storage, IANA tz alongside, local at the edge.** `dateTimestamp` is UTC epoch seconds of the user's local-midnight on the day the entry represents. A separate `tz: String` column carries the IANA name (`"Europe/Warsaw"`). Local conversion happens at the formatting layer (Compose) only. Mixing UTC and local epochs anywhere in storage or transport is a bug.
9. **Sync is best-effort, never destructive.** If the server returns an error, the local `sync_state` reverts to `pending`, never to a state that loses the user's data. A 401 marks the secret as invalid in `EncryptedSharedPreferences` (a new `secret_invalid` flag) and surfaces in the status line; the data is untouched.
10. **No bypassing the transport for the Bearer token.** Every HTTP call to the server goes through the singleton `ServerApi` (Retrofit interface) injected with the `AuthInterceptor`. Hand-rolled `URL.openConnection()` calls are banned.
11. **Backup before edit on long-lived schema files.** Before modifying `app/src/main/java/.../db/AppDatabase.kt` (the `@Database` annotation), `DailyReadinessEntity.kt`, or any committed `Migration_N_M.kt` file, copy it to `docs/history/<filename>.<timestamp>.bak`. Migration files, once committed and pushed, are **never edited** — only added to. Editing a released migration corrupts every device that already ran it.
12. **`MASTER_BLUEPRINT.md` is read-only documentation.** It captures the original intent of the app. This CLAUDE.md may extend the app, but the algorithm spec lifted from the blueprint into §4.1 is canonical for tests. If the user later updates the algorithm, that is a `v2` event: bump `ALGORITHM_VERSION`, write new fixtures, write `Migration_*` files, coordinate with server-side `readiness_calc.py`.
13. **Checkpoint discipline.** After every phase, stop. Run the checkpoint commands listed in that phase's section. Post the results to the user (over Discord if the user has configured a transport, otherwise paste into chat). Wait for `next` before starting the following phase.
14. **No destructive shell operations without explicit confirmation.** Destructive = `rm -rf` outside `build/` or `.gradle/`, `git push --force`, `git reset --hard` on `main`, dropping any Room table, deleting a migration file, overwriting `MASTER_BLUEPRINT.md`. Ask in chat with the exact command in a code block, wait for the literal reply `TAK`. Any other reply, including `tak`, `ok`, `yes`, means stop.

---

## §2. The Server Contract

The companion server lives at `~/ai-server/` on the user's home server (WSL2 Ubuntu under Windows). It exposes the Android sync API via Caddy at:

- LAN: `https://192.168.55.106/api/android/*` (self-signed cert; phone must trust the LAN CA or be on Tailscale)
- Tailscale: `https://aiserver.tail198ba5.ts.net/api/android/*` (real Let's Encrypt cert via Tailscale; preferred path)

The phone is expected to be on the tailnet (the user confirmed Tailscale is installed on the phone). The default server URL stored in settings should be the Tailscale hostname.

### §2.1 Authentication

Every request requires `Authorization: Bearer <ANDROID_API_SECRET>`. The secret is shared with the server (lives in the server's `.env`). 401 means either the secret is missing, wrong, or has been rotated.

### §2.2 Endpoints

**`POST /api/android/sync`**

Headers:
```
Authorization: Bearer <secret>
Idempotency-Key: <uuidv4>
Content-Type: application/json
```

Body — one or many `DailyReadiness` records:
```json
[
  {
    "date_timestamp": 1747094400,
    "tz": "Europe/Warsaw",
    "sleep_code": "S2",
    "hrv_code": "H2",
    "physical_load_code": "P2",
    "work_code": "W0",
    "alcohol_code": "A0",
    "nutrition_code": "N1",
    "cns_drain": 3,
    "body_drain": 1,
    "drain_tags": "[\"DOMS\"]"
  }
]
```

Note `drain_tags` is a JSON-encoded string (server's choice — keep it that way). The contents are a flat array of tag IDs (strings).

Responses:
- `200 OK` with body `{"synced": <n>, "errors": []}` — all good.
- `207 Multi-Status` with body `{"synced": <n>, "errors": [{...}]}` — some records failed validation.
- `401 Unauthorized` — bad/missing secret.
- `5xx` — retry later.

**Idempotency:** the server stores `(idempotency_key, hash_of_payload, response, created_at)` for 24h. Retrying within 24h with the same key returns the cached response — even if the original POST succeeded. So a flaky network never causes a double-write.

**`GET /api/android/readiness?days=28`**

Headers: `Authorization: Bearer <secret>`.

Response — newest first:
```json
[
  {
    "date_timestamp": 1747094400,
    "local_date": "2026-05-13",
    "tz": "Europe/Warsaw",
    "raw": {
      "sleep_code": "S2",
      "hrv_code": "H2",
      "physical_load_code": "P2",
      "work_code": "W0",
      "alcohol_code": "A0",
      "nutrition_code": "N1",
      "cns_drain": 3,
      "body_drain": 1,
      "drain_tags": ["DOMS"]
    },
    "computed": {
      "algorithm_version": "v1",
      "readiness_score": 42.5,
      "d_code": 5.2,
      "total_cns": 3.1,
      "total_body": 2.1,
      "overload_triggered": false
    }
  }
]
```

Days with no record on the server are omitted (the phone is the source of truth for whether a day exists). The phone uses this to populate `readiness_computed_cache`.

**`GET /api/android/readiness/summary`**

Returns averages, trends, and streaks. The phone consumes this only if a future feature needs it; not required for Phase 7. Document the schema in code so a later phase can wire it up.

**`GET /api/android/health`**

Public, no auth. Returns `{"status": "ok", "uptime_s": <int>, "version": "<sha>"}`. Used by the Settings screen's "Test connection" button.

### §2.3 What the server does with each sync

Every successful POST triggers, server-side: an upsert into `daily_readiness`, then a recompute of `readiness_computed` for that day **and the three following days** (because each day's score depends on its 3 predecessors). The phone does not need to know how; it just needs to be aware that after a sync, the server's view of days +0, +1, +2, +3 from any synced day may have changed. This is why the phone always does a `GET /readiness?days=N` immediately after a successful POST.

---

## §3. Repository Layout (Post-Phase 7)

Existing layout is preserved; new additions are marked NEW. The exact package name is whatever the existing app uses — the new files go under appropriate subpackages of that root. `<pkg>` below is shorthand for the app's root package (e.g. `pl.dawid.trybsportowy`).

```
trybsportowy/
├── CLAUDE.md                                # This file
├── docs/
│   ├── MASTER_BLUEPRINT.md                  # Original spec; read-only after Phase 1
│   └── history/                             # NEW — pre-edit backups per §1.11
│       └── <filename>.<ts>.bak
├── app/
│   ├── build.gradle.kts                     # Edited in Phase 3 (dependencies)
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml          # Edited in Phase 5 (INTERNET, WorkManager)
│   │   │   ├── java/<pkg>/
│   │   │   │   ├── App.kt                   # Application class — WorkManager init
│   │   │   │   ├── db/
│   │   │   │   │   ├── AppDatabase.kt       # @Database; version bumped Phase 2
│   │   │   │   │   ├── DailyReadinessEntity.kt  # Extended Phase 2
│   │   │   │   │   ├── ReadinessDao.kt
│   │   │   │   │   ├── SyncAttemptEntity.kt        # NEW Phase 2
│   │   │   │   │   ├── SyncAttemptDao.kt           # NEW Phase 2
│   │   │   │   │   ├── ComputedScoreCacheEntity.kt # NEW Phase 2
│   │   │   │   │   ├── ComputedScoreCacheDao.kt    # NEW Phase 2
│   │   │   │   │   └── migrations/
│   │   │   │   │       └── Migration_<N>_<N+1>.kt  # NEW Phase 2
│   │   │   │   ├── readiness/
│   │   │   │   │   ├── AlgorithmVersion.kt         # NEW Phase 1 — const "v1"
│   │   │   │   │   └── CalculateReadinessUseCase.kt # Verified/aligned Phase 1
│   │   │   │   ├── settings/
│   │   │   │   │   ├── SecretsStore.kt             # NEW Phase 3 — EncryptedSharedPrefs
│   │   │   │   │   ├── SettingsScreen.kt           # NEW Phase 3 (minimal)
│   │   │   │   │   └── SettingsViewModel.kt        # NEW Phase 3
│   │   │   │   ├── sync/
│   │   │   │   │   ├── api/                        # NEW Phase 4
│   │   │   │   │   │   ├── ServerApi.kt            # Retrofit interface
│   │   │   │   │   │   ├── AuthInterceptor.kt
│   │   │   │   │   │   ├── HeaderRedactor.kt       # logging filter
│   │   │   │   │   │   ├── NetworkModule.kt        # OkHttp + Retrofit factory
│   │   │   │   │   │   └── dto/
│   │   │   │   │   │       ├── DailyReadinessDto.kt
│   │   │   │   │   │       ├── ReadinessRecordDto.kt
│   │   │   │   │   │       ├── ComputedDto.kt
│   │   │   │   │   │       ├── SyncResponseDto.kt
│   │   │   │   │   │       └── HealthDto.kt
│   │   │   │   │   ├── SyncRepository.kt           # NEW Phase 5 — single write path
│   │   │   │   │   ├── ReadinessRepository.kt      # NEW Phase 5 — wraps DAO writes
│   │   │   │   │   ├── SyncWorker.kt               # NEW Phase 5 — WorkManager
│   │   │   │   │   ├── SyncScheduler.kt            # NEW Phase 5 — schedule periodic + one-time
│   │   │   │   │   └── SyncStatus.kt               # NEW Phase 5 — enum + status flow
│   │   │   │   └── ui/
│   │   │   │       └── pro/
│   │   │   │           ├── ProDashboardScreen.kt   # Edited Phase 6 (status line, sync button)
│   │   │   │           └── SyncStatusLine.kt       # NEW Phase 6
│   │   │   └── res/
│   │   │       └── values/
│   │   │           └── strings.xml          # Extended every phase (Polish strings)
│   │   ├── test/
│   │   │   ├── java/<pkg>/readiness/
│   │   │   │   ├── CalculateReadinessUseCaseTest.kt # NEW Phase 1
│   │   │   │   └── FixtureLoader.kt                 # NEW Phase 1
│   │   │   └── resources/
│   │   │       └── algorithm_v1_fixtures.json       # NEW Phase 1 — shared with server
│   │   └── androidTest/                              # Instrumented tests (Phase 2, 5)
│   │       └── java/<pkg>/
│   │           ├── db/MigrationTest.kt              # NEW Phase 2
│   │           └── sync/SyncRepositoryTest.kt       # NEW Phase 5
└── scripts/
    └── verify-no-secrets.sh                # NEW Phase 3 — git-diff guard
```

---

## §4. Cross-Cutting Engineering Rules

Each rule below is binding on every change. They exist because each is cheap now and very expensive to retrofit.

### §4.1 Algorithm parity (the most important rule)

The on-phone algorithm and the server's `agents/android_api/readiness_calc.py` must agree exactly. The shared enforcement mechanism is a JSON file of fixtures consumed by both sides' test suites.

#### §4.1.1 The algorithm (reproduced from server's CLAUDE.md §4.3)

**Base point dictionaries:**

```
S (Sleep):    S0=0,  S1=+5,  S2=+20, S3=+25, S4=+35
H (HRV):      H0=-35, H1=-15, H2=0,   H3=+15
P (Physical): P0=0,  P1=-10, P2=-30, P3=-55, P4=-85
W (Work):     W0=0,  W1=-5,  W2=-15, W3=-30, W4=-55
A (Alcohol):  A0=0,  A1=-5,  A2=-15, A3=-35
N (Nutrition):N0=+5, N1=0,   N2=-8,  N3=-15
```

**Decay weights for the 4-day window:**

| Day | S weight | All other base codes (H,P,W,A,N) | cnsDrain | bodyDrain |
|---|---|---|---|---|
| Day 0 (today) | 1.0 | 1.0 | 1.0 | 1.0 |
| Day -1 (yesterday) | **0.8** (special case) | **0.75** | 0.75 | 0.75 |
| Day -2 | 0.5 | 0.5 | 0.5 | 0.5 |
| Day -3 | 0.2 | 0.2 | 0.2 | 0.2 |

**The 0.8 sleep weight on Day -1 is a deliberate exception. It is not a typo. Tests must assert it explicitly.**

Missing days in the DB are all-zero entries (no contribution). A "missing day" is one without a row in `daily_readiness` for that local date.

**Base score:**
```
base = Σ over d ∈ {0, -1, -2, -3}:
        weight_S(d)     * S_points(d)
      + weight_other(d) * ( H_points(d) + P_points(d) + W_points(d) + A_points(d) + N_points(d) )
```

**System Overload Penalty (CNS):**
```
total_cns  = Σ over d: weight(d) * cnsDrain(d)
total_body = Σ over d: weight(d) * bodyDrain(d)

if (total_cns + total_body) > 6.0:
    final_score = base * 0.85
else:
    final_score = base
```

**D-Code** = `total_cns + total_body`, rounded to one decimal using `HALF_UP` rounding. (Pin the rounding mode explicitly in both implementations. `BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toDouble()` on Kotlin; `Decimal(value).quantize(Decimal("0.1"), ROUND_HALF_UP)` on Python. Without pinning the mode, the two languages can disagree at .5 boundaries.)

**Floating point note:** Use `Double` for intermediate sums. Compare floats with a tolerance of `1e-9` in tests, not `==`. Round only the published `d_code` (to 1 decimal) and `readiness_score` (kept as raw `Double` for now).

#### §4.1.2 The shared fixture file

Path on Android: `app/src/test/resources/algorithm_v1_fixtures.json`.
Path on server (out of scope for this CLAUDE.md but documented for completeness): `~/ai-server/agents/android_api/tests/algorithm_v1_fixtures.json`. Both files must be byte-identical. If you change one, copy it to the other in the same commit on each side.

Schema:
```json
{
  "algorithm_version": "v1",
  "rounding_mode": "HALF_UP",
  "fixtures": [
    {
      "name": "<short slug>",
      "description": "<one sentence>",
      "input": [
        {
          "day_offset": 0,
          "sleep_code": "S2",
          "hrv_code": "H2",
          "physical_load_code": "P0",
          "work_code": "W0",
          "alcohol_code": "A0",
          "nutrition_code": "N1",
          "cns_drain": 0,
          "body_drain": 0
        }
      ],
      "expected": {
        "base_score": 20.0,
        "readiness_score": 20.0,
        "total_cns": 0.0,
        "total_body": 0.0,
        "overload_triggered": false,
        "d_code": 0.0
      }
    }
  ]
}
```

`input[].day_offset` is 0, -1, -2, or -3. Missing offsets are treated as missing days (no contribution). `expected.base_score` and `expected.readiness_score` are exact floats with the test asserting `abs(actual - expected) < 1e-9`.


> **§4.1.3 (required minimum fixtures) and §4.1.4 (test invocation) were relocated to `docs/phases/phase-1.md`** for token economy. Content unchanged; still canonical for Phase 1 tests.

### §4.2 Schema migrations from day one

Room is wired with explicit migrations only.

```kotlin
@Database(
    entities = [
        DailyReadinessEntity::class,
        SyncAttemptEntity::class,
        ComputedScoreCacheEntity::class
    ],
    version = <N>,  // bump every schema change
    exportSchema = true  // commits schemas to app/schemas/
)
abstract class AppDatabase : RoomDatabase() { ... }
```

`exportSchema = true` and `app/schemas/` is committed to git. Each schema version is one JSON file. Any change that doesn't produce a new schema file means no schema change happened — and is therefore not a version bump.

Each `Migration_<N>_<N+1>` is committed to `app/src/main/java/<pkg>/db/migrations/` and registered in `AppDatabase`'s builder. Once a migration has been merged to `main` and a release built off it, **the migration file is immutable**. If it had a bug, fix it forward with `Migration_<N+1>_<N+2>`.

Instrumented migration test (Phase 2): `MigrationTest.kt` uses `MigrationTestHelper` from `androidx.room.testing` to assert each `Migration_X_Y` correctly transforms a `version=X` database into a `version=Y` shape. One `@Test` per migration. Required to land any schema change.

`fallbackToDestructiveMigration()` is banned. CI grep:
```
grep -RE "fallbackToDestructive" app/src/main/ && exit 1 || exit 0
```
Wire this into Phase 3's `scripts/verify-no-secrets.sh` as a second check, or into a pre-commit hook.

### §4.3 Sync state model

Every `DailyReadinessEntity` row carries a sync state:

```kotlin
enum class SyncState { PENDING, SYNCING, SYNCED, FAILED }
```

Stored as a `String` column (Room handles enums via `@TypeConverter`; or store as `String` and convert in the repository). Lifecycle:

```
[user edits row]                  → PENDING (updatedAt = now)
[SyncWorker picks it up]          → SYNCING
[server returns 200/207 success]  → SYNCED
[server returns 5xx / network]    → PENDING (worker retries on schedule)
[server returns 401]              → FAILED + secret_invalid flag set in SecretsStore
[server returns 207 with this row in errors[]] → FAILED with the error message stored
```

`PENDING` rows are eligible for the next sync attempt. `SYNCED` rows are not, unless edited (which flips them back to `PENDING` and bumps `updatedAt`).

The repository is the only place that mutates `syncState`. Workers and ViewModels never write to it directly.

### §4.4 Idempotency keys

Per sync attempt, the phone:

1. Picks all `PENDING` rows (capped at a reasonable batch — say 200 — but in practice it's at most a few days).
2. Inserts a new row into `sync_attempt`:
   ```
   id: <auto-increment Int>
   idempotency_key: <UUID.randomUUID().toString()>
   payload_hash: <SHA-256 of the canonical JSON payload, lowercase hex>
   created_at: <UTC epoch ms>
   status: 'pending'
   row_ids: <comma-separated dateTimestamps included in this attempt>
   ```
3. Sets each included row's `syncState = SYNCING`.
4. POSTs the payload with `Idempotency-Key: <that uuid>`.
5. On success: marks rows `SYNCED`, `sync_attempt.status = 'success'`, stores `response_summary` (the `{synced, errors}` JSON, truncated to 1KB).
6. On retryable failure: reverts rows to `PENDING`, **leaves `sync_attempt.status = 'pending'`**, and on next attempt **reuses the same row** (same `idempotency_key`) if the payload is identical. If the user edited a row in between, the payload changes — generate a new attempt.
7. On 401: rows revert to `PENDING`, `sync_attempt.status = 'auth_failed'`, `SecretsStore.markSecretInvalid()`.

GC: `sync_attempt` rows older than 7 days with status in (`success`, `auth_failed`) are deleted by a maintenance routine in `SyncWorker` (run once per worker invocation).

**The canonical JSON payload for hashing** is the payload after Moshi serialization with `lenient = false` and sorted keys. Implement the canonicalization as a pure function `canonicalize(payload: List<DailyReadinessDto>): String`, test it.

### §4.5 UTC at storage, local at the edge

`DailyReadinessEntity.dateTimestamp` is the UTC epoch seconds corresponding to local-midnight of the day the entry represents, computed at entry time using the device's current TZ. If the user travels and edits a record, the original `dateTimestamp` stays put — it represents the day in the TZ where it was created. A separate `tz: String` column records that TZ.

Server-side, the same convention holds (server §4.6). The phone's `dateTimestamp` becomes the server's `date_timestamp` directly — no conversion at the wire.

At the UI layer (Compose), formatting always uses `tz` from the row (not the current device TZ) so the user sees "Monday 2026-05-13" regardless of where they happen to be when they open the app. Pseudo:

```kotlin
fun localDateLabel(entity: DailyReadinessEntity, locale: Locale): String {
    val zone = ZoneId.of(entity.tz)
    val instant = Instant.ofEpochSecond(entity.dateTimestamp)
    val date = instant.atZone(zone).toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale))
}
```

### §4.6 Bearer secret discipline

The secret lives in `EncryptedSharedPreferences` only. `SecretsStore` is the sole accessor:

```kotlin
class SecretsStore(context: Context) {
    fun saveSecret(secret: String)
    fun getSecret(): String?            // returns null if absent
    fun clearSecret()
    fun isSecretInvalid(): Boolean      // set true on 401
    fun markSecretInvalid()
    fun clearSecretInvalid()            // called after a successful sync

    fun saveServerUrl(url: String)
    fun getServerUrl(): String?
}
```

Implementation uses `EncryptedSharedPreferences.create(...)` with `MasterKey.Builder(...).setKeyScheme(AES256_GCM).build()`. The file name (`secrets.xml`) is opaque on disk and ignored by `.gitignore`.

**Logging:** `HeaderRedactor` (Phase 4) is an OkHttp `Interceptor` that explicitly redacts the `Authorization` header in any logging output, regardless of log level. The standard `HttpLoggingInterceptor` is configured with `level = HEADERS` in debug builds and `level = BASIC` in release. Independently of that, `HeaderRedactor` runs first and calls `HttpLoggingInterceptor.Logger`'s `redactHeader("Authorization")` (the API exists in OkHttp 5+; if the project is on OkHttp 4, write a custom filtering Logger and assert in a test that no log line contains "Bearer ").

A unit test (`AuthInterceptorTest.kt`) asserts that:
1. Every outbound request has an `Authorization` header iff a secret is set.
2. The mocked logger receives `Authorization: ██` (or any non-Bearer value) for every logged request.

### §4.7 Polish strings via resources only

CI / pre-commit check (Phase 3 ships the script): `scripts/verify-no-polish-literals.sh`. It greps Kotlin source for common Polish letters (`ą|ę|ć|ł|ń|ó|ś|ź|ż`) outside comments and outside `strings.xml`. False positives are rare (Polish-named identifiers are forbidden anyway by invariant §1.7). Output of the check is `CLEAN` or a list of offending file:line entries.

```bash
#!/usr/bin/env bash
set -euo pipefail
hits=$(grep -RnE '[ąęćłńóśźżĄĘĆŁŃÓŚŹŻ]' app/src/main/java/ 2>/dev/null \
       | grep -vE '//.*[ąęćłńóśźżĄĘĆŁŃÓŚŹŻ]' \
       | grep -vE '/\*.*[ąęćłńóśźżĄĘĆŁŃÓŚŹŻ].*\*/' || true)
if [ -z "$hits" ]; then
    echo "CLEAN"
else
    echo "Polish literals found in code (move to strings.xml):"
    echo "$hits"
    exit 1
fi
```

### §4.8 Logging hygiene (broader than §4.6)

Beyond the Authorization header rule: do not log raw response bodies in release builds (they may contain user data). Debug builds may log bodies for `/api/android/*`. PII consideration: the user is the sole owner of the phone, but a debug log left attached to a bug report should still not leak the Bearer token. Hence the explicit redactor.

`Timber` is the recommended logging facade. If the project already uses `android.util.Log`, leave it — adding Timber is a separate refactor.

---

## §5. Operational Discipline

- **Asking for confirmation:** for any destructive operation (per §1.14), post the exact command in chat, in a code block, with one sentence of justification. Wait for the literal reply `TAK`.
- **Posting checkpoint results:** after every phase checkpoint, paste the output of every checkpoint command in chat. Do not summarize. If a command output is long, paste it in fenced code blocks; the user will read it.
- **When you don't know:** stop and ask. Do not guess defaults. The Master Blueprint or this CLAUDE.md should answer most questions; if neither does, the user does.
- **When a Gradle command fails for environmental reasons** (e.g. SDK not installed on the host running Claude Code): say so. Tell the user what command to run and ask them to paste the output back. Do not fabricate test results.
- **Logging completed phases:** after every phase checkpoint passes, append a one-line entry to `CHANGELOG.md` at the repo root: `YYYY-MM-DD — Phase N complete — <one-line summary>`. Create `CHANGELOG.md` in Phase 1 if it does not exist.

---
---

## §13. What This Document Does Not Cover

- **UI redesign.** The existing screens (`QuickEntryScreen`, `ProDashboardScreen`, `DayDetailScreen`, `LegendBottomSheet`) keep their look and structure. The only UI touches are: a Settings screen (new) and a status line + sync button on `ProDashboardScreen`. Any broader UI work is a future phase.
- **Health Connect integration** (Pixel Watch / Garmin / Fitbit HRV + sleep auto-fill). Explicitly out of scope at the user's request. When/if added, plan: a `data_source` column on `DailyReadinessEntity` (values: `user` | `health_connect`), a `HealthConnectClient` interface in a new package, an opt-in toggle in Settings. Schema bump means another `Migration_*_*`.
- **Push notifications from the server** (e.g. Hermes new-conclusion alerts). Needs FCM + server-side cert plumbing. Add later if read-back-on-app-open isn't sufficient.
- **Multi-user.** Single-athlete by design. The server's CLAUDE.md §13 covers when multi-user becomes a need; on Android, the change is a `user_id` column on the entity and a Settings field for which user the device represents.
- **CSV / share export** of historical data. Trivial to add later: read all rows, format, share-intent. Not a blocker.
- **Algorithm v2.** When the math changes, bump `ALGORITHM_VERSION`, write new fixtures, write `Migration_X_Y`, coordinate the same change on the server. The infrastructure for this is in place (§4.1, §4.2, §1.2, §1.12).
- **Direct LAN access without Tailscale** (i.e. trusting the server's local CA on the phone). Possible but out of scope; document a `network_security_config.xml` recipe later if anyone wants it.

---

*End of CLAUDE.md. Begin with §6 — Phase 1. Stop at every checkpoint. The Master Blueprint stays the source of truth for the app's existing behavior; this document is the source of truth for how the app talks to the server and how the algorithm stays honest.*
