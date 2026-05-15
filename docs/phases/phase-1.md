# Phase 1 — Algorithm Parity (procedure)

> Active-phase procedure. Canonical rules live in `/CLAUDE.md` (§1 invariants, §4 cross-cutting incl. the algorithm). §4.1.3 and §4.1.4 are reproduced at the END of this file (relocated from CLAUDE.md §4; content unchanged).

---

## §6. Phase 1 — Algorithm Parity

The single most important phase. Without it, every subsequent phase is built on guesswork.

### §6.1 Inventory

1. `git clone https://github.com/dawadwadwadw/trybsportowy` (already done by user). Inspect the current state:
   ```bash
   find app/src/main/java -type f -name "*.kt" | head -50
   ./gradlew tasks --all | head -50
   cat app/build.gradle.kts || cat app/build.gradle
   ```
2. Locate the existing readiness algorithm. Likely names: `CalculateReadinessUseCase`, `ReadinessCalculator`, `ReadinessAlgorithm`. Read it end to end.
3. Compare its logic to §4.1.1 above. Specifically check:
   - Day -1 sleep weight: is it `0.8` or `0.75`?
   - Overload threshold: is it `> 6.0` (strict) or `>= 6.0`?
   - Overload penalty: is it `* 0.85`?
   - Missing-day handling: are missing days treated as zero, or skipped?
   - Are `cnsDrain` and `bodyDrain` themselves decayed by the same weights as the base codes? (They should be, per §4.1.1.)
4. **Write a short audit in chat** before changing any code: a bulleted list of what the current code does, where it disagrees with §4.1.1, and the proposed fix.

### §6.2 Create the fixture file

1. Add `CHANGELOG.md` at the repo root if absent.
2. Create `docs/MASTER_BLUEPRINT.md` with the Master Blueprint content if absent.
3. Create `app/src/test/resources/algorithm_v1_fixtures.json` containing all six fixtures from §4.1.3. The JSON must be valid (`jq . algorithm_v1_fixtures.json` succeeds). Include them in the exact schema from §4.1.2.

### §6.3 Add `AlgorithmVersion.kt`

```kotlin
// app/src/main/java/<pkg>/readiness/AlgorithmVersion.kt
package <pkg>.readiness

const val ALGORITHM_VERSION: String = "v1"
```

### §6.4 Add the test harness

Create `app/src/test/java/<pkg>/readiness/FixtureLoader.kt` that loads and parses the JSON (Moshi or kotlinx.serialization, whichever the project already uses; if neither, use Moshi — it's coming in Phase 4 anyway, pull it in early via `testImplementation`).

Create `app/src/test/java/<pkg>/readiness/CalculateReadinessUseCaseTest.kt` with one `@Test` per fixture. Each test calls the use case with the fixture's input and asserts every expected field. Use `assertEquals(expected, actual, 1e-9)` for floats and `assertEquals(expected, actual)` for booleans.

If `CalculateReadinessUseCase` doesn't currently take a `List<DailyReadinessEntity>` directly, write a thin test-only adapter that constructs the appropriate input format from the fixture rows. Don't change the production signature in this phase.

### §6.5 Reconcile

Run `./gradlew test --tests "<pkg>.readiness.*"`. If all pass: the on-phone algorithm is already correct, no production change needed. Commit just the test infrastructure and `AlgorithmVersion.kt`.

If any fail: fix the on-phone algorithm to match §4.1.1. Take care:
- Day -1 sleep weight is `0.8`, not `0.75`. This is the single most likely existing bug.
- The overload threshold is **strict** `> 6.0` — `6.0` exactly does **not** trigger overload (Fixture 4).
- `cnsDrain` and `bodyDrain` are decayed by the same weights as the non-sleep base codes.

After fixes, all six fixtures must pass.

### §6.6 Checkpoint

```bash
# 1. Fixture file is valid JSON with six fixtures
jq '.fixtures | length' app/src/test/resources/algorithm_v1_fixtures.json
# expect: 6
jq -r '.algorithm_version' app/src/test/resources/algorithm_v1_fixtures.json
# expect: v1
jq -r '.rounding_mode' app/src/test/resources/algorithm_v1_fixtures.json
# expect: HALF_UP

# 2. All algorithm tests pass
./gradlew test --tests "*CalculateReadinessUseCaseTest*"
# expect: BUILD SUCCESSFUL, 6 tests passed

# 3. AlgorithmVersion constant exists
grep -RE 'ALGORITHM_VERSION\s*[:=]\s*"v1"' app/src/main/java/
# expect: one hit

# 4. Master Blueprint committed
test -f docs/MASTER_BLUEPRINT.md && echo OK
# expect: OK

# 5. Changelog seeded
test -f CHANGELOG.md && head -3 CHANGELOG.md
# expect: a line for Phase 1
```

Append to `CHANGELOG.md`: `YYYY-MM-DD — Phase 1 complete — Algorithm parity established; 6 fixtures pass.`
Wait for `next`.


---

# (Relocated from CLAUDE.md §4) Required fixtures & test invocation

#### §4.1.3 Required minimum fixtures (write Phase 1; expand later)

These are the must-haves. The doc gives the inputs and the expected outputs you must reproduce. If your implementation produces different numbers, your implementation is wrong.

**Fixture 1: `default_today_only`**
- Today: S2, H2, P0, W0, A0, N1, cns=0, body=0
- Day -1, -2, -3: missing
- Expected: `base_score=20.0`, `readiness_score=20.0`, `total_cns=0.0`, `total_body=0.0`, `overload_triggered=false`, `d_code=0.0`

**Fixture 2: `day_minus_one_sleep_weight_080_exception`** (catches the most likely bug)
- Today: S2, H2, P0, W0, A0, N1, cns=0, body=0
- Day -1: S4, H2, P0, W0, A0, N1, cns=0, body=0
- Day -2, -3: missing
- Hand calculation:
  - Day 0: S=20*1.0=20, others=0
  - Day -1: S=35*0.8=28, others=0*0.75=0
  - base = 48
  - total_cns=0, total_body=0, no overload
- Expected: `base_score=48.0`, `readiness_score=48.0`, `total_cns=0.0`, `total_body=0.0`, `overload_triggered=false`, `d_code=0.0`

**A test that uses 0.75 instead of 0.8 here gets `base_score=46.25` and fails. That is the point.**

**Fixture 3: `full_window_overload_triggered`**
- Today: S2, H2, P3, W2, A0, N1, cns=4, body=2
- Day -1: S2, H2, P2, W0, A0, N1, cns=2, body=1
- Day -2: S1, H1, P0, W0, A0, N1, cns=3, body=0
- Day -3: S0, H2, P0, W0, A0, N1, cns=0, body=0
- Hand calculation:
  - Day 0: S=20*1.0=20, others=(0+(-55)+(-15)+0+0)*1.0=-70 → -50
  - Day -1: S=20*0.8=16, others=(0+(-30)+0+0+0)*0.75=-22.5 → -6.5
  - Day -2: S=5*0.5=2.5, others=(-15+0+0+0+0)*0.5=-7.5 → -5
  - Day -3: S=0*0.2=0, others=0 → 0
  - base = -50 + (-6.5) + (-5) + 0 = -61.5
  - total_cns = 4*1.0 + 2*0.75 + 3*0.5 + 0*0.2 = 7.0
  - total_body = 2*1.0 + 1*0.75 + 0*0.5 + 0*0.2 = 2.75
  - sum = 9.75 > 6.0 → overload
  - final = -61.5 * 0.85 = -52.275
  - d_code = round_half_up(9.75, 1) = 9.8
- Expected: `base_score=-61.5`, `readiness_score=-52.275`, `total_cns=7.0`, `total_body=2.75`, `overload_triggered=true`, `d_code=9.8`

**Fixture 4: `full_window_just_under_overload`**
- Construct inputs such that `total_cns + total_body = 6.0` exactly (boundary is **strict** `> 6.0` per spec, so 6.0 should **not** trigger overload).
- Hand-tune: today cns=6, body=0, all other days empty → total_cns=6.0, total_body=0.0, sum=6.0, no overload.
- Today: S2 only, all else default; cns=6, body=0.
- Expected: `base_score=20.0`, `readiness_score=20.0`, `total_cns=6.0`, `total_body=0.0`, `overload_triggered=false`, `d_code=6.0`

**Fixture 5: `full_window_just_over_overload`**
- Same as Fixture 4 but cns=7 today.
- Expected: `total_cns=7.0`, `total_body=0.0`, `overload_triggered=true`, `readiness_score = 20.0 * 0.85 = 17.0`, `d_code=7.0`

**Fixture 6: `missing_intermediate_days`**
- Today: S3, all else default, cns=0, body=0
- Day -1: missing
- Day -2: missing
- Day -3: S4, all else default, cns=0, body=0
- Expected:
  - Day 0: S=25*1.0=25
  - Day -3: S=35*0.2=7
  - base = 32
  - No drains, no overload
- Expected: `base_score=32.0`, `readiness_score=32.0`, `d_code=0.0`, `overload_triggered=false`

Add more fixtures over time, especially after debugging any real-world divergence between phone and server — every divergence becomes a fixture, so it can never silently reappear.

#### §4.1.4 Test invocation

`app/src/test/java/<pkg>/readiness/CalculateReadinessUseCaseTest.kt` uses JUnit4 (matching whatever the rest of the app uses; if KSP/JUnit5 is already wired, use that). The test reads the fixtures via `FixtureLoader` from `src/test/resources/algorithm_v1_fixtures.json`, runs the use case for each, asserts each numeric field with `1e-9` tolerance for floats and exact equality for `overload_triggered`. A `@Test` method per fixture name (parameterized or one-per-fixture; one-per-fixture is fine and produces clearer failure messages).

`./gradlew test` must pass before any commit. CI (if/when added) treats failure as a release blocker.

