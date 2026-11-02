#!/usr/bin/env bash
# Starts Postgres, Redis and Kafka, then the API on :8080 using .env. Session events flow to Kafka.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
export GRIDPULSE_EVENTS_KAFKA_ENABLED="${GRIDPULSE_EVENTS_KAFKA_ENABLED:-true}"
docker compose up -d --wait
exec ./gradlew -q bootRun
