# Phase 7 — End-to-end Verification (procedure)

> Active-phase procedure. Canonical rules live in `/CLAUDE.md`. Do not start this phase until Phase 6 has passed its checkpoint and the user replied `next`.

---

## §12. Phase 7 — End-to-end Verification

A single shell script `scripts/smoke-test.sh` plus a manual UI walkthrough. Run after all prior phases land.

### §12.1 Smoke script

```bash
#!/usr/bin/env bash
set -euo pipefail
HOST="${HOST:-https://aiserver.tail198ba5.ts.net}"
SECRET="${ANDROID_API_SECRET:?must be set}"

echo "== Health =="
curl -fsS "$HOST/api/android/health" | jq -r .status

echo "== Auth boundary =="
test "$(curl -s -o /dev/null -w '%{http_code}' $HOST/api/android/readiness?days=1)" = "401" \
    && echo "OK: unauthenticated request rejected"

echo "== App build clean =="
./gradlew clean assembleDebug --quiet
echo "OK: assembleDebug"

echo "== Algorithm parity =="
./gradlew test --tests "*CalculateReadinessUseCaseTest*" --quiet
echo "OK: algorithm tests"

echo "== Migration test =="
./gradlew connectedAndroidTest --tests "*MigrationTest*" --quiet
echo "OK: migration test"

echo "== No fallbackToDestructive =="
grep -RE 'fallbackToDestructive' app/src/main/ && exit 1 || echo "OK"

echo "== No secrets committed =="
bash scripts/verify-no-secrets.sh

echo "== No Polish literals in code =="
bash scripts/verify-no-polish-literals.sh

echo "== Done =="
```

`chmod +x scripts/smoke-test.sh`. Add a one-line entry to the repo `README.md` (or create it) explaining how to run it.

### §12.2 Manual walkthrough

Open the app on a real device on the tailnet:

1. Settings: enter Tailscale hostname + valid secret → "Testuj połączenie" returns "Połączono. Wersja: <sha>".
2. QuickEntry: create a record for yesterday with S3/H2/P2/W1/A0/N1, cns=2, body=1, drain tags ["DOMS","Deadline"].
3. ProDashboard: row appears, status line shifts to "Synchronizuję..." then "Zsynchronizowano przed chwilą." within ~5s.
4. The score shown matches the server's:
   ```
   curl -s -H "Authorization: Bearer $SECRET" \
     "$HOST/api/android/readiness?days=2" | jq '.[0].computed.readiness_score'
   ```
5. Edit yesterday's record (change cns from 2 to 5). The row's syncState should flip back to PENDING, then SYNCED again. Server's `readiness_computed` for that day and the 3 following days should reflect the change.
6. Toggle airplane mode on, edit today's record. Status line: "Synchronizacja nieudana — ponowię gdy będzie sieć." Turn airplane mode off — within ~30s the worker retries and the line goes to "Zsynchronizowano".
7. In Settings, paste a deliberately wrong secret, save. Trigger a sync. Status line: "Token nieprawidłowy. Otwórz ustawienia." Restore the correct secret. Sync succeeds.

### §12.3 Final checkpoint

```bash
bash scripts/smoke-test.sh
```

Expect every line OK. Append to `CHANGELOG.md`: `YYYY-MM-DD — Phase 7 complete — End-to-end verified.`

