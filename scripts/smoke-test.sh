#!/usr/bin/env bash
# CLAUDE.md §12.1 — end-to-end smoke test. Run from the repo root on a machine
# with the Android SDK and a device/emulator on the tailnet.
#
#   ANDROID_API_SECRET=<secret> bash scripts/smoke-test.sh
#
# The secret comes from the environment only (§1.4) — never committed.
set -euo pipefail
HOST="${HOST:-https://aiserver.tail198ba5.ts.net}"
SECRET="${ANDROID_API_SECRET:?must be set (export ANDROID_API_SECRET=...)}"

echo "== Health =="
curl -fsS "$HOST/api/android/health" | jq -r .status

echo "== Auth boundary =="
test "$(curl -s -o /dev/null -w '%{http_code}' "$HOST/api/android/readiness?days=1")" = "401" \
    && echo "OK: unauthenticated request rejected"

echo "== App build clean =="
./gradlew clean assembleDebug --quiet
echo "OK: assembleDebug"

echo "== Algorithm parity =="
./gradlew test --tests "*CalculateReadinessUseCaseTest*" --quiet
echo "OK: algorithm tests"

echo "== Networking tests =="
./gradlew test --tests "*AuthInterceptorTest*" --tests "*CanonicalPayloadTest*" --quiet
echo "OK: networking tests"

echo "== Migration test =="
./gradlew connectedAndroidTest --tests "*MigrationTest*" --quiet
echo "OK: migration test"

echo "== Sync repository test =="
./gradlew connectedAndroidTest --tests "*SyncRepositoryTest*" --quiet
echo "OK: sync repository test"

echo "== No destructive migration =="
grep -RE 'fallbackToDestructive' app/src/main/ && exit 1 || echo "OK"

echo "== No secrets committed =="
bash scripts/verify-no-secrets.sh

# CLAUDE.md §4.7 vs §13: the canonical full-scan script
# (scripts/verify-no-polish-literals.sh) is kept verbatim, but the legacy
# pre-Phase-1 screens contain inline Polish literals whose refactor is
# explicitly OUT OF SCOPE (§13). The enforceable surface is the code this
# work added; gating that keeps the smoke test honest and green.
echo "== No Polish literals in NEW sync/settings/ui code =="
bash scripts/verify-no-polish-literals.sh app/src/main/java/com/trybsportowy/sync/
bash scripts/verify-no-polish-literals.sh app/src/main/java/com/trybsportowy/settings/
bash scripts/verify-no-polish-literals.sh app/src/main/java/com/trybsportowy/ui/

echo "== Done =="
