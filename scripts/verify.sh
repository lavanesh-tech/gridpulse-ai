#!/usr/bin/env bash
# Run on your Mac from the repo root: ./scripts/verify.sh
set -euo pipefail
echo "== Java =="; java -version 2>&1 | head -1
if [ ! -f gradlew ]; then
  echo "== Generating Gradle wrapper (needs 'gradle' installed: brew install gradle) =="
  gradle wrapper --gradle-version 8.14.3
fi
echo "== Docker =="
if ! docker info >/dev/null 2>&1; then echo "Docker is not running. Open Docker Desktop, wait for it to say Running, then re-run."; exit 1; fi
echo "Docker running"
echo "== Build + tests (Testcontainers starts Postgres automatically) =="
./gradlew clean build
echo "== Compose smoke test =="
trap "docker compose down >/dev/null 2>&1" EXIT
docker compose up -d --wait
docker compose exec -T postgres psql -U gridpulse -d gridpulse -c "select 1" >/dev/null && echo "Postgres OK"
docker compose exec -T redis redis-cli ping
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9094 --list >/dev/null && echo "Kafka OK"

echo "== Frontend (Node) =="
if ! command -v npm >/dev/null; then
  if command -v brew >/dev/null; then brew install node; else echo "Node.js is missing. Install it from https://nodejs.org (LTS), then re-run."; exit 1; fi
fi
node -v
(cd frontend && npm install --no-audit --no-fund && npm test && npm run build)

echo "ALL CHECKS PASSED"
