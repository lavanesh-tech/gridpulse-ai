#!/usr/bin/env bash
# Browser end-to-end tests: real React app -> real API -> real Postgres, all throwaway. Usage: ./scripts/e2e.sh
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/lib-throwaway.sh
trap stop_stack EXIT
start_stack 5434 18081
cd frontend
npm install --no-audit --no-fund >/dev/null
if [ -n "${CI:-}" ]; then npx playwright install --with-deps chromium >/dev/null; else npx playwright install chromium >/dev/null; fi
E2E_API="$BASE" E2E_ADMIN_PASSWORD="$ADMIN_PASSWORD" npx playwright test
echo "E2E PASSED"
