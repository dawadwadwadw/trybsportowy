# Phase 6 — UI Integration (Minimal) (procedure)

> Active-phase procedure. Canonical rules live in `/CLAUDE.md`. Do not start this phase until Phase 5 has passed its checkpoint and the user replied `next`.

---

## §11. Phase 6 — UI Integration (Minimal)

Surgical additions to `ProDashboardScreen` only.

### §11.1 `SyncStatusLine`

A thin Composable that observes `SyncScheduler.observeStatus()` + the most recent `sync_attempt` row + `SecretsStore.isSecretInvalid()`.

States and Polish text (in `strings.xml`):

| Condition | Display |
|---|---|
| Secret invalid | `"Token nieprawidłowy. Otwórz ustawienia."` (clickable, navigates to Settings) |
| Worker is running | `"Synchronizuję..."` |
| Last attempt success, < 5 min ago | `"Zsynchronizowano przed chwilą."` |
| Last attempt success, ≥ 5 min ago | `"Ostatnia synchronizacja: %1$s"` formatted as `HH:mm` |
| Last attempt failed, network | `"Synchronizacja nieudana — ponowię gdy będzie sieć."` |
| Last attempt failed, server | `"Synchronizacja nieudana: %1$s"` |
| No attempts yet | `"Brak synchronizacji."` |

Render style: single row of 14sp `--text-secondary`-equivalent text under the existing top status of `ProDashboardScreen`. Do not stack icons or emojis — text only (consistent with the server-side dashboard rule from the server CLAUDE.md §10.1, though that rule binds the web dashboard, not this Android app; we adopt the same minimalist text-status convention anyway).

### §11.2 "Synchronizuj teraz" button

A `TextButton` in the `ProDashboardScreen`'s top app bar action area (or the screen-level action row, depending on the existing layout). On click: `SyncScheduler.triggerOnce(context)`. Disable for 3 seconds after press to prevent spam.

String: `<string name="sync_now">Synchronizuj teraz</string>`.

### §11.3 Show server's computed score when available

`ProDashboardScreen`'s per-day row already shows the locally computed readiness score (per the Master Blueprint). For each row, look up `readiness_computed_cache.dateTimestamp = row.dateTimestamp`:

- If a cache row exists with `algorithmVersion = "v1"`: display the server's `readinessScore` (rounded to integer or one decimal — match whatever the existing UI does).
- If no cache row: display the locally computed score as before, with a small text marker `"·"` or `"~"` indicating "not yet synced". Use a string resource: `<string name="score_local_marker">~</string>`.

This keeps the UI honest: by default it shows the server's truth; before sync, it shows local compute.

If the two values ever disagree by more than the float tolerance, that is a §4.1 violation — file it as a bug, do not paper over it in UI.

### §11.4 Checkpoint

```bash
# 1. App compiles, installs
./gradlew installDebug

# 2. Manual UI verification on device:
# a. Settings empty → ProDashboard status line shows "Brak synchronizacji."
# b. Save valid secret + URL, return to ProDashboard.
# c. Create today's record. Status line should switch to "Synchronizuję..." then
#    "Zsynchronizowano przed chwilą." within ~5s.
# d. The dashboard row for today shows a numeric score (server's value).
# e. Tap "Synchronizuj teraz" — status flips to "Synchronizuję..." then settled.
# f. Open Settings, save a wrong secret. Return. Status line shows
#    "Token nieprawidłowy. Otwórz ustawienia."

# 3. No emojis or stray UI in source
grep -RE '[\\x{1F300}-\\x{1FAFF}]' app/src/main/java/<pkg>/ui/ || echo CLEAN
# expect: CLEAN

# 4. Polish strings all in resources
bash scripts/verify-no-polish-literals.sh
# expect: CLEAN
```

Append to `CHANGELOG.md`. Wait for `next`.

---
