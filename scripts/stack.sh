#!/usr/bin/env bash
# Builds the Docker images and runs the whole stack: Postgres, Redis, Kafka, API, notification service, web console.
# Then smoke-tests it through the web container, exactly as a browser would.
#   ./scripts/stack.sh          build + start + smoke test (stack keeps running)
#   ./scripts/stack.sh down     stop everything (your data volume is kept)
set -euo pipefail
cd "$(dirname "$0")/.."
if [ "${1:-}" = "down" ]; then docker compose --profile app down; exit 0; fi
docker info >/dev/null 2>&1 || { echo "Docker is not running. Open Docker Desktop and re-run."; exit 1; }
[ -f .env ] || { echo "No .env - run ./scripts/setup-openai.sh or copy .env.example to .env first."; exit 1; }
set -a; . ./.env; set +a
for p in "${API_PORT:-8080}" "${NOTIFICATION_PORT:-8081}" "${WEB_PORT:-3000}"; do
  if lsof -ti tcp:"$p" >/dev/null 2>&1 && ! docker compose --profile app ps --format '{{.Ports}}' | grep -q ":$p->"; then
    echo "Port $p is used by another program. Stop it, or set API_PORT / NOTIFICATION_PORT / WEB_PORT in .env."; exit 1
  fi
done

echo "== Building images (first time: several minutes) =="
docker compose --profile app build
echo "== Starting the stack =="
docker compose --profile app up -d --wait postgres redis kafka
docker compose --profile app up -d api notification web

WEB="http://localhost:${WEB_PORT:-3000}"
echo "== Waiting for the API (through the web container) =="
for i in $(seq 1 90); do
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$WEB/api/v1/auth/token" -H 'Content-Type: application/json' \
    -d "{\"username\":\"admin\",\"password\":\"${GRIDPULSE_ADMIN_PASSWORD:-}\"}" || true)
  [ "$code" = "200" ] && break
  [ "$i" = 90 ] && { echo "API not ready (last HTTP $code). Logs:"; docker compose --profile app logs --tail 40 api; exit 1; }
  sleep 2
done
echo "== Smoke test =="
curl -sf "$WEB/" | grep -q '<div id="root">' && echo "web console OK"
curl -sf "http://localhost:${API_PORT:-8080}/actuator/health/readiness" >/dev/null && echo "API ready"
curl -sf "http://localhost:${NOTIFICATION_PORT:-8081}/actuator/health/readiness" >/dev/null && echo "notification service ready"
docker compose --profile app ps --format 'table {{.Service}}\t{{.Status}}'
echo "STACK UP: open $WEB and sign in as admin (password in .env). Stop with ./scripts/stack.sh down"
