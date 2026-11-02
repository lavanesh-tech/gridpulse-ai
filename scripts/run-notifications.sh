#!/usr/bin/env bash
# Starts the notification service on :8081 (needs run-local.sh's Postgres + Kafka). Same .env, same JWT secret.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
docker compose up -d --wait postgres kafka
exec ./gradlew -q :notification-service:bootRun
