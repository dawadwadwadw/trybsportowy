#!/usr/bin/env bash
# CLAUDE.md §4.7 — Polish letters in Kotlin source outside comments and
# outside strings.xml indicate a hard-coded user-visible literal (§1.7).
#
# NOTE: the legacy pre-Phase-1 screens (MainActivity legend, the old
# presentation/settings/SettingsScreen, QuickEntry, etc.) already contain
# inline Polish literals. Refactoring them is explicitly OUT OF SCOPE per
# CLAUDE.md §13 ("existing screens keep their look and structure ... broader
# UI work is a future phase"). This script is canonical per §4.7 and scans
# everything; an optional path argument lets CI enforce only the new
# sync/settings code added in Phases 3-6.
set -euo pipefail

SCAN_PATH="${1:-app/src/main/java/}"

hits=$(grep -RnE '[ąęćłńóśźżĄĘĆŁŃÓŚŹŻ]' "$SCAN_PATH" 2>/dev/null \
       | grep -vE '//.*[ąęćłńóśźżĄĘĆŁŃÓŚŹŻ]' \
       | grep -vE '/\*.*[ąęćłńóśźżĄĘĆŁŃÓŚŹŻ].*\*/' || true)
if [ -z "$hits" ]; then
    echo "CLEAN"
else
    echo "Polish literals found in code (move to strings.xml): $SCAN_PATH"
    echo "$hits"
    exit 1
fi
