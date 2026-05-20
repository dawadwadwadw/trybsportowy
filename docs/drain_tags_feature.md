# Feature: Drain Tag Decay Display

## Context

This is the `trybsportowy` Android app — Jetpack Compose, MVVM, Room, Kotlin Coroutines.
The app tracks daily athlete readiness using a 4-day fatigue decay algorithm.

Before starting: read `CLAUDE.md` and `docs/MASTER_BLUEPRINT.md` fully.
Then inspect the existing codebase — specifically:
- The existing `CalculateReadinessUseCase` (or equivalent) to understand how `cnsDrain`
  and `bodyDrain` are currently computed and decayed.
- `DailyReadinessEntity` to confirm the `drainTags: String` field (JSON array of tag IDs).
- `ProDashboardScreen` to understand the existing layout, especially where `INJURY GUARD`
  currently renders — the new component goes directly above it.
- The existing tag dictionary (tag ID → CNS weight or Body weight) — it may live in
  a constants file, a sealed class, an enum, or inline in the use case. Find it before
  writing any new code.

Do a brief written audit in chat before touching any file:
- Where is the tag dictionary defined?
- What is the shape of `drainTags` in the DB (confirm it's a JSON array of string IDs)?
- What is the exact Composable name and file of the INJURY GUARD section in ProDashboardScreen?

---

## What to build

A collapsible `DrainBreakdownCard` Composable that sits directly above the INJURY GUARD
section in `ProDashboardScreen`. It is always visible (not hidden behind a nav). It shows
the decayed contribution of every drain tag from the last 4 days, broken down by tag.

**No algorithm changes. No schema changes. No server sync changes.**
This is display-only. Read existing Room data, compute display values in the ViewModel,
render them.

---

## The decay math for display (mirrors the existing algorithm exactly)

The algorithm already decays `cnsDrain` and `bodyDrain` day-totals using these weights:

| Day       | Weight |
|-----------|--------|
| Day 0     | 1.0    |
| Day -1    | 0.75   |
| Day -2    | 0.5    |
| Day -3    | 0.2    |

Note: the 0.8 special case applies only to the Sleep base code (S). For drain tags,
Day -1 weight is always 0.75. This is already correct in the existing algorithm.

For this display feature, apply the same weights per-tag instead of per-day-total.

**Per-tag decayed contribution:**
```
contribution(tag, day) = tag.weight * decay_weight(day)
```

Where `tag.weight` is the tag's CNS or Body point value from the Master Blueprint:

CNS tag weights:
- Silny Stres: +4
- Kac po siłowni: +3
- Deadline: +3
- Nauka: +2
- Ekran: +2
- Relaks: -2

Body tag weights:
- Ból ścięgien: +4
- Choroba: +5
- DOMS: +2
- Odwodnienie: +2
- Siedzenie: +2
- Aktywna regeneracja: -2

**Grouped total per tag (across all 4 days):**
```
total(tag) = Σ over d ∈ {0,-1,-2,-3}: contribution(tag, d)  [only days where tag appears]
```

**Running CNS total and Body total (what the collapsed card shows):**
```
displayed_cns   = Σ total(tag) for all CNS tags across 4 days
displayed_body  = Σ total(tag) for all Body tags across 4 days
```

These numbers must equal the values the existing algorithm already uses for the
overload check — verify this in a unit test (see §Tests below).

Round displayed values to one decimal place (HALF_UP, same as d_code).

---

## Data flow

```
Room (last 4 DailyReadinessEntity rows)
  │
  ▼
DrainBreakdownCalculator.kt   ← new, pure function, testable
  │  reads: drainTags JSON from each day, looks up weights, applies decay
  │  produces: DrainBreakdownState
  ▼
ProDashboardViewModel         ← extend existing ViewModel with a new StateFlow
  │  val drainBreakdown: StateFlow<DrainBreakdownState>
  ▼
DrainBreakdownCard.kt         ← new Composable
  │  placed directly above INJURY GUARD in ProDashboardScreen
  ▼
User sees collapsed: "CNS: 4.5   Body: 2.0"
User taps → expanded: per-tag breakdown
```

---

## `DrainBreakdownState` data class

```kotlin
data class TagContribution(
    val tagId: String,           // internal ID, e.g. "silny_stres"
    val labelRes: Int,           // string resource ID for Polish display name
    val category: DrainCategory, // CNS or BODY
    val totalContribution: Double // summed across 4 days with decay; can be negative
)

enum class DrainCategory { CNS, BODY }

data class DrainBreakdownState(
    val totalCns: Double,                        // sum of all CNS tag contributions
    val totalBody: Double,                        // sum of all Body tag contributions
    val tagContributions: List<TagContribution>,  // only tags with non-zero contribution
    val isExpanded: Boolean = false
)
```

`tagContributions` is sorted: CNS tags first, then Body tags, each group sorted by
`abs(totalContribution)` descending (highest impact at top). Tags with
`totalContribution == 0.0` are omitted.

---

## `DrainBreakdownCalculator.kt`

Location: `app/src/main/java/<pkg>/readiness/DrainBreakdownCalculator.kt`

Pure function — no Android dependencies, no coroutines, no Room. Takes a list of up
to 4 `DailyReadinessEntity` rows (ordered day 0 first, day -3 last; missing days
represented as `null`) and returns `DrainBreakdownState`.

```kotlin
object DrainBreakdownCalculator {
    fun calculate(
        days: List<DailyReadinessEntity?>  // index 0 = today, index 3 = 3 days ago
    ): DrainBreakdownState
}
```

The tag dictionary (tag ID → weight + category) lives here as a private constant map.
If the existing codebase already has this dictionary somewhere, import it rather than
duplicating — no two sources of truth for tag weights.

---

## `DrainBreakdownCard.kt`

Location: `app/src/main/java/<pkg>/ui/pro/DrainBreakdownCard.kt`

Collapsed state (always visible, even when no tags contributed):
```
┌────────────────────────────────────────┐
│  Drain   CNS: 4.5        Body: 2.0   ▼ │
└────────────────────────────────────────┘
```

If `totalCns == 0.0 && totalBody == 0.0`:
```
┌────────────────────────────────────────┐
│  Drain   CNS: 0.0        Body: 0.0   ▼ │
└────────────────────────────────────────┘
```

Expanded state (AnimatedVisibility, standard Compose expand animation):
```
┌────────────────────────────────────────┐
│  Drain   CNS: 4.5        Body: 2.0   ▲ │
├────────────────────────────────────────┤
│  🧠 CNS                                │
│  Deadline            +2.3             │
│  Silny Stres         +1.5             │
│  Relaks              −0.5             │
├────────────────────────────────────────┤
│  💪 Body                               │
│  DOMS                +1.5             │
│  Choroba             +0.0  ← omit     │
└────────────────────────────────────────┘
```

Wait — no emojis. The Master Blueprint uses them but `CLAUDE.md` §10.1 (server dashboard rule)
and the general minimalism of this project means: use text labels instead.
- `"CNS"` and `"Body"` as section headers (small caps or slightly smaller text).
- Tag names in Polish from `strings.xml`.
- Contribution formatted as `"+2.3"` or `"−1.5"` (use minus sign U+2212, not hyphen).
- Values rounded to one decimal.

The card uses the existing app theme — `MaterialTheme.colorScheme` only, no hardcoded
colors. Match the card style of the INJURY GUARD section directly below it for visual
consistency (same elevation, same corner radius, same padding). Inspect INJURY GUARD
before styling this card.

Tap target: the entire collapsed row is tappable (not just the chevron). The chevron
rotates 180° on expand with `animateFloatAsState`.

`isExpanded` state lives inside the Composable via `rememberSaveable` — it survives
screen rotation. Do not push expanded/collapsed state up to the ViewModel.

---

## ViewModel changes

In the existing `ProDashboardViewModel` (or equivalent), add:

```kotlin
val drainBreakdown: StateFlow<DrainBreakdownState> =
    readinessDao.observeLastNDays(4)          // new DAO query — see below
        .map { rows -> DrainBreakdownCalculator.calculate(rows.padToFour()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrainBreakdownState(0.0, 0.0, emptyList()))
```

`padToFour()` is an extension on `List<DailyReadinessEntity>` that returns a list of
exactly 4 nullable elements: index 0 = most recent day in DB (today or last entered),
going backwards. If the DB has fewer than 4 days, pad with `null`. If it has more than 4,
take only the 4 most recent.

---

## DAO addition

Add to `ReadinessDao`:

```kotlin
@Query("""
    SELECT * FROM DailyReadinessEntity
    ORDER BY dateTimestamp DESC
    LIMIT :n
""")
fun observeLastNDays(n: Int): Flow<List<DailyReadinessEntity>>
```

This is a read query — it does not violate CLAUDE.md §1.5 (writes only through
`ReadinessRepository`).

---

## ProDashboardScreen change

Find the INJURY GUARD section. Directly above it, add:

```kotlin
DrainBreakdownCard(
    state = viewModel.drainBreakdown.collectAsStateWithLifecycle().value
)
```

Surgical insertion only. Do not refactor the rest of the screen.

---

## Strings to add to `res/values/strings.xml`

```xml
<!-- DrainBreakdownCard -->
<string name="drain_card_title">Drain</string>
<string name="drain_cns_label">CNS</string>
<string name="drain_body_label">Body</string>
<string name="drain_cns_section_header">CNS</string>
<string name="drain_body_section_header">Body</string>

<!-- Tag display names (add only if not already present) -->
<string name="tag_silny_stres">Silny Stres</string>
<string name="tag_kac_po_silowni">Kac po siłowni</string>
<string name="tag_deadline">Deadline</string>
<string name="tag_nauka">Nauka</string>
<string name="tag_ekran">Ekran</string>
<string name="tag_relaks">Relaks</string>
<string name="tag_bol_sciegien">Ból ścięgien</string>
<string name="tag_choroba">Choroba</string>
<string name="tag_doms">DOMS</string>
<string name="tag_odwodnienie">Odwodnienie</string>
<string name="tag_siedzenie">Siedzenie</string>
<string name="tag_aktywna_regeneracja">Aktywna regeneracja</string>
```

Check if any of these already exist in `strings.xml` before adding — no duplicates.

---

## Tests

### Unit test: `DrainBreakdownCalculatorTest.kt`

Location: `app/src/test/java/<pkg>/readiness/DrainBreakdownCalculatorTest.kt`

Required cases:

**1. `empty_days_returns_zero`**
- All 4 days null.
- Expected: `totalCns=0.0`, `totalBody=0.0`, `tagContributions=[]`.

**2. `single_tag_today_full_weight`**
- Today has `drainTags = ["doms"]`, bodyDrain=2 (or whatever the existing field is).
- Expected: one `TagContribution(tagId="doms", category=BODY, totalContribution=2.0)`.
- `totalBody=2.0`.

**3. `single_tag_yesterday_decayed`**
- Day -1 has `drainTags = ["deadline"]`, cnsDrain=3. Today has no tags.
- Expected: `TagContribution(tagId="deadline", category=CNS, totalContribution=3*0.75=2.25)`.
- `totalCns=2.25`.

**4. `same_tag_multiple_days_accumulates`**
- Today: `["doms"]`, Day -1: `["doms"]`, Day -2: `["doms"]`.
- DOMS weight = 2.
- Expected contribution: 2*1.0 + 2*0.75 + 2*0.5 = 2.0 + 1.5 + 1.0 = 4.5.
- `totalBody=4.5`, one `TagContribution` for DOMS with `totalContribution=4.5`.

**5. `negative_tag_reduces_total`**
- Today: `["relaks"]` (CNS weight -2).
- Expected: `totalCns=-2.0`, one TagContribution with `totalContribution=-2.0`.

**6. `mixed_cns_and_body_tags`**
- Today: `["silny_stres", "doms"]`. Silny Stres CNS=4, DOMS Body=2.
- Expected: `totalCns=4.0`, `totalBody=2.0`, two contributions.

**7. `totals_match_algorithm_drain_values`**
This is the parity test. For a given set of 4 days, assert that:
```
DrainBreakdownCalculator.calculate(days).totalCns
    == CalculateReadinessUseCase.computeTotalCns(days)  // existing function
```
and same for `totalBody`. If `CalculateReadinessUseCase` doesn't expose these as
separate functions, refactor it to expose them (pure extraction, no behavior change,
covered by existing algorithm tests from CLAUDE.md Phase 1).

**8. `sort_order_highest_abs_first`**
- Multiple tags with different contributions. Assert that
  `tagContributions[0].totalContribution` has the highest absolute value.

**9. `zero_contribution_tag_omitted`**
- A tag appears on day -3 only (weight × 0.2 = some small but non-zero value — so
  actually this can't be zero unless weight is 0, which none are). Adjust: test that a
  tag present in the DB but which only appears on days where its contribution rounds to
  exactly 0.0 is omitted. (Edge case: if you strip all days except day -3 with a weight-2
  tag: 2 * 0.2 = 0.4, not zero. So the omission rule really only fires if the tag ID in
  the JSON is unrecognized. Test: unknown tag ID → omitted, no crash.)

Run with: `./gradlew test --tests "*DrainBreakdownCalculatorTest*"`
All 9 tests must be green before the PR is considered done.

---

## Checklist before declaring done

```bash
# 1. Compile
./gradlew assembleDebug
# expect: BUILD SUCCESSFUL

# 2. All tests pass (algorithm parity tests from Phase 1 + new drain tests)
./gradlew test
# expect: BUILD SUCCESSFUL, no failures

# 3. No Polish literals in code
bash scripts/verify-no-polish-literals.sh
# expect: CLEAN

# 4. No new colors hardcoded
grep -RE 'Color(0x|0xff|\.fromHex|Color\.rgb)' app/src/main/java/<pkg>/ui/pro/DrainBreakdownCard.kt || echo CLEAN
# expect: CLEAN

# 5. No schema change happened (this feature touches zero DB columns)
git diff app/schemas/
# expect: no changes

# 6. Manual UI check on device
./gradlew installDebug
# On device:
# a. Open ProDashboardScreen — DrainBreakdownCard visible above INJURY GUARD.
# b. With no tags in last 4 days: shows "CNS: 0.0  Body: 0.0", tap expands to empty sections.
# c. Enter a record with DOMS + Deadline tags. Return to dashboard.
#    Collapsed row shows non-zero numbers.
#    Tap → expands, shows DOMS under Body section, Deadline under CNS section,
#    each with a positive contribution value.
# d. Rotate screen — expanded state survives rotation.
# e. Enter another record for yesterday with same tags. Numbers in collapsed view increase.
#    Expanded view shows contribution < today's (decay applied).
```

---

## What NOT to do

- Do not change `CalculateReadinessUseCase`'s behavior or its output — only potentially
  extract sub-functions for testability (test 7 above).
- Do not add new Room columns. `drainTags` already stores the tag IDs as a JSON string.
- Do not change the sync payload. This feature is display-only.
- Do not add a new screen or route. The card lives on `ProDashboardScreen` only.
- Do not move `isExpanded` to the ViewModel — it is UI-only state.
- Do not use `LazyColumn` inside the expanded section; the tag list is at most 12 items,
  a regular `Column` is correct.
- Do not introduce a new dependency for this feature. Everything needed is already
  in the project (Compose, Room, existing Moshi for JSON parsing of `drainTags`).
