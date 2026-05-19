#!/usr/bin/env bash
# CLAUDE.md §8.4 / §1.4 — fails if a Bearer secret or a destructive Room
# migration was committed. Run from the repo root.
set -euo pipefail

echo "== Checking no Bearer tokens in committed files =="
hits=$(git grep -nE '(Bearer[[:space:]]+[A-Za-z0-9+/=._-]{20,}|ANDROID_API_SECRET[[:space:]]*=[[:space:]]*["A-Za-z0-9])' \
       -- ':!docs/' ':!*.md' ':!*.bak' ':!scripts/verify-no-secrets.sh' || true)
if [ -n "$hits" ]; then
    echo "POTENTIAL SECRETS COMMITTED:"
    echo "$hits"
    exit 1
fi
echo "CLEAN"

echo "== Checking no destructive Room migration =="
hits=$(grep -RE 'fallbackToDestructive' app/src/main/ || true)
if [ -n "$hits" ]; then
    echo "DESTRUCTIVE MIGRATION FOUND:"
    echo "$hits"
    exit 1
fi
echo "CLEAN"
