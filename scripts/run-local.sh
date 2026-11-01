#!/usr/bin/env bash
# Starts Postgres + Redis and the API on :8080 using the settings in .env.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
docker compose up -d --wait
exec ./gradlew -q bootRun
