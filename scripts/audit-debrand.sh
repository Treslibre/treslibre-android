#!/usr/bin/env bash
# Modified 2026 by Treslibre Project (Treslibre).
# Phase 5 (§5.1): Automated CI Audit Gate for treslibre-android
set -euo pipefail

echo "=== [1/2] Auditing codebase for upstream Threema domains, legal entity addresses, and Swiss UID ==="
MAIN_PATTERNS='\bthreema\.(ch|com|id|de|work)\b|Churerstrasse|Pfäffikon|CHE-487\.082\.806'

if grep -RInE "$MAIN_PATTERNS" . \
    --exclude-dir=.git \
    --exclude-dir=build \
    --exclude-dir=dist \
    --exclude-dir=target \
    --exclude-dir=.gradle \
    --exclude-dir=.idea \
    --exclude-dir=.licenses \
    --exclude="LICENSE*" \
    --exclude="COPYING*" \
    --exclude="NOTICE*" \
    --exclude="THIRD_PARTY*" \
    --exclude="MANUAL_ASSETS.md" \
    --exclude="audit-debrand.sh" \
    --exclude="Cargo.lock"; then
    echo "ERROR: Prohibited upstream domain or legal imprint string found!"
    exit 1
fi
echo "OK: Zero prohibited upstream domains or legal entity strings found."

echo "=== [2/2] Auditing user-facing Android string resources (*strings.xml) for lingering Threema product strings ==="
# Check all *strings.xml files across all locales; exclude AGPL attribution strings (about_copyright, "fork of Threema", "Threema AG", "Threema GmbH") and XML comment lines
UI_MATCHES=$(grep -RInE '<string[^>]*>.*Threema' app/src/main/res/values* \
    --include="*strings.xml" \
    | grep -vE 'about_copyright|fork of Threema|endorsed by Threema|Threema AG|Threema GmbH' || true)

if [ -n "$UI_MATCHES" ]; then
    echo "ERROR: Lingering Threema product branding found in user-facing Android string resources:"
    echo "$UI_MATCHES"
    exit 1
fi
echo "OK: Zero lingering Threema product strings in user-facing Android string resources."
echo "=== Audit Passed (exit 0) ==="
