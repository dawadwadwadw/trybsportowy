# Changelog

Format: `YYYY-MM-DD — <scope> — <one-line summary>` (newest first).

2026-05-19 — Phase 1 complete — Algorithm parity established; 6 fixtures pass (CalculateReadinessUseCaseTest). Added docs/history/ for §1.11 schema backups. (Test run confirmed by user in Android Studio.)

2026-05-15 — Phase 1 (pending verification) — CalculateReadinessUseCase rewritten to conform to §4.1.1: added CNS overload penalty (×0.85 when total_cns+total_body > 6.0 strict) and D-Code (HALF_UP, 1 dp); cnsDrain/bodyDrain removed from the base score (they now only drive overload + D-Code); intermediate math switched to Double. Behavior change: `stressCode` retired from scoring — the column is retained but no longer affects the readiness score. Added AlgorithmVersion.kt (v1), algorithm_v1_fixtures.json (6 fixtures), and the JUnit4 parity harness. Awaiting `./gradlew test` confirmation before Phase 1 is marked complete.

2026-05-15 — Docs — CLAUDE.md installed from the Android Operating Manual and restructured into the lean split: invariants + server contract + cross-cutting rules + algorithm stay in CLAUDE.md; per-phase procedure moved to docs/phases/phase-1..7.md; Master Blueprint archived to docs/MASTER_BLUEPRINT.md (no prior CLAUDE.md existed in the repo).
